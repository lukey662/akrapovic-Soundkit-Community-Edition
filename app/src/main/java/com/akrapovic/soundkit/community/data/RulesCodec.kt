package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.rules.Rule
import com.akrapovic.soundkit.community.domain.rules.RuleAction
import com.akrapovic.soundkit.community.domain.rules.RuleTrigger
import com.akrapovic.soundkit.community.domain.rules.label
import org.json.JSONArray
import org.json.JSONObject

object RulesCodec {
    const val MAX_RULES = 16

    fun encode(rules: List<Rule>): String {
        val array = JSONArray()
        rules.forEach { array.put(encodeRule(it)) }
        return array.toString()
    }

    fun decode(json: String?): List<Rule> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    add(decodeRule(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun normalize(rules: List<Rule>): List<Rule> = rules.distinctBy { it.id }.take(MAX_RULES)

    private fun encodeRule(rule: Rule): JSONObject {
        val obj = JSONObject()
        obj.put("id", rule.id)
        obj.put("name", rule.name)
        obj.put("enabled", rule.enabled)
        obj.put("priority", rule.priority)
        obj.put("action", rule.action.label())
        when (val trigger = rule.trigger) {
            is RuleTrigger.Schedule -> {
                obj.put("triggerType", "Schedule")
                obj.put("daysOfWeek", JSONArray(trigger.daysOfWeek.toList()))
                obj.put("startMinuteOfDay", trigger.startMinuteOfDay)
                obj.put("endMinuteOfDay", trigger.endMinuteOfDay)
            }
            is RuleTrigger.Geofence -> {
                obj.put("triggerType", "Geofence")
                obj.put("zoneId", trigger.zoneId)
                obj.put("onEnter", trigger.onEnter)
            }
            RuleTrigger.Manual -> obj.put("triggerType", "Manual")
        }
        return obj
    }

    private fun decodeRule(obj: JSONObject): Rule {
        val action = decodeAction(obj.getString("action"))
        val trigger = when (obj.getString("triggerType")) {
            "Schedule" -> {
                val daysArray = obj.getJSONArray("daysOfWeek")
                val days = buildSet {
                    for (i in 0 until daysArray.length()) {
                        add(daysArray.getInt(i))
                    }
                }
                RuleTrigger.Schedule(
                    daysOfWeek = days,
                    startMinuteOfDay = obj.getInt("startMinuteOfDay"),
                    endMinuteOfDay = obj.getInt("endMinuteOfDay"),
                )
            }
            "Geofence" -> RuleTrigger.Geofence(
                zoneId = obj.getString("zoneId"),
                onEnter = obj.getBoolean("onEnter"),
            )
            else -> RuleTrigger.Manual
        }
        return Rule(
            id = obj.getString("id"),
            name = obj.getString("name"),
            enabled = obj.optBoolean("enabled", true),
            priority = obj.optInt("priority", 0),
            trigger = trigger,
            action = action,
        )
    }

    private fun decodeAction(value: String): RuleAction = when (value) {
        "Open" -> RuleAction.Open
        "Close" -> RuleAction.Close
        "Toggle" -> RuleAction.Toggle
        else -> RuleAction.Open
    }
}
