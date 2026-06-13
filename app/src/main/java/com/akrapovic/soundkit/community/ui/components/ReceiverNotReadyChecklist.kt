package com.akrapovic.soundkit.community.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object ReceiverNotReadyChecklist {
    val steps: List<String> = listOf(
        "Vehicle switched on (ignition or accessory) so the Sound Kit receiver has power.",
        "Engine running if your install requires it — some receivers stay in status 04 until ready.",
        "Phone within about 20 metres of the receiver.",
        "Receiver green LED indicates power; check the Akrapovič manual if the remote LED blinks white.",
        "Stay parked — retry Open/Close after a few seconds once the car is ready.",
        "Still stuck? Export diagnostics and email support@appsforgood.net.",
    )
}

@Composable
fun ReceiverNotReadyChecklistContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "The receiver is connected but not accepting valve changes yet. Try these steps:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReceiverNotReadyChecklist.steps.forEach { step ->
            Text(
                text = "•  $step",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
