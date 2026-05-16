package com.akrapovic.soundkit.community.ui.onboarding

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button

@Composable
fun RiskNoticeDialog(
    onAccept: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { /* blocking */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = {
            Text(
                text = "Use at your own risk",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { contentDescription = "Risk notice" },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Bullet("Sound Kit Community is an independent open-source project. It is not affiliated with, endorsed by, or supported by Akrapovič d.d. or the original publisher.")
                Bullet("The valve protocol was reverse-engineered from a public APK. Behavior may differ from the official app.")
                Bullet("Using this app may void your exhaust, vehicle, or component warranty.")
                Bullet("Operating exhaust valves can change emissions, sound levels, and legal compliance. You are responsible for following local noise and emissions regulations.")
                Bullet("Never operate the valves while driving. Use only when the vehicle is parked with safe ventilation.")
                Bullet("The app is provided as-is, with no warranty of any kind. You accept all risk of equipment damage, legal exposure, and personal injury that may result from use.")
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("I understand and accept")
            }
        },
        dismissButton = {
            TextButton(onClick = { (context as? Activity)?.finish() }) {
                Text("Exit app")
            }
        },
    )
}

@Composable
private fun Bullet(text: String) {
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
