"""
Akrapovic-style exhaust valve hero — Blender render pipeline.

Builds a wide oval titanium/carbon exhaust tip with a butterfly disc, lights it
for a premium studio look, and renders a CLOSED -> OPEN frame sequence with a
transparent background. The frames are then packed into a single sprite sheet by
`pack_spritesheet.py` and played back in-app by the Compose `ValveVisual`.

USAGE (headless, recommended):
    blender --background --python design/blender/valve_render.py

USAGE (interactive, to tweak look first):
    Open Blender, switch to the Scripting workspace, open this file, press Run.
    Then re-run headless to render the full sequence.

Everything is parameterised in CONFIG below. The geometry/materials are a solid
starting point — tweak roughness, lighting and the heat-tint ramp in Blender
until it matches the look you want, then render.

Tested against Blender 3.6 LTS and 4.x (Principled BSDF input names differ
between versions, so we set advanced inputs defensively).
"""

import math
import os

import bpy
from mathutils import Vector

# --------------------------------------------------------------------------- #
# CONFIG
# --------------------------------------------------------------------------- #
CONFIG = {
    # Output
    "out_dir": os.path.join(os.path.dirname(os.path.abspath(__file__)), "out"),
    "frame_count": 48,          # closed (frame 1) -> fully open (frame N)
    "res_x": 540,               # per-frame source resolution (packed/downscaled in-app)
    "res_y": 360,
    "samples": 160,             # Cycles samples; raise for less noise, lower to iterate fast
    "use_gpu": True,

    # Geometry (Blender units)
    "oval_scale_x": 1.5,        # >1 makes the round tip a wide oval
    "pipe_radius": 1.0,         # inner bore radius
    "pipe_length": 2.4,         # how far the bore recedes (depth read)
    "lip_width": 0.10,          # titanium front lip thickness
    "carbon_width": 0.10,       # carbon outer sleeve thickness
    "carbon_depth": 0.35,       # how far the carbon sleeve sticks forward
    "disc_fill": 0.80,          # butterfly disc fills 80% of the bore when closed
    "disc_thickness": 0.06,
    "open_angle_deg": 88.0,     # disc rotation at fully-open

    # Optional studio HDRI for proper reflections (best results). Leave empty to
    # use the built-in 3-point area-light rig.
    "hdri_path": "",            # e.g. "/path/to/studio_small_08_2k.hdr"
    "hdri_strength": 1.0,
}


# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
def reset_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete()
    for block in (bpy.data.meshes, bpy.data.materials, bpy.data.lights, bpy.data.images):
        for item in list(block):
            if item.users == 0:
                block.remove(item)


def set_bsdf_input(bsdf, candidate_names, value):
    """Set the first matching Principled BSDF input (names vary across versions)."""
    for name in candidate_names:
        if name in bsdf.inputs:
            try:
                bsdf.inputs[name].default_value = value
                return True
            except Exception:
                pass
    return False


def make_principled(name, base_color, metallic, roughness, coat=0.0, aniso=0.0):
    mat = bpy.data.materials.new(name)
    # Materials are node-based by default in Blender 4+/5+; use_nodes is deprecated.
    if mat.node_tree is None:
        mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = base_color
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    set_bsdf_input(bsdf, ["Coat Weight", "Clearcoat"], coat)
    set_bsdf_input(bsdf, ["Anisotropic"], aniso)
    return mat


def add_cylinder(name, radius, depth, location, rotation=(0, 0, 0), caps=True):
    bpy.ops.mesh.primitive_cylinder_add(
        radius=radius,
        depth=depth,
        location=location,
        rotation=rotation,
        end_fill_type="NGON" if caps else "NOTHING",
        vertices=128,
    )
    obj = bpy.context.active_object
    obj.name = name
    bpy.ops.object.shade_smooth()
    return obj


