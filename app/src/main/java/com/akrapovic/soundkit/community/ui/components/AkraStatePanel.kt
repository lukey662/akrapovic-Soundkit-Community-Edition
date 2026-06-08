package com.akrapovic.soundkit.community.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AkraStatePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    primaryContentDescription: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryContentDescription: String? = null,
) {
    AkraElevated(modifier = modifier.fillMaxWidth()) {
        if (eyebrow != null) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (primaryLabel != null && onPrimary != null) {
            AkraActionButton(
                label = primaryLabel,
                contentDescription = primaryContentDescription ?: primaryLabel,
                onClick = onPrimary,
            )
        }
        if (secondaryLabel != null && onSecondary != null) {
            AkraActionButton(
                label = secondaryLabel,
                filled = false,
                contentDescription = secondaryContentDescription ?: secondaryLabel,
                onClick = onSecondary,
            )
        }
    }
}
