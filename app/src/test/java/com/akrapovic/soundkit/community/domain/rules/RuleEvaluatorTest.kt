package com.akrapovic.soundkit.community.domain.rules

import com.akrapovic.soundkit.community.domain.ValveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEvaluatorTest {
    private val mondayMorning = 0 * (24 * 60) + (9 * 60) // Monday 09:00

    @Test
    fun manualOverrideWinsOverSchedule() {
        val rules = listOf(
            scheduleRule(id = "sport", priority = 10, action = RuleAction.Open),
            manualRule(id = "manual", priority = 1, action = RuleAction.Close),
        )
        val context = connectedContext(
            manualOverrideAction = RuleAction.Close,
        )

        val decision = RuleEvaluator.evaluate(rules, context)

        assertEquals(RuleAction.Close, decision?.action)
        assertEquals("manual_override", decision?.reason)
    }

    @Test
    fun manualPauseBlocksAutomation() {
        val rules = listOf(
            scheduleRule(id = "sport", priority = 10, action = RuleAction.Open),
        )
        val context = connectedContext(manualPause = true)

        assertNull(RuleEvaluator.evaluate(rules, context))
    }

    @Test
    fun higherPriorityScheduleWins() {
        val rules = listOf(
            scheduleRule(id = "quiet", priority = 1, action = RuleAction.Close),
            scheduleRule(id = "sport", priority = 10, action = RuleAction.Open),
        )
        val context = connectedContext(nowEpochMinuteOfWeek = mondayMorning)

        val decision = RuleEvaluator.evaluate(rules, context)

        assertEquals("sport", decision?.rule?.id)
        assertEquals(RuleAction.Open, decision?.action)
    }

    @Test
    fun disabledRulesIgnored() {
        val rules = listOf(
            scheduleRule(id = "sport", priority = 10, action = RuleAction.Open, enabled = false),
        )

        assertNull(RuleEvaluator.evaluate(rules, connectedContext(nowEpochMinuteOfWeek = mondayMorning)))
    }

    @Test
    fun notConnectedReturnsNull() {
        val rules = listOf(scheduleRule(id = "sport", priority = 1, action = RuleAction.Open))
        val context = RuleContext(
            nowEpochMinuteOfWeek = mondayMorning,
            activeGeofenceZoneIds = emptySet(),
            connectionConnected = false,
            valveState = ValveState.Closed,
            manualPause = false,
        )

        assertNull(RuleEvaluator.evaluate(rules, context))
    }

    private fun scheduleRule(
        id: String,
        priority: Int,
        action: RuleAction,
        enabled: Boolean = true,
    ) = Rule(
        id = id,
        name = id,
        enabled = enabled,
        priority = priority,
        trigger = RuleTrigger.Schedule(
            daysOfWeek = setOf(0),
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 18 * 60,
        ),
        action = action,
    )

    private fun manualRule(id: String, priority: Int, action: RuleAction) = Rule(
        id = id,
        name = id,
        priority = priority,
        trigger = RuleTrigger.Manual,
        action = action,
    )

    private fun connectedContext(
        nowEpochMinuteOfWeek: Int = mondayMorning,
        manualPause: Boolean = false,
        manualOverrideAction: RuleAction? = null,
    ) = RuleContext(
        nowEpochMinuteOfWeek = nowEpochMinuteOfWeek,
        activeGeofenceZoneIds = emptySet(),
        connectionConnected = true,
        valveState = ValveState.Closed,
        manualPause = manualPause,
        manualOverrideAction = manualOverrideAction,
    )
}
