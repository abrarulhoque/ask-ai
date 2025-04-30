package com.example.askai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
// Removed: import android.app.Service 
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder // Keep Binder for onBind
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
// Removed: import androidx.lifecycle.LifecycleService
import android.app.Service // Use standard Service
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.askai.ui.theme.AskAITheme

// Extend standard Service and implement owner interfaces directly
class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AskAI_Channel"
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    // Manually managed Lifecycle, ViewModelStore, and SavedStateRegistry
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val binder = LocalBinder() // Keep binder for communication if needed

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    // Standard Service onBind
    override fun onBind(intent: Intent?): IBinder {
        // super.onBind(intent) // No super call needed for standard Service onBind unless extending another Binder service
        return binder
    }

    override fun onCreate() {
        super.onCreate() // Call super for standard Service
        Log.d(TAG, "OverlayService - onCreate")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager // Use Context.WINDOW_SERVICE

        // Initialize SavedStateRegistryController and Lifecycle
        savedStateRegistryController.performRestore(null) // Restore state if available
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId) // Call super for standard Service
        Log.d(TAG, "OverlayService - onStartCommand")
        startForegroundAndMoveLifecycle() // Renamed for clarity
        return START_STICKY // Keep using START_STICKY as before
    }

    // Renamed to reflect lifecycle management
    private fun startForegroundAndMoveLifecycle() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // Use IMMUTABLE for API 23+
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AskAI")
            .setContentText("Definition service running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification) // Standard Service startForeground call
        // Dispatch lifecycle events manually
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AskAI Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for AskAI definitions"
            } // Closing brace for apply
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // Use Context.NOTIFICATION_SERVICE
            notificationManager.createNotificationChannel(channel)
        } // Closing brace for if
    }

    // Moved isViewAttached outside showOverlay
    private fun isViewAttached(view: View): Boolean {
        return try {
            view.isAttachedToWindow
        } catch (e: Exception) {
            false
        }
    }

    fun showOverlay(definition: String) {
        Log.d(TAG, "OverlayService - showOverlay with definition: $definition")

        // Use the member function isViewAttached
        if (::overlayView.isInitialized && isViewAttached(overlayView)) {
            // Update existing overlay
            val composeView = overlayView.findViewById<ComposeView>(R.id.composeView) // Assuming R.id.composeView exists in overlay_layout.xml
            // Pass hideOverlay reference
            composeView.setContent {
                AskAITheme {
                    PopupContent(definition = definition, onClose = ::hideOverlay)
                }
            }
            return
        }
        
        // Create and show new overlay
        try {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_layout, null)
            
            // Set up the ComposeView
            val composeView = overlayView.findViewById<ComposeView>(R.id.composeView)

            // Set the essential ViewTree owners on the root view BEFORE setContent on the child ComposeView
            overlayView.setViewTreeLifecycleOwner(this)
            overlayView.setViewTreeViewModelStoreOwner(this)
            overlayView.setViewTreeSavedStateRegistryOwner(this)

            composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Pass hideOverlay reference
            composeView.setContent {
                AskAITheme {
                    PopupContent(definition = definition, onClose = ::hideOverlay)
                }
            }
            
            // Configure the layout parameters
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                
                // Choose the appropriate window type based on API level
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.CENTER
                x = 0
                y = 0
            }
            
            // Make the overlay draggable
            overlayView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        true
                    }
                    else -> false
                }
            }
            
            // Add the view to the window
            windowManager.addView(overlayView, params)
            Log.d(TAG, "OverlayService - View added to window")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}")
            e.printStackTrace()
        }
    }

    // Moved hideOverlay outside showOverlay (it was already outside, just confirming placement)
    fun hideOverlay() {
        if (::overlayView.isInitialized && isViewAttached(overlayView)) {
            try {
                windowManager.removeView(overlayView)
                Log.d(TAG, "OverlayService - View removed from window")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding overlay: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Moved PopupContent outside showOverlay
    @Composable
    fun PopupContent(definition: String, onClose: () -> Unit) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Definition",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Close"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = definition,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
    override fun onDestroy() {
        // Dispatch lifecycle events manually before calling super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        hideOverlay() // Ensure overlay is removed
        super.onDestroy() // Call super for standard Service
        Log.d(TAG, "OverlayService - onDestroy")
    }
}
