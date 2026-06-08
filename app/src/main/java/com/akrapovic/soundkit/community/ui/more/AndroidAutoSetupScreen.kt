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
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraScreen

@Composable
fun AndroidAutoSetupScreen(modifier: Modifier = Modifier) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            title = "Android Auto",
            subtitle = "Show Sound Kit on your car display (sideload + dev mode).",
            compact = true,
            titleModifier = Modifier.semantics { heading() },
        )

        AkraListGroup {
            SetupStep(
                number = 1,
                title = "Connect on the phone first",
                body = "Pair to your Sound Kit receiver once on Home so a default receiver is saved.",
            )
            SetupStep(
                number = 2,
                title = "Enable AA Developer mode",
                body = "Open the Android Auto app → Settings → tap the version line at the bottom 10 times.",
            )
            SetupStep(
                number = 3,
                title = "Allow unknown sources",
                body = "Android Auto ⋮ menu → Developer settings → turn on Unknown sources.",
            )
            SetupStep(
                number = 4,
                title = "Customize launcher",
                body = "Android Auto → Display → Customize launcher → enable Sound Kit if it appears in the list.",
            )
            SetupStep(
                number = 5,
                title = "Reconnect to the car",
                body = "USB or wireless Android Auto, then open Sound Kit from the car launcher while parked.",
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "If Sound Kit never appears in the car launcher, use the foreground notification or Quick Settings tile while connected on the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Play Store Android Auto listing is not available for valve controllers. Sideload + developer mode is the supported personal path.",
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
