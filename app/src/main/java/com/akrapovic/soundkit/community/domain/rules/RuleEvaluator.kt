package com.akrapovic.soundkit.community.domain.rules

import com.akrapovic.soundkit.community.domain.ValveState

/**
 * Pure rules evaluation for design spike — no BLE execution or persistence.
 *
 * Precedence: manual override > manual pause (no automation) > enabled rules by priority.
 */
object RuleEvaluator {
    fun evaluate(rules: List<Rule>, context: RuleContext): RuleDecision? {
        if (!context.connectionConnected) return null
        if (context.valveState == ValveState.Unknown) return null

        context.manualOverrideAction?.let { action ->
            return rules.firstOrNull { it.trigger is RuleTrigger.Manual && it.enabled }?.let { rule ->
                RuleDecision(rule = rule, action = action, reason = "manual_override")
            } ?: RuleDecision(
                rule = Rule(
                    id = "manual",
                    name = "Manual",
                    trigger = RuleTrigger.Manual,
                    action = action,
                ),
                action = action,
                reason = "manual_override",
            )
        }

        if (context.manualPause) return null

        val candidates = rules
            .filter { it.enabled }
            .sortedByDescending { it.priority }
            .mapNotNull { rule -> match(rule, context)?.let { action -> rule to action } }

        val (rule, action) = candidates.firstOrNull() ?: return null
        return RuleDecision(rule = rule, action = action, reason = triggerReason(rule.trigger, context))
    }

    private fun match(rule: Rule, context: RuleContext): RuleAction? {
        val triggerMatches = when (val trigger = rule.trigger) {
            is RuleTrigger.Schedule -> scheduleMatches(trigger, context.nowEpochMinuteOfWeek)
            is RuleTrigger.Geofence -> geofenceMatches(trigger, context.activeGeofenceZoneIds)
            RuleTrigger.Manual -> false
        }
        if (!triggerMatches) return null
        return rule.action
    }

    private fun scheduleMatches(trigger: RuleTrigger.Schedule, minuteOfWeek: Int): Boolean {
        val day = minuteOfWeek / (24 * 60)
        val minuteOfDay = minuteOfWeek % (24 * 60)
        if (day !in trigger.daysOfWeek) return false
        return minuteOfDay in trigger.startMinuteOfDay..trigger.endMinuteOfDay
    }

    private fun geofenceMatches(trigger: RuleTrigger.Geofence, activeZones: Set<String>): Boolean {
        val inZone = trigger.zoneId in activeZones
        return if (trigger.onEnter) inZone else !inZone
    }

    private fun triggerReason(trigger: RuleTrigger, context: RuleContext): String {
        return when (trigger) {
            is RuleTrigger.Schedule -> "schedule"
            is RuleTrigger.Geofence -> "geofence:${trigger.zoneId}"
            RuleTrigger.Manual -> "manual"
        }
    }
}
