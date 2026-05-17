package com.akrapovic.soundkit.community.ui.beta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import java.text.DateFormat
import java.util.Date

@Composable
fun AutomationLogScreen(
    modifier: Modifier = Modifier,
    entries: List<RuleExecutionEntry>,
    onClear: () -> Unit,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Beta",
            title = "Activity log",
            subtitle = "Recent automation attempts while connected.",
        )

        if (entries.isNotEmpty()) {
            AkraActionButton(
                label = "Clear log",
                filled = false,
                onClick = onClear,
                contentDescription = "Clear automation log",
            )
        }

        if (entries.isEmpty()) {
            AkraCard(accent = MaterialTheme.colorScheme.onSurfaceVariant) {
                Text("No automation activity yet.")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.asReversed().forEach { entry ->
                    AkraCard(accent = MaterialTheme.colorScheme.primary) {
                        Text(entry.displaySummary(), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = DateFormat.getDateTimeInstance().format(Date(entry.timestampMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = entry.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        entry.detail?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
