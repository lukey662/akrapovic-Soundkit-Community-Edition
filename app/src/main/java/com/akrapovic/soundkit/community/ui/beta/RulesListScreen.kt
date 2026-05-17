package com.akrapovic.soundkit.community.ui.beta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.rules.Rule
import com.akrapovic.soundkit.community.domain.rules.RuleAction
import com.akrapovic.soundkit.community.domain.rules.RuleTrigger
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen

@Composable
fun RulesListScreen(
    modifier: Modifier = Modifier,
    rules: List<Rule>,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onRuleEnabledChanged: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Beta",
            title = "Rules",
            subtitle = "When a trigger matches and you are connected, the app may open or close valves.",
        )

        AkraActionButton(
            label = "Add rule",
            onClick = onAddRule,
            contentDescription = "Add automation rule",
        )

        if (rules.isEmpty()) {
            AkraCard(accent = MaterialTheme.colorScheme.onSurfaceVariant) {
                Text(
                    text = "No rules yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Create a schedule or geofence rule to automate valves while connected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rules.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        onEdit = { onEditRule(rule.id) },
                        onEnabledChange = { onRuleEnabledChanged(rule.id, it) },
                        onDelete = { onDeleteRule(rule.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: Rule,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    AkraCard(accent = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${rule.action.label()} · ${rule.trigger.label()} · priority ${rule.priority}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
        }
        Row {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private fun RuleAction.label(): String = when (this) {
    RuleAction.Open -> "Open"
    RuleAction.Close -> "Close"
    RuleAction.Toggle -> "Toggle"
}

private fun RuleTrigger.label(): String = when (this) {
    is RuleTrigger.Schedule -> "Schedule"
    is RuleTrigger.Geofence -> "Geofence"
    RuleTrigger.Manual -> "Manual"
}
