package com.akrapovic.soundkit.community.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.RuleExecutionOutcome
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraListDivider
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraSectionTitle
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Recent valve changes with where each one came from. Everything stays on the phone. */
@Composable
fun ActivityScreen(
    entries: List<RuleExecutionEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
) {
    AkraScreen(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AkraHeroHeader(
                title = "Activity",
                subtitle = "Recent valve changes and where they came from.",
                compact = true,
                modifier = Modifier.weight(1f),
                titleModifier = Modifier.semantics { heading() },
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = "No valve changes yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@AkraScreen
        }

        entries.sortedByDescending { it.timestampMillis }
            .groupBy { dayLabel(it.timestampMillis, nowMillis) }
            .forEach { (day, dayEntries) ->
                AkraSectionTitle(text = day)
                AkraListGroup {
                    dayEntries.forEachIndexed { index, entry ->
                        if (index > 0) AkraListDivider()
                        ActivityRow(entry)
                    }
                }
            }
    }
}

@Composable
private fun ActivityRow(entry: RuleExecutionEntry) {
    val (title, dot) = entry.titleAndColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = listOfNotNull(entry.ruleName, entry.detail).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestampMillis)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RuleExecutionEntry.titleAndColor(): Pair<String, Color> {
    val opening = action.startsWith("Open")
    return when (outcome) {
        RuleExecutionOutcome.Success -> (if (opening) "Opened" else "Closed") to AkraColors.Signal
        RuleExecutionOutcome.Failed -> (if (opening) "Open failed" else "Close failed") to AkraColors.Danger
        RuleExecutionOutcome.Skipped -> (if (opening) "Already open" else "Already closed") to AkraColors.Mist
    }
}

private fun dayLabel(timestampMillis: Long, nowMillis: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = timestampMillis }
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    fun Calendar.sameDayAs(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    if (day.sameDayAs(today)) return "Today"
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (day.sameDayAs(yesterday)) return "Yesterday"
    return SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(timestampMillis))
}
