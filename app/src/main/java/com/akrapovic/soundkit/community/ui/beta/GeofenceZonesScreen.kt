package com.akrapovic.soundkit.community.ui.beta

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.akrapovic.soundkit.community.domain.GeofenceZone
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill

@Composable
fun GeofenceZonesScreen(
    modifier: Modifier = Modifier,
    zones: List<GeofenceZone>,
    locationPermissionGranted: Boolean,
    onSaveZone: (GeofenceZone) -> Unit,
    onDeleteZone: (String) -> Unit,
    onNewZoneId: () -> String,
    onRequestLocationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val hasFineLocation = locationPermissionGranted ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    var editing by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(150f) }

    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Beta",
            title = "Geofence zones",
            subtitle = "Location is used only for enter/exit triggers. Nothing is uploaded.",
        )

        if (!hasFineLocation) {
            AkraCard(accent = MaterialTheme.colorScheme.primary) {
                AkraStatusPill(text = "Location")
                Text(
                    text = "Allow location to register geofences. Bluetooth scanning does not use this permission.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AkraActionButton(
                    label = "Grant location",
                    onClick = onRequestLocationPermission,
                    contentDescription = "Grant location permission for geofences",
                )
            }
        }

        if (editing) {
            AkraCard(accent = MaterialTheme.colorScheme.secondary) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude") })
                OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Longitude") })
                Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 50f..500f,
                )
                Row {
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                    AkraActionButton(
                        label = "Save zone",
                        enabled = name.isNotBlank() && lat.toDoubleOrNull() != null && lng.toDoubleOrNull() != null,
                        onClick = {
                            onSaveZone(
                                GeofenceZone(
                                    id = editId ?: onNewZoneId(),
                                    name = name.trim(),
                                    latitude = lat.toDouble(),
                                    longitude = lng.toDouble(),
                                    radiusMeters = radius,
                                ),
                            )
                            editing = false
                        },
                        contentDescription = "Save geofence zone",
                    )
                }
            }
        } else if (zones.size < 4) {
            AkraActionButton(
                label = "Add zone",
                enabled = hasFineLocation,
                onClick = {
                    editId = null
                    name = ""
                    lat = ""
                    lng = ""
                    radius = 150f
                    editing = true
                },
                contentDescription = "Add geofence zone",
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            zones.forEach { zone ->
                AkraCard(accent = MaterialTheme.colorScheme.primary) {
                    Text(zone.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${zone.latitude}, ${zone.longitude} · ${zone.radiusMeters.toInt()} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = {
                            editId = zone.id
                            name = zone.name
                            lat = zone.latitude.toString()
                            lng = zone.longitude.toString()
                            radius = zone.radiusMeters
                            editing = true
                        }) { Text("Edit") }
                        TextButton(onClick = { onDeleteZone(zone.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
