package com.akrapovic.soundkit.community.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.RuleExecutionOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Context.executionLogDataStore by preferencesDataStore(name = "soundkit_rule_log")

interface RuleExecutionLogStore {
    val entries: Flow<List<RuleExecutionEntry>>
    val lastExecution: Flow<RuleExecutionEntry?>

    suspend fun append(entry: RuleExecutionEntry)
    suspend fun clear()
}

@Singleton
class RuleExecutionLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : RuleExecutionLogStore {
    private val jsonKey = stringPreferencesKey("execution_log_json")
    private val _lastExecution = MutableStateFlow<RuleExecutionEntry?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val entries: Flow<List<RuleExecutionEntry>> = context.executionLogDataStore.data.map { preferences ->
        decode(preferences[jsonKey])
    }

    override val lastExecution: Flow<RuleExecutionEntry?> = _lastExecution.asStateFlow()

    init {
        scope.launch {
            entries.collect { list -> _lastExecution.value = list.lastOrNull() }
        }
    }

    override suspend fun append(entry: RuleExecutionEntry) {
        context.executionLogDataStore.edit { preferences ->
            val updated = (decode(preferences[jsonKey]) + entry).takeLast(MAX_ENTRIES)
            preferences[jsonKey] = encode(updated)
        }
        _lastExecution.value = entry
    }

    override suspend fun clear() {
        context.executionLogDataStore.edit { it.remove(jsonKey) }
        _lastExecution.value = null
    }

    private fun encode(entries: List<RuleExecutionEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val item = JSONObject()
                .put("timestampMillis", entry.timestampMillis)
                .put("ruleName", entry.ruleName)
                .put("action", entry.action)
                .put("reason", entry.reason)
                .put("outcome", entry.outcome.name)
            entry.detail?.let { item.put("detail", it) }
            array.put(item)
        }
        return array.toString()
    }

    private fun decode(json: String?): List<RuleExecutionEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        RuleExecutionEntry(
                            timestampMillis = item.getLong("timestampMillis"),
                            ruleName = item.getString("ruleName"),
                            action = item.getString("action"),
                            reason = item.getString("reason"),
                            outcome = RuleExecutionOutcome.valueOf(item.getString("outcome")),
                            detail = item.optString("detail").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val MAX_ENTRIES = 30
    }
}
