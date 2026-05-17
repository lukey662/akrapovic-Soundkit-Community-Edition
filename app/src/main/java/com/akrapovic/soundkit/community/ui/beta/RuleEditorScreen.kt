package com.akrapovic.soundkit.community.ui.beta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.GeofenceZone
import com.akrapovic.soundkit.community.domain.rules.Rule
import com.akrapovic.soundkit.community.domain.rules.RuleAction
import com.akrapovic.soundkit.community.domain.rules.RuleTrigger
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    modifier: Modifier = Modifier,
    ruleId: String?,
    existing: Rule?,
    zones: List<GeofenceZone>,
    onSave: (Rule) -> Unit,
    onNewId: () -> String,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var action by remember { mutableStateOf(existing?.action ?: RuleAction.Open) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 0) }
    var triggerType by remember {
        mutableStateOf(
            when (existing?.trigger) {
                is RuleTrigger.Geofence -> TriggerType.Geofence
                else -> TriggerType.Schedule
            },
        )
    }
    var selectedDays by remember {
        mutableStateOf(
            (existing?.trigger as? RuleTrigger.Schedule)?.daysOfWeek ?: setOf(0, 1, 2, 3, 4, 5, 6),
        )
    }
    var startMinute by remember {
        mutableIntStateOf((existing?.trigger as? RuleTrigger.Schedule)?.startMinuteOfDay ?: 8 * 60)
    }
    var endMinute by remember {
        mutableIntStateOf((existing?.trigger as? RuleTrigger.Schedule)?.endMinuteOfDay ?: 18 * 60)
    }
    var zoneId by remember {
        mutableStateOf((existing?.trigger as? RuleTrigger.Geofence)?.zoneId ?: zones.firstOrNull()?.id.orEmpty())
    }
    var onEnter by remember {
        mutableStateOf((existing?.trigger as? RuleTrigger.Geofence)?.onEnter ?: true)
    }

    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Beta",
            title = if (existing == null) "New rule" else "Edit rule",
            subtitle = "Automation runs only when connected and the receiver is ready.",
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        AkraCard(accent = MaterialTheme.colorScheme.primary) {
            Text("Action", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RuleAction.Open, RuleAction.Close, RuleAction.Toggle).forEach { item ->
                    FilterChip(
                        selected = action == item,
                        onClick = { action = item },
                        label = { Text(item.label()) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = priority.toString(),
            onValueChange = { priority = it.toIntOrNull() ?: 0 },
            label = { Text("Priority (higher wins)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        AkraCard(accent = MaterialTheme.colorScheme.secondary) {
            Text("Trigger", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerType.entries.forEach { type ->
                    FilterChip(
                        selected = triggerType == type,
                        onClick = { triggerType = type },
                        label = { Text(type.name) },
                    )
                }
            }
        }

        when (triggerType) {
            TriggerType.Schedule -> ScheduleFields(
                selectedDays = selectedDays,
                onDaysChange = { selectedDays = it },
                startMinute = startMinute,
                endMinute = endMinute,
                onStartChange = { startMinute = it },
                onEndChange = { endMinute = it },
            )
            TriggerType.Geofence -> GeofenceFields(
                zones = zones,
                zoneId = zoneId,
                onZoneIdChange = { zoneId = it },
                onEnter = onEnter,
                onOnEnterChange = { onEnter = it },
            )
        }

        Spacer(Modifier.height(8.dp))

        AkraActionButton(
            label = "Save rule",
            enabled = name.isNotBlank() && (triggerType != TriggerType.Geofence || zoneId.isNotBlank()),
            onClick = {
                val trigger: RuleTrigger = when (triggerType) {
                    TriggerType.Schedule -> RuleTrigger.Schedule(
                        daysOfWeek = selectedDays,
                        startMinuteOfDay = startMinute,
                        endMinuteOfDay = endMinute,
                    )
                    TriggerType.Geofence -> RuleTrigger.Geofence(zoneId = zoneId, onEnter = onEnter)
                }
                onSave(
                    Rule(
                        id = ruleId ?: onNewId(),
                        name = name.trim(),
                        enabled = existing?.enabled ?: true,
                        trigger = trigger,
                        action = action,
                        priority = priority,
                    ),
                )
            },
            contentDescription = "Save automation rule",
        )
    }
}

private enum class TriggerType { Schedule, Geofence }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleFields(
    selectedDays: Set<Int>,
    onDaysChange: (Set<Int>) -> Unit,
    startMinute: Int,
    endMinute: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    AkraCard(accent = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text("Days", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dayLabels.forEachIndexed { index, label ->
                FilterChip(
                    selected = index in selectedDays,
                    onClick = {
                        onDaysChange(
                            if (index in selectedDays) selectedDays - index else selectedDays + index,
                        )
                    },
                    label = { Text(label) },
                )
            }
        }
        OutlinedTextField(
            value = formatMinute(startMinute),
            onValueChange = { onStartChange(parseMinute(it, startMinute)) },
            label = { Text("Start (HH:MM)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = formatMinute(endMinute),
            onValueChange = { onEndChange(parseMinute(it, endMinute)) },
            label = { Text("End (HH:MM)") },
            singleLine = true,
        )
    }
}

@Composable
private fun GeofenceFields(
    zones: List<GeofenceZone>,
    zoneId: String,
    onZoneIdChange: (String) -> Unit,
    onEnter: Boolean,
    onOnEnterChange: (Boolean) -> Unit,
) {
    AkraCard(accent = MaterialTheme.colorScheme.onSurfaceVariant) {
        if (zones.isEmpty()) {
            Text("Add a geofence zone first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Zone", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                zones.forEach { zone ->
                    FilterChip(
                        selected = zoneId == zone.id,
                        onClick = { onZoneIdChange(zone.id) },
                        label = { Text(zone.name) },
                    )
                }
            }
            FilterChip(
                selected = onEnter,
                onClick = { onOnEnterChange(true) },
                label = { Text("When entering") },
            )
            FilterChip(
                selected = !onEnter,
                onClick = { onOnEnterChange(false) },
                label = { Text("When leaving") },
            )
        }
    }
}

private fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}

private fun RuleAction.label(): String = when (this) {
    RuleAction.Open -> "Open"
    RuleAction.Close -> "Close"
    RuleAction.Toggle -> "Toggle"
}

private fun parseMinute(text: String, fallback: Int): Int {
    val parts = text.split(":")
    if (parts.size != 2) return fallback
    val h = parts[0].toIntOrNull() ?: return fallback
    val m = parts[1].toIntOrNull() ?: return fallback
    return (h.coerceIn(0, 23) * 60) + m.coerceIn(0, 59)
}
