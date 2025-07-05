package com.example.askai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import android.app.Service
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
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

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

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService - onCreate")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "OverlayService - onStartCommand")
        startForegroundAndMoveLifecycle()
        return START_STICKY
    }

    private fun startForegroundAndMoveLifecycle() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AskAI")
            .setContentText("Definition service running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
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
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isViewAttached(view: View): Boolean {
        return try {
            view.isAttachedToWindow
        } catch (e: Exception) {
            false
        }
    }

    fun showOverlay(definition: String) {
        Log.d(TAG, "OverlayService - showOverlay with definition: $definition")
        showOverlayInternal(definition, "Definition", false, null)
    }

    fun showOverlayWithCopy(text: String, onCopy: (() -> Unit)?, showCopyButton: Boolean) {
        Log.d(TAG, "OverlayService - showOverlayWithCopy with text: $text")
        showOverlayInternal(text, "Revised Text", showCopyButton, onCopy)
    }

    private fun showOverlayInternal(
        content: String, 
        title: String, 
        showCopyButton: Boolean, 
        onCopy: (() -> Unit)?
    ) {
        if (::overlayView.isInitialized && isViewAttached(overlayView)) {
            val composeView = overlayView.findViewById<ComposeView>(R.id.composeView)
            composeView.setContent {
                AskAITheme {
                    EnhancedPopupContent(
                        content = content,
                        title = title,
                        onClose = ::hideOverlay,
                        onCopy = onCopy,
                        showCopyButton = showCopyButton
                    )
                }
            }
            return
        }
        
        try {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_layout, null)
            
            val composeView = overlayView.findViewById<ComposeView>(R.id.composeView)

            overlayView.setViewTreeLifecycleOwner(this)
            overlayView.setViewTreeViewModelStoreOwner(this)
            overlayView.setViewTreeSavedStateRegistryOwner(this)

            composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            composeView.setContent {
                AskAITheme {
                    EnhancedPopupContent(
                        content = content,
                        title = title,
                        onClose = ::hideOverlay,
                        onCopy = onCopy,
                        showCopyButton = showCopyButton
                    )
                }
            }
            
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                
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
            
            windowManager.addView(overlayView, params)
            Log.d(TAG, "OverlayService - View added to window")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}")
            e.printStackTrace()
        }
    }

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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EnhancedPopupContent(
        content: String,
        title: String,
        onClose: () -> Unit,
        onCopy: (() -> Unit)?,
        showCopyButton: Boolean
    ) {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        
        // Calculate responsive dimensions
        val screenWidth = with(density) { configuration.screenWidthDp.dp }
        val screenHeight = with(density) { configuration.screenHeightDp.dp }
        
        // Make popup responsive - wider on larger screens
        val popupWidth = min(screenWidth * 0.9f, 500.dp)
        val maxPopupHeight = min(screenHeight * 0.8f, 600.dp)

        Card(
            modifier = Modifier
                .width(popupWidth)
                .heightIn(max = maxPopupHeight)
                .padding(16.dp),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 8.dp
            ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header with title and controls
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (showCopyButton && onCopy != null) {
                                IconButton(
                                    onClick = onCopy,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                                                         Icon(
                                         imageVector = Icons.Default.FileCopy,
                                         contentDescription = "Copy to clipboard",
                                         modifier = Modifier.size(20.dp)
                                     )
                                }
                            }
                            
                            IconButton(
                                onClick = onClose,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                
                // Scrollable content area with markdown support
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (content.contains("*") || content.contains("#") || content.contains("`") || 
                            content.contains("[") || content.contains("**") || content.contains("_")) {
                            // Content appears to contain markdown, render with markdown
                            Markdown(
                                content = content,
                                colors = markdownColor(
                                    text = MaterialTheme.colorScheme.onSurface,
                                    codeText = MaterialTheme.colorScheme.onSecondaryContainer,
                                    codeBackground = MaterialTheme.colorScheme.secondaryContainer,
                                    linkText = MaterialTheme.colorScheme.primary
                                ),
                                                                 typography = markdownTypography(
                                     h1 = MaterialTheme.typography.headlineMedium,
                                     h2 = MaterialTheme.typography.headlineSmall,
                                     h3 = MaterialTheme.typography.titleLarge,
                                     paragraph = MaterialTheme.typography.bodyLarge,
                                     code = MaterialTheme.typography.bodyMedium.copy(
                                         fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                     )
                                 )
                            )
                        } else {
                            // Plain text content
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4
                            )
                        }
                    }
                }
                
                // Footer with additional actions if needed
                if (showCopyButton && onCopy != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = onCopy,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                                                         Icon(
                                 imageVector = Icons.Default.FileCopy,
                                 contentDescription = null,
                                 modifier = Modifier.size(18.dp)
                             )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy to Clipboard")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        hideOverlay()
        super.onDestroy()
        Log.d(TAG, "OverlayService - onDestroy")
    }
}
