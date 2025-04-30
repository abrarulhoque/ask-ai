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
import com.example.askai.data.DefinitionStore
import com.example.askai.data.SettingsStore

class ProcessTextActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ProcessTextActivity"
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
            processDefinition()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            overlayService = null
            serviceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ProcessTextActivity - onCreate")
        
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
    
    private fun processDefinition() {
        Log.d(TAG, "Processing definition")
        if (!serviceBound || overlayService == null) {
            Toast.makeText(this, "Service not bound, try again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Show a temporary message while getting the definition
        overlayService?.showOverlay("Getting definition...")
        
        // Get definition using the appropriate AI service
        val serviceFactory = AIServiceFactory(settingsStore)
        lifecycleScope.launch {
            try {
                val aiService = serviceFactory.getService()
                val definition = aiService.getDefinition(selectedText)
                Log.d(TAG, "Got definition: $definition")
                
                // Save to DataStore
                definitionStore.saveDefinition(selectedText, definition)
                Log.d(TAG, "Definition saved to DataStore")
                
                // Show in overlay
                overlayService?.showOverlay(definition)
                finish() // Activity can finish, overlay will remain
            } catch (e: Exception) {
                Log.e(TAG, "Error getting definition", e)
                overlayService?.showOverlay("Error: ${e.message}")
            }
        }
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
                    "Permission denied. Cannot show definition overlay.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ProcessTextActivity - onDestroy")
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
    }
} 