package com.akrapovic.soundkit.community.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akrapovic.soundkit.community.domain.rules.Rule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rulesDataStore by preferencesDataStore(name = "soundkit_rules")

interface RulesStore {
    val rules: Flow<List<Rule>>

    suspend fun upsertRule(rule: Rule)
    suspend fun deleteRule(id: String)
    suspend fun setRuleEnabled(id: String, enabled: Boolean)
}

@Singleton
class RulesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : RulesStore {
    private val jsonKey = stringPreferencesKey("rules_json")

    override val rules: Flow<List<Rule>> = context.rulesDataStore.data.map { preferences ->
        RulesCodec.normalize(RulesCodec.decode(preferences[jsonKey]))
    }

    override suspend fun upsertRule(rule: Rule) {
        context.rulesDataStore.edit { preferences ->
            val current = RulesCodec.decode(preferences[jsonKey])
            val without = current.filterNot { it.id == rule.id }
            writeRules(preferences, RulesCodec.normalize(without + rule))
        }
    }

    override suspend fun deleteRule(id: String) {
        context.rulesDataStore.edit { preferences ->
            val updated = RulesCodec.decode(preferences[jsonKey]).filterNot { it.id == id }
            writeRules(preferences, RulesCodec.normalize(updated))
        }
    }

    override suspend fun setRuleEnabled(id: String, enabled: Boolean) {
        context.rulesDataStore.edit { preferences ->
            val updated = RulesCodec.decode(preferences[jsonKey]).map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
            writeRules(preferences, RulesCodec.normalize(updated))
        }
    }

    fun newRuleId(): String = UUID.randomUUID().toString()

    private fun writeRules(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        rules: List<Rule>,
    ) {
        if (rules.isEmpty()) {
            preferences.remove(jsonKey)
        } else {
            preferences[jsonKey] = RulesCodec.encode(rules)
        }
    }
}
