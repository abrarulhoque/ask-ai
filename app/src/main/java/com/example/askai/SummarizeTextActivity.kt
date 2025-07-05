package com.example.askai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.example.askai.data.SettingsStore
import kotlinx.coroutines.flow.first
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId

/**
 * Activity that handles the "Summarize" process-text action.
 * It generates a concise summary of the selected text and shows it in an overlay.
 * Nothing is stored in the local database.
 */
class SummarizeTextActivity : ComponentActivity() {
    companion object {
        private const val TAG = "SummarizeTextActivity"
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    }

    private lateinit var selectedText: String
    private var overlayService: OverlayService? = null
    private var serviceBound = false
    private lateinit var settingsStore: SettingsStore

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as OverlayService.LocalBinder
            overlayService = binder.getService()
            serviceBound = true
            processSummary()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            overlayService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // Make activity transparent
        window.setBackgroundDrawableResource(android.R.color.transparent)

        settingsStore = SettingsStore(applicationContext)

        selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
        if (selectedText.isBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            Toast.makeText(
                this,
                "Please grant permission to display over other apps",
                Toast.LENGTH_LONG
            ).show()
        } else {
            startAndBindOverlayService()
        }
    }

    private fun startAndBindOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun processSummary() {
        if (!serviceBound || overlayService == null) {
            Toast.makeText(this, "Service not bound, try again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        overlayService?.showOverlay("Summarizing…")

        val serviceFactory = AIServiceFactory(settingsStore)
        lifecycleScope.launch {
            try {
                val aiService = serviceFactory.getService()
                val summary = getSummaryText(aiService, selectedText)
                overlayService?.showOverlay(summary)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Error summarizing text", e)
                overlayService?.showOverlay("Error: ${e.message}")
            }
        }
    }

    /**
     * Generate summary using summarySystemPrompt from settings.
     */
    private suspend fun getSummaryText(aiService: AIService, text: String): String {
        val settings = settingsStore.settingsFlow.first()

        return when (aiService) {
            is OpenAIService -> {
                val openAI = aiService.getOpenAI()
                val request = ChatCompletionRequest(
                    model = ModelId(settings.model),
                    messages = listOf(
                        ChatMessage(ChatRole.System, settings.summarySystemPrompt),
                        ChatMessage(ChatRole.User, text)
                    )
                )
                val completion = openAI.chatCompletion(request)
                completion.choices.first().message.content ?: "No summary available."
            }
            is GeminiService -> {
                val model = aiService.getGenerativeModel()
                val prompt = """
                    ${settings.summarySystemPrompt}

                    ${text}
                """.trimIndent()
                val response = model.generateContent(prompt)
                response.text ?: "No summary available."
            }
            else -> "Unsupported AI service"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startAndBindOverlayService()
            } else {
                Toast.makeText(this, "Permission denied. Cannot show overlay.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
    }
} 