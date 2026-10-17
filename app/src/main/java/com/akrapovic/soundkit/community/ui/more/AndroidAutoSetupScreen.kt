package com.akrapovic.soundkit.community.ui.more

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.BuildConfig
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraScreen

@Composable
fun AndroidAutoSetupScreen(modifier: Modifier = Modifier) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            title = "Android Auto",
            subtitle = "Android Auto lives in system Settings on modern phones, not a launcher app.",
            compact = true,
            titleModifier = Modifier.semantics { heading() },
        )

        AkraListGroup {
            SetupStep(
                number = 1,
                title = "Connect on the phone first",
                body = "Finish onboarding, grant Bluetooth permission, connect once on Home, and choose a default receiver. " +
                    "In Settings, keep “Connect in car” on if the car screen should reconnect that receiver.",
            )
            SetupStep(
                number = 2,
                title = "Why Customize launcher is empty",
                body = "This car screen uses the Android for Cars App Library. Google only lists those apps in a real car when the phone installed them from Play. " +
                    "Unknown sources does not apply to this kind of app, so this install (${BuildConfig.APPLICATION_ID}) stays off the launcher.",
            )
            SetupStep(
                number = 3,
                title = "Use the phone while parked",
                body = "Open and close from the notification or the Quick Settings tile. " +
                    "Connect in car still reconnects the receiver from the car's Bluetooth without a car-screen icon.",
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "If onboarding, Bluetooth permission, or a default receiver is missing, the car shows a phone-directed setup message instead of controls. " +
                "Finish setup on the phone, then reconnect to the car.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "A Play internal test or Internal App Sharing install is what puts Sound Kit on the car screen. " +
                "A public Play listing is still unavailable: a valve controller does not fit a published Android Auto category. " +
                "The Desktop Head Unit can still open this screen on a computer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    body: String,
) {
    Text(
        text = "$number. $title",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}
