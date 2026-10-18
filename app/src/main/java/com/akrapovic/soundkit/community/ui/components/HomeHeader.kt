package com.akrapovic.soundkit.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.theme.GarageThemeFamilies
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

/**
 * Home header shared by the connected and disconnected states: app title, brand stripes with the
 * theme family, and a receiver chip whose dot colour carries the connection state.
 */
@Composable
fun HomeHeader(
    receiverName: String,
    statusLabel: String,
    dot: Color,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAkraTheme.current
    val familyName = GarageThemeFamilies.firstOrNull { it.contains(theme.id) }?.name ?: theme.name
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Car Sound Kit",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BrandStripes(theme)
                Text(
                    text = familyName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ReceiverChip(name = receiverName, statusLabel = statusLabel, dot = dot)
    }
}

@Composable
private fun ReceiverChip(name: String, statusLabel: String, dot: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Text(
            text = "$name · $statusLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
