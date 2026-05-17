package com.akrapovic.soundkit.community.automation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.akrapovic.soundkit.community.domain.rules.RuleExecutionEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RuleEvaluationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ruleExecutionEngine: RuleExecutionEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ruleExecutionEngine.evaluateNow(triggerReason = "schedule")
        return Result.success()
    }
}
