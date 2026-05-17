package com.akrapovic.soundkit.community.automation

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.akrapovic.soundkit.community.domain.rules.Rule
import com.akrapovic.soundkit.community.domain.rules.RuleTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun syncSchedule(rules: List<Rule>) {
        val needsWorker = rules.any {
            it.enabled && (it.trigger is RuleTrigger.Schedule || it.trigger is RuleTrigger.Geofence)
        }
        if (needsWorker) {
            enqueue()
        } else {
            cancel()
        }
    }

    private fun enqueue() {
        val request = PeriodicWorkRequestBuilder<RuleEvaluationWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "soundkit_rule_evaluation"
    }
}
