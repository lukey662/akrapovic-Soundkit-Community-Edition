package com.akrapovic.soundkit.community.domain.rules

fun RuleAction.label(): String = when (this) {
    RuleAction.Open -> "Open"
    RuleAction.Close -> "Close"
    RuleAction.Toggle -> "Toggle"
}
