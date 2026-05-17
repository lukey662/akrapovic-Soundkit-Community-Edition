package com.akrapovic.soundkit.community.ui.beta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akrapovic.soundkit.community.automation.AutomationScheduler
import com.akrapovic.soundkit.community.automation.GeofenceRegistrar
import com.akrapovic.soundkit.community.data.GeofenceZonesRepository
import com.akrapovic.soundkit.community.data.GeofenceZonesStore
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.data.RulesRepository
import com.akrapovic.soundkit.community.data.RulesStore
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.GeofenceZone
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.rules.Rule
import com.akrapovic.soundkit.community.domain.rules.RuleAction
import com.akrapovic.soundkit.community.domain.rules.RuleTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BetaUiState(
    val settings: SoundKitSettings = SoundKitSettings(),
    val rules: List<Rule> = emptyList(),
    val zones: List<GeofenceZone> = emptyList(),
    val logEntries: List<RuleExecutionEntry> = emptyList(),
)

@HiltViewModel
class BetaViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val rulesStore: RulesStore,
    private val zonesStore: GeofenceZonesStore,
    private val executionLog: RuleExecutionLogStore,
    private val rulesRepository: RulesRepository,
    private val zonesRepository: GeofenceZonesRepository,
    private val automationScheduler: AutomationScheduler,
    private val geofenceRegistrar: GeofenceRegistrar,
) : ViewModel() {
    val uiState = combine(
        settingsStore.settings,
        rulesStore.rules,
        zonesStore.zones,
        executionLog.entries,
    ) { settings, rules, zones, log ->
        BetaUiState(settings, rules, zones, log)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BetaUiState())

    fun acceptBetaDisclaimer() {
        viewModelScope.launch {
            settingsStore.acceptBetaDisclaimer()
            syncAutomation()
        }
    }

    fun setAutomationPaused(paused: Boolean) {
        viewModelScope.launch { settingsStore.setAutomationPaused(paused) }
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            rulesStore.setRuleEnabled(id, enabled)
            syncAutomation()
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            rulesStore.deleteRule(id)
            syncAutomation()
        }
    }

    fun saveRule(rule: Rule) {
        viewModelScope.launch {
            rulesStore.upsertRule(rule)
            syncAutomation()
        }
    }

    fun newRuleId(): String = rulesRepository.newRuleId()

    fun saveZone(zone: GeofenceZone) {
        viewModelScope.launch {
            zonesStore.upsertZone(zone)
            geofenceRegistrar.syncZones()
            syncAutomation()
        }
    }

    fun deleteZone(id: String) {
        viewModelScope.launch {
            zonesStore.deleteZone(id)
            rulesStore.rules.first()
                .filter { it.trigger is RuleTrigger.Geofence && (it.trigger as RuleTrigger.Geofence).zoneId == id }
                .forEach { rulesStore.deleteRule(it.id) }
            geofenceRegistrar.syncZones()
            syncAutomation()
        }
    }

    fun newZoneId(): String = zonesRepository.newZoneId()

    fun clearLog() {
        viewModelScope.launch { executionLog.clear() }
    }

    private suspend fun syncAutomation() {
        automationScheduler.syncSchedule(rulesStore.rules.first())
    }

    init {
        viewModelScope.launch {
            rulesStore.rules.collect { automationScheduler.syncSchedule(it) }
        }
        viewModelScope.launch {
            zonesStore.zones.collect { zones ->
                if (zones.isNotEmpty()) {
                    runCatching { geofenceRegistrar.syncZones() }
                } else {
                    geofenceRegistrar.removeAll()
                }
            }
        }
    }
}
