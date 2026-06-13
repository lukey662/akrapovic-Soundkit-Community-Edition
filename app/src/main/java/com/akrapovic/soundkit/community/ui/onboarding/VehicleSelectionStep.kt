package com.akrapovic.soundkit.community.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.VehicleCompatibilityCatalog
import com.akrapovic.soundkit.community.domain.VehicleCompatibilityEntry
import com.akrapovic.soundkit.community.domain.VehicleSupportTier
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

private const val AKRAPOVIC_SOUND_KIT_URL =
    "https://www.akrapovic.com/en/car/products/sound-kit"

@Composable
fun VehicleSelectionContent(
    selectedVehicleId: String?,
    onSelectVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = LocalAkraTheme.current.accent
    val makes = VehicleCompatibilityCatalog.makes()
    var expandedMake by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = VehicleCompatibilityCatalog.findById(selectedVehicleId)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Pick the car with your Akrapovič Sound Kit. We use this for support and optional theme suggestions — not to limit what you can connect to.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        makes.forEach { make ->
            val models = VehicleCompatibilityCatalog.modelsForMake(make)
            val isExpanded = expandedMake == make
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .clickable { expandedMake = if (isExpanded) null else make }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = make,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isExpanded) {
                    models.forEach { entry ->
                        VehicleOptionRow(
                            entry = entry,
                            selected = selected?.id == entry.id,
                            accent = accent,
                            onClick = { onSelectVehicle(entry.id) },
                        )
                    }
                }
            }
        }

        VehicleOptionRow(
            entry = VehicleCompatibilityCatalog.unsupportedEntry(),
            selected = selected?.id == VehicleCompatibilityCatalog.NO_SOUND_KIT_ID,
            accent = accent,
            onClick = { onSelectVehicle(VehicleCompatibilityCatalog.NO_SOUND_KIT_ID) },
        )

        selected?.let { entry ->
            TierBadge(entry = entry)
        }

        FindSoundKitHelp()

        TextButton(
            onClick = { onSelectVehicle(VehicleCompatibilityCatalog.OTHER_SOUND_KIT_ID) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Other car with Sound Kit (Beta)")
        }

        if (selected?.tier == VehicleSupportTier.Unsupported) {
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(AKRAPOVIC_SOUND_KIT_URL)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Learn about Akrapovič Sound Kit")
            }
        }
    }
}

@Composable
private fun VehicleOptionRow(
    entry: VehicleCompatibilityEntry,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            )
            .border(
                width = 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "${entry.displayName}, ${entry.tierLabel}" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.model,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (entry.tier != VehicleSupportTier.Unsupported) {
            AkraStatusPill(
                text = entry.tierLabel.uppercase(),
                color = when (entry.tier) {
                    VehicleSupportTier.Supported -> MaterialTheme.colorScheme.primary
                    VehicleSupportTier.Beta -> LocalAkraTheme.current.secondaryAccent
                    VehicleSupportTier.Unsupported -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TierBadge(entry: VehicleCompatibilityEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = entry.tierDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun isVehicleSelectionComplete(selectedVehicleId: String?): Boolean {
    if (selectedVehicleId.isNullOrBlank()) return false
    val entry = VehicleCompatibilityCatalog.findById(selectedVehicleId) ?: return false
    return entry.tier != VehicleSupportTier.Unsupported
}
