package com.akrapovic.soundkit.community.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.akrapovic.soundkit.community.ui.theme.GarageTheme

/** Colors the valve art takes from the active theme. */
data class ValveArtPalette(
    val glow: Color,
    val heatTintedLip: Boolean,
) {
    companion object {
        fun from(theme: GarageTheme): ValveArtPalette = ValveArtPalette(theme.glow, theme.heatTintedLip)
    }
}

/**
 * Front-on exhaust tip: carbon twill sleeve, brushed titanium lip, deep bore, and a butterfly
 * disc on a steel shaft. Opening tilts the disc edge-on and reveals the baffle and ember glow.
 *
 * Pure drawing with no Compose state, so the phone, Android Auto tiles, and the widget share it.
 * Radii are fractions of the sleeve radius R.
 */
object ValveArt {
    private const val SLEEVE = 0.44f

    private val tiStops = arrayOf(
        0f to Color(0xFFF7F7F9),
        0.18f to Color(0xFFC3C6CC),
        0.34f to Color(0xFF7B7F86),
        0.50f to Color(0xFFE3E5E9),
        0.66f to Color(0xFF686B72),
        0.82f to Color(0xFFB5B8BE),
        1f to Color(0xFF4A4C51),
    )
    private val heatTintStops = arrayOf(
        0.25f to Color(0x00D2AE62),
        0.48f to Color(0xBFD2AE62),
        0.64f to Color(0xD98A5FC9),
        0.80f to Color(0xE63E6FD9),
        1f to Color(0xE62B4EA8),
    )

    private val twillBase: ImageBitmap by lazy {
        tile(12) { canvas, paint ->
            paint.color = Color(0xFF1E1E22)
            canvas.drawRect(Rect(0f, 0f, 6f, 12f), paint)
            paint.color = Color(0xFF111114)
            canvas.drawRect(Rect(6f, 0f, 12f, 12f), paint)
        }
    }
    private val twillCross: ImageBitmap by lazy {
        tile(12) { canvas, paint ->
            paint.color = Color.White.copy(alpha = 0.04f)
            canvas.drawRect(Rect(0f, 0f, 12f, 6f), paint)
        }
    }
    private val perforation: ImageBitmap by lazy {
        tile(7) { canvas, paint ->
            paint.color = Color(0xFF2C2D31)
            canvas.drawRect(Rect(0f, 0f, 7f, 7f), paint)
            paint.color = Color(0xFF020202)
            canvas.drawCircle(Offset(3.5f, 3.5f), 1.7f, paint)
        }
    }

    private fun tile(size: Int, draw: (Canvas, Paint) -> Unit): ImageBitmap {
        val image = ImageBitmap(size, size)
        draw(Canvas(image), Paint())
        return image
    }

    private fun patternBrush(image: ImageBitmap, degrees: Float): Brush {
        val shader = ImageShader(image, TileMode.Repeated, TileMode.Repeated)
        if (degrees != 0f) {
            val matrix = android.graphics.Matrix().apply { setRotate(degrees) }
            shader.setLocalMatrix(matrix)
        }
        return ShaderBrush(shader)
    }

    /**
     * Draws the tip centered in this scope.
     * @param openAmount 0 closed (disc face-on) to 1 open (disc edge-on, full heat).
     */
    fun DrawScope.drawValve(
        openAmount: Float,
        palette: ValveArtPalette,
        contentAlpha: Float = 1f,
        busyRing: Color? = null,
        ripple: Float = 0f,
    ) {
        val r = size.minDimension * SLEEVE
        val c = center
        val heat = ValveGapMath.heatAlpha(openAmount)
        val discScaleY = ValveGapMath.discHeightScale(openAmount)
        val glow = palette.glow
        val a = contentAlpha
        val tl = Offset(c.x - r, c.y - r)
        val br = Offset(c.x + r, c.y + r)

        if (heat > 0.02f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.45f to glow.copy(alpha = 0.32f * heat * a),
                    1f to Color.Transparent,
                    center = c,
                    radius = r * 1.32f,
                ),
                radius = r * 1.32f,
                center = c,
            )
        }

        val sleeve = Path().apply { addOval(Rect(c, r)) }
        clipPath(sleeve) {
            drawCircle(brush = patternBrush(twillBase, 45f), radius = r, center = c, alpha = a)
            drawCircle(brush = patternBrush(twillCross, -45f), radius = r, center = c, alpha = a)
        }
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.72f to Color.Black.copy(alpha = 0.18f),
                1f to Color.Black.copy(alpha = 0.65f),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
            alpha = a,
        )
        drawCircle(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.18f),
                0.5f to Color.Transparent,
                start = tl,
                end = br,
            ),
            radius = r,
            center = c,
            alpha = a,
        )
        drawCircle(
            brush = Brush.linearGradient(
                0f to Color(0xFF7A7C82),
                0.5f to Color(0xFF2A2B2F),
                1f to Color(0xFF0A0A0B),
                start = tl,
                end = br,
            ),
            radius = r * 0.99f,
            center = c,
            style = Stroke(width = r * 0.024f),
            alpha = a,
        )

        drawCircle(color = Color(0xFF08080A), radius = r * 0.887f, center = c, alpha = a)
        val lipR = r * 0.835f
        val lipWidth = r * 0.113f
        drawCircle(
            brush = Brush.linearGradient(*tiStops, start = tl, end = br),
            radius = lipR,
            center = c,
            style = Stroke(width = lipWidth),
            alpha = a,
        )
        if (palette.heatTintedLip) {
            drawCircle(
                brush = Brush.verticalGradient(*heatTintStops, startY = c.y - r, endY = c.y + r),
                radius = lipR,
                center = c,
                style = Stroke(width = lipWidth),
                alpha = a,
            )
        }
        val hairline = 1f * density
        drawCircle(
            color = Color.White.copy(alpha = 0.38f * a),
            radius = r * 0.892f,
            center = c,
            style = Stroke(width = hairline),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.7f * a),
            radius = r * 0.779f,
            center = c,
            style = Stroke(width = hairline * 1.5f),
        )

        val boreR = r * 0.774f
        val bore = Path().apply { addOval(Rect(c, boreR)) }
        clipPath(bore) {
            drawCircle(color = Color(0xFF4A4C50), radius = boreR, center = c, alpha = a)
            val focus = Offset(c.x - r * 0.08f, c.y - r * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Black,
                    0.55f to Color(0xFF060607),
                    0.82f to Color(0xFF1C1D20),
                    0.95f to Color(0xFF34363A),
                    1f to Color(0xFF4A4C50),
                    center = focus,
                    radius = r * 0.86f,
                ),
                radius = boreR * 1.2f,
                center = focus,
                alpha = a,
            )
            if (heat > 0.02f) {
                drawCircle(
                    brush = patternBrush(perforation, 0f),
                    radius = r * 0.415f,
                    center = Offset(c.x, c.y + r * 0.02f),
                    alpha = heat * 0.55f * a,
                )
                val emberCenter = Offset(c.x, c.y + r * 0.06f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color(0xFFFFF4E0).copy(alpha = 0.7f),
                        0.2f to glow.copy(alpha = 0.9f),
                        0.58f to glow.copy(alpha = 0.32f),
                        1f to Color.Transparent,
                        center = emberCenter,
                        radius = r * 0.755f,
                    ),
                    radius = r * 0.755f,
                    center = Offset(c.x, c.y + r * 0.04f),
                    alpha = heat * a,
                )
            }
        }

        val discR = r * 0.717f
        val discH = discR * 2f * discScaleY
        val discTopLeft = Offset(c.x - discR, c.y - discH / 2f)
        val discSize = Size(discR * 2f, discH)
        drawOval(
            brush = Brush.linearGradient(
                0f to Color(0xFF6A6C72),
                0.45f to Color(0xFF2A2B2F),
                0.7f to Color(0xFF141416),
                1f to Color(0xFF3A3C41),
                start = Offset(discTopLeft.x + discSize.width * 0.2f, discTopLeft.y),
                end = Offset(discTopLeft.x + discSize.width * 0.8f, discTopLeft.y + discH),
            ),
            topLeft = discTopLeft,
            size = discSize,
            alpha = a,
        )
        drawOval(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.2f),
                1f to Color.Transparent,
                center = Offset(discTopLeft.x + discSize.width * 0.35f, discTopLeft.y + discH * 0.3f),
                radius = discR * 1.2f,
            ),
            topLeft = discTopLeft,
            size = discSize,
            alpha = a,
        )
        drawOval(
            color = Color.White.copy(alpha = 0.3f * a),
            topLeft = discTopLeft,
            size = discSize,
            style = Stroke(width = hairline * 1.2f),
        )

        val shaftH = r * 0.066f
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFFECEEF1),
                0.5f to Color(0xFF8D9096),
                1f to Color(0xFF3C3E43),
                startY = c.y - shaftH / 2f,
                endY = c.y + shaftH / 2f,
            ),
            topLeft = Offset(c.x - r * 0.84f, c.y - shaftH / 2f),
            size = Size(r * 1.68f, shaftH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(shaftH / 2f),
            alpha = a,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFF2F3F6),
                0.6f to Color(0xFF8A8D93),
                1f to Color(0xFF3A3C41),
                center = Offset(c.x - r * 0.017f, c.y - r * 0.025f),
                radius = r * 0.085f,
            ),
            radius = r * 0.085f,
            center = c,
            alpha = a,
        )
        drawCircle(color = Color(0xFF1A1A1C), radius = r * 0.028f, center = c, alpha = a)
        listOf(-1f, 1f).forEach { side ->
            val bolt = Offset(c.x + side * r * 0.821f, c.y)
            drawCircle(color = Color(0xFFD0D1D5), radius = r * 0.052f, center = bolt, alpha = a)
            drawCircle(color = Color(0xFF2A2A2C), radius = r * 0.021f, center = bolt, alpha = a)
        }

        val arcR = r * 0.81f
        drawArc(
            color = Color.White.copy(alpha = 0.5f * a),
            startAngle = 210f,
            sweepAngle = 44f,
            useCenter = false,
            topLeft = Offset(c.x - arcR, c.y - arcR),
            size = Size(arcR * 2f, arcR * 2f),
            style = Stroke(width = 2.5f * density, cap = StrokeCap.Round),
        )

        if (busyRing != null) {
            drawCircle(color = busyRing, radius = r, center = c, style = Stroke(width = 2f * density))
        }
        if (ripple > 0f) {
            drawCircle(
                color = glow.copy(alpha = (1f - ripple) * 0.35f),
                radius = boreR * (0.9f + ripple * 0.25f),
                center = c,
                style = Stroke(width = 2.5f * density, cap = StrokeCap.Round),
            )
        }
    }
}
