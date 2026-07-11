package com.akrapovic.soundkit.community

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.akrapovic.soundkit.community.domain.VoiceValveAction
import com.akrapovic.soundkit.community.domain.VoiceValveActionResult
import com.akrapovic.soundkit.community.domain.VoiceValveActionRouter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Internal shortcut fulfillment only. It accepts no device identifier or command extras:
 * [VoiceValveActionRouter] resolves the saved default receiver and performs all admission.
 */
@AndroidEntryPoint
class AssistantActionActivity : ComponentActivity() {
    @Inject lateinit var router: VoiceValveActionRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = when (intent.action) {
            ACTION_OPEN -> VoiceValveAction.Open
            ACTION_CLOSE -> VoiceValveAction.Close
            ACTION_STATUS -> VoiceValveAction.Status
            else -> {
                finish()
                return
            }
        }
        lifecycleScope.launch {
            val result = router.execute(action)
            Toast.makeText(
                this@AssistantActionActivity,
                when (result) {
                    is VoiceValveActionResult.Success -> result.message
                    is VoiceValveActionResult.Failure -> result.message
                },
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    companion object {
        const val ACTION_OPEN = "com.akrapovic.soundkit.community.action.VOICE_OPEN"
        const val ACTION_CLOSE = "com.akrapovic.soundkit.community.action.VOICE_CLOSE"
        const val ACTION_STATUS = "com.akrapovic.soundkit.community.action.VOICE_STATUS"
    }
}