def add_torus_ring(name, inner_radius, width, depth, location, rotation=(0, 0, 0)):
    """Annulus built from a torus (no boolean — reliable in headless mode)."""
    major = inner_radius + width / 2.0
    minor = width / 2.0
    bpy.ops.mesh.primitive_torus_add(
        major_radius=major,
        minor_radius=minor,
        abso_major_rad=1.0,
        abso_minor_rad=1.0,
        location=location,
        rotation=rotation,
        major_segments=128,
        minor_segments=48,
    )
    obj = bpy.context.active_object
    obj.name = name
    bpy.ops.object.shade_smooth()
    # Flatten slightly so the ring reads as a machined lip, not a doughnut tube.
    obj.scale = (1.0, depth / (minor * 2.0), 1.0)
    bpy.ops.object.transform_apply(scale=True)
    return obj


# --------------------------------------------------------------------------- #
# Build the tip
# --------------------------------------------------------------------------- #
def build_tip():
    c = CONFIG
    parent = bpy.data.objects.new("ValveTip", None)
    bpy.context.collection.objects.link(parent)

    # Materials -------------------------------------------------------------- #
    titanium = make_principled(
        "Titanium", base_color=(0.62, 0.64, 0.67, 1.0),
        metallic=1.0, roughness=0.28, aniso=0.5,
    )
    carbon = make_principled(
        "Carbon", base_color=(0.02, 0.02, 0.025, 1.0),
        metallic=0.2, roughness=0.32, coat=1.0,
    )
    bore_mat = make_principled(
        "BoreInterior", base_color=(0.015, 0.015, 0.018, 1.0),
        metallic=0.8, roughness=0.5,
    )
    # Heat-tinted titanium for the front lip (subtle iridescent blue->gold).
    lip_mat = make_principled(
        "TitaniumHeat", base_color=(0.36, 0.42, 0.62, 1.0),
        metallic=1.0, roughness=0.22, aniso=0.6,
    )

    objs = []

    # Bore tube (recedes back along -Y) ------------------------------------- #
    bore = add_cylinder(
        "Bore", radius=c["pipe_radius"], depth=c["pipe_length"],
        location=(0, -c["pipe_length"] / 2.0, 0), rotation=(math.radians(90), 0, 0),
        caps=False,
    )
    bore.data.materials.append(bore_mat)
    objs.append(bore)

    # Bore back cap (pure black so depth reads) ----------------------------- #
    back = add_cylinder(
        "BoreBack", radius=c["pipe_radius"], depth=0.02,
        location=(0, -c["pipe_length"], 0), rotation=(math.radians(90), 0, 0),
    )
    black = make_principled("Black", (0, 0, 0, 1), 0.0, 1.0)
    back.data.materials.append(black)
    objs.append(back)

    # Titanium front lip (annulus at y=0) ----------------------------------- #
    lip = add_torus_ring(
        "TitaniumLip",
        inner_radius=c["pipe_radius"],
        width=c["lip_width"],
        depth=0.10,
        location=(0, 0.02, 0),
        rotation=(math.radians(90), 0, 0),
    )
    lip.data.materials.append(lip_mat)
    objs.append(lip)

    # Carbon outer sleeve (annulus, pushed forward) ------------------------- #
    carbon_ring = add_torus_ring(
        "CarbonSleeve",
        inner_radius=c["pipe_radius"] + c["lip_width"],
        width=c["carbon_width"],
        depth=c["carbon_depth"],
        location=(0, c["carbon_depth"] / 2.0 - 0.05, 0),
        rotation=(math.radians(90), 0, 0),
    )
    carbon_ring.data.materials.append(carbon)
    objs.append(carbon_ring)

    # Butterfly disc (rotates about world X) -------------------------------- #
    disc_radius = c["pipe_radius"] * c["disc_fill"]
    disc = add_cylinder(
        "ButterflyDisc", radius=disc_radius, depth=c["disc_thickness"],
        location=(0, -0.18, 0), rotation=(math.radians(90), 0, 0),
    )
    disc.data.materials.append(titanium)
    objs.append(disc)

    # Slim hub + shaft ------------------------------------------------------- #
    hub = add_cylinder(
        "Hub", radius=disc_radius * 0.16, depth=c["disc_thickness"] * 1.6,
        location=(0, -0.18, 0), rotation=(math.radians(90), 0, 0),
    )
    hub.data.materials.append(lip_mat)
    objs.append(hub)

    shaft = add_cylinder(
        "Shaft", radius=0.035, depth=c["pipe_radius"] * 2.05,
        location=(0, -0.18, 0), rotation=(0, 0, math.radians(90)),
    )
    shaft.data.materials.append(titanium)
    objs.append(shaft)

    # Parent everything; the disc + hub get their own pivot for rotation ----- #
    pivot = bpy.data.objects.new("DiscPivot", None)
    bpy.context.collection.objects.link(pivot)
    pivot.location = (0, -0.18, 0)
    pivot.parent = parent
    for o in (disc, hub):
        o.parent = pivot
        o.matrix_parent_inverse = pivot.matrix_world.inverted()

    for o in objs:
        if o.parent is None:
            o.parent = parent

    # Make it a wide oval ---------------------------------------------------- #
    parent.scale = (CONFIG["oval_scale_x"], 1.0, 1.0)

    return parent, pivot


# --------------------------------------------------------------------------- #
# Lighting + camera + world
# --------------------------------------------------------------------------- #
def setup_world():
    world = bpy.data.worlds.new("ValveWorld")
    bpy.context.scene.world = world
    if world.node_tree is None:
        world.use_nodes = True
    nt = world.node_tree
    bg = nt.nodes.get("Background")

    if CONFIG["hdri_path"] and os.path.exists(CONFIG["hdri_path"]):
        env = nt.nodes.new("ShaderNodeTexEnvironment")
        env.image = bpy.data.images.load(CONFIG["hdri_path"])
        nt.links.new(env.outputs["Color"], bg.inputs["Color"])
        bg.inputs["Strength"].default_value = CONFIG["hdri_strength"]
    else:
        # Pure black — keyed out in pack_spritesheet.py (see Blender 5.1 note in setup_render).
        bg.inputs["Color"].default_value = (0.0, 0.0, 0.0, 1.0)
        bg.inputs["Strength"].default_value = 1.0


def add_sun_light(name, location, energy, rotation=(0, 0, 0)):
    light_data = bpy.data.lights.new(name, type="SUN")
    light_data.energy = energy
    light = bpy.data.objects.new(name, light_data)
    light.location = location
    light.rotation_euler = rotation
    bpy.context.collection.objects.link(light)
    return light


def add_area_light(name, location, energy, size, rotation=(0, 0, 0)):
    light_data = bpy.data.lights.new(name, type="AREA")
    light_data.energy = energy
    light_data.size = size
    light = bpy.data.objects.new(name, light_data)
    light.location = location
    light.rotation_euler = rotation
    bpy.context.collection.objects.link(light)
    return light


def setup_lights():
    # Strong 3-point + sun — Cycles PBR metals need plenty of light on a black backdrop.
    add_area_light("Key", (2.5, 5.5, 2.8), energy=8000, size=8,
                   rotation=(math.radians(-45), math.radians(15), 0))
    add_area_light("Fill", (-3.5, 4.5, 1.5), energy=3500, size=10,
                   rotation=(math.radians(-35), math.radians(-20), 0))
    add_area_light("Rim", (0.0, -3.0, 3.0), energy=5000, size=6,
                   rotation=(math.radians(115), 0, 0))
    add_sun_light("Sun", (4.0, 4.0, 6.0), energy=4.0,
                  rotation=(math.radians(-50), math.radians(15), 0))


