package com.akrapovic.soundkit.community.domain.rules

import com.akrapovic.soundkit.community.domain.ValveState

data class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val trigger: RuleTrigger,
    val action: RuleAction,
    val priority: Int = 0,
)

sealed interface RuleTrigger {
    data class Schedule(
        val daysOfWeek: Set<Int>,
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
    ) : RuleTrigger

    data class Geofence(
        val zoneId: String,
        val onEnter: Boolean,
    ) : RuleTrigger

    data object Manual : RuleTrigger
}

sealed interface RuleAction {
    data object Open : RuleAction
    data object Close : RuleAction
    data object Toggle : RuleAction
}

data class RuleContext(
    val nowEpochMinuteOfWeek: Int,
    val activeGeofenceZoneIds: Set<String>,
    val connectionConnected: Boolean,
    val valveState: ValveState,
    val manualPause: Boolean,
    val manualOverrideAction: RuleAction? = null,
)

data class RuleDecision(
    val rule: Rule,
    val action: RuleAction,
    val reason: String,
)
