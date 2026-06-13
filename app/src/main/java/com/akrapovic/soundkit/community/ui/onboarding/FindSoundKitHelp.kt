package com.akrapovic.soundkit.community.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun FindSoundKitHelp(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Finding your receiver",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HelpBullet("The BLE discovery name is usually SoundKit.")
        HelpBullet("The six-digit pairing PIN is printed on the receiver and in your Sound Kit manual.")
        HelpBullet("Switch the vehicle on before scanning — the receiver needs power.")
        HelpBullet("Install only the Akrapovič Car Sound Kit receiver, not the motorcycle app kit.")
    }
}

@Composable
private fun HelpBullet(text: String) {
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