def setup_camera():
    cam_data = bpy.data.cameras.new("ValveCam")
    cam_data.lens = 85
    cam_data.clip_start = 0.01
    cam_data.clip_end = 100.0
    cam = bpy.data.objects.new("ValveCam", cam_data)
    # Sit on +Y and look toward the tip at the origin (local -Z → world -Y).
    cam.location = (0.0, 6.5, 0.0)
    cam.rotation_euler = (math.radians(-90), 0.0, 0.0)
    bpy.context.collection.objects.link(cam)
    bpy.context.scene.camera = cam
    return cam


# --------------------------------------------------------------------------- #
# Animation + render
# --------------------------------------------------------------------------- #
def iter_action_fcurves(action):
    """Yield f-curves from an action (Blender 3.x legacy + 4.x/5.x layered actions)."""
    if action is None:
        return
    legacy = getattr(action, "fcurves", None)
    if legacy is not None:
        yield from legacy
        return
    for layer in getattr(action, "layers", []):
        for strip in getattr(layer, "strips", []):
            channelbag = getattr(strip, "channelbag", None)
            if channelbag is not None:
                yield from getattr(channelbag, "fcurves", [])


def set_fcurve_linear(action):
    for fcurve in iter_action_fcurves(action):
        for kp in fcurve.keyframe_points:
            kp.interpolation = "LINEAR"


def animate_disc(pivot):
    n = CONFIG["frame_count"]
    scene = bpy.context.scene
    scene.frame_start = 1
    scene.frame_end = n

    # Linear interpolation so in-app easing fully controls feel.
    pivot.rotation_euler = (0.0, 0.0, 0.0)
    pivot.keyframe_insert(data_path="rotation_euler", frame=1, index=0)
    pivot.rotation_euler = (math.radians(CONFIG["open_angle_deg"]), 0.0, 0.0)
    pivot.keyframe_insert(data_path="rotation_euler", frame=n, index=0)

    if pivot.animation_data and pivot.animation_data.action:
        set_fcurve_linear(pivot.animation_data.action)


def setup_render():
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = CONFIG["samples"]
    scene.render.resolution_x = CONFIG["res_x"]
    scene.render.resolution_y = CONFIG["res_y"]
    scene.render.resolution_percentage = 100
    # Blender 5.1 Cycles: film_transparent=True writes alpha=0 for ALL pixels (broken).
    # Render on pure black instead; pack_spritesheet.py keys the backdrop to alpha.
    scene.render.film_transparent = False
    scene.view_settings.exposure = 1.25
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.compression = 15

    if CONFIG["use_gpu"]:
        prefs = bpy.context.preferences.addons.get("cycles")
        if prefs:
            try:
                prefs.preferences.compute_device_type = "METAL"  # macOS; CUDA/OPTIX on others
                for dev in prefs.preferences.devices:
                    dev.use = True
                scene.cycles.device = "GPU"
            except Exception:
                scene.cycles.device = "CPU"


def render_sequence():
    os.makedirs(CONFIG["out_dir"], exist_ok=True)
    scene = bpy.context.scene
    for frame in range(scene.frame_start, scene.frame_end + 1):
        scene.frame_set(frame)
        idx = frame - scene.frame_start  # 0-based for the in-app manifest
        scene.render.filepath = os.path.join(CONFIG["out_dir"], f"frame_{idx:03d}.png")
        bpy.ops.render.render(write_still=True)
        print(f"[valve_render] wrote frame {idx + 1}/{CONFIG['frame_count']}")


def main():
    reset_scene()
    _, pivot = build_tip()
    setup_world()
    setup_lights()
    setup_camera()
    animate_disc(pivot)
    setup_render()
    # Only render when run headless; in the GUI, stop here so you can tweak.
    if bpy.app.background:
        render_sequence()
        print(f"[valve_render] done -> {CONFIG['out_dir']}")
    else:
        print("[valve_render] scene built. Tweak the look, then run headless to render.")


if __name__ == "__main__":
    main()
