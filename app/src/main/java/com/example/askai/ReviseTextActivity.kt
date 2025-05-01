package com.example.askai

import android.content.ClipData
import android.content.ClipboardManager
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
import com.example.askai.data.DefinitionStore
import com.example.askai.data.SettingsStore
import kotlinx.coroutines.flow.first
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId

class ReviseTextActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ReviseTextActivity"
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    }
    
    private lateinit var selectedText: String
    private var overlayService: OverlayService? = null
    private var serviceBound = false
    private lateinit var definitionStore: DefinitionStore
    private lateinit var settingsStore: SettingsStore
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as OverlayService.LocalBinder
            overlayService = binder.getService()
            serviceBound = true
            processRevision()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            overlayService = null
            serviceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ReviseTextActivity - onCreate")
        
        // Make activity transparent
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Initialize DefinitionStore and SettingsStore
        definitionStore = DefinitionStore(applicationContext)
        settingsStore = SettingsStore(applicationContext)
        
        // Get the incoming intent
        val intent = intent
        
        // Extract the selected text
        selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
        Log.d(TAG, "Selected text: $selectedText")
        
        if (selectedText.isBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Check for overlay permission
        checkOverlayPermission()
    }
    
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Requesting overlay permission")
            // Request permission
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
            Log.d(TAG, "Overlay permission already granted")
            // Start overlay service and bind to it
            startAndBindOverlayService()
        }
    }
    
    private fun startAndBindOverlayService() {
        Log.d(TAG, "Starting and binding overlay service")
        val serviceIntent = Intent(this, OverlayService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }
    
    private fun processRevision() {
        Log.d(TAG, "Processing revision")
        if (!serviceBound || overlayService == null) {
            Toast.makeText(this, "Service not bound, try again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Show a temporary toast indicating revision in progress
        Toast.makeText(this, "Revising text...", Toast.LENGTH_SHORT).show()
        
        // Get revised text using the appropriate AI service
        val serviceFactory = AIServiceFactory(settingsStore)
        lifecycleScope.launch {
            try {
                val aiService = serviceFactory.getService()
                // Use a different method for revision
                val revisedText = getRevisedText(aiService, selectedText)
                Log.d(TAG, "Got revised text: $revisedText")
                
                // Always return result to modify original text 
                val resultIntent = Intent()
                resultIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, revisedText)
                setResult(RESULT_OK, resultIntent)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Error revising text", e)
                Toast.makeText(
                    this@ReviseTextActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    private suspend fun getRevisedText(aiService: AIService, text: String): String {
        // We'll create a custom method to use the revise system prompt
        val settings = settingsStore.settingsFlow.first()
        
        if (aiService is OpenAIService) {
            val openAI = aiService.getOpenAI()
            
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId(settings.model),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = settings.reviseSystemPrompt
                    ),
                    ChatMessage(
                        role = ChatRole.User,
                        content = text
                    )
                )
            )
            
            val completion = openAI.chatCompletion(chatCompletionRequest)
            return completion.choices.first().message.content ?: "No revision available."
        } else if (aiService is GeminiService) {
            // Handle Gemini service
            val model = aiService.getGenerativeModel()
            val prompt = """
                ${settings.reviseSystemPrompt}
                
                ${text}
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            return response.text ?: "No revision available."
        } else {
            return "Unsupported AI service"
        }
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Revised Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Overlay permission granted")
                startAndBindOverlayService()
            } else {
                Log.d(TAG, "Overlay permission denied")
                Toast.makeText(
                    this,
                    "Permission denied. Cannot show overlay.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ReviseTextActivity - onDestroy")
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
    }
} 