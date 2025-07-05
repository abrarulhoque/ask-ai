package com.example.askai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.askai.data.Definition
import com.example.askai.data.DefinitionStore
import com.example.askai.data.SettingsStore
import com.example.askai.service.WordReminderManager
import com.example.askai.ui.SettingsScreen
import com.example.askai.ui.theme.AskAITheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.askai.AIServiceFactory
import androidx.compose.material.icons.filled.Add

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    
    private lateinit var definitionStore: DefinitionStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var reminderManager: WordReminderManager
    
    // Notification permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, setup notifications based on current settings
            setupNotificationsFromSettings()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize DataStore and services
        definitionStore = DefinitionStore(applicationContext)
        settingsStore = SettingsStore(applicationContext)
        reminderManager = WordReminderManager(applicationContext)
        
        // Request notification permission if needed and setup notifications
        requestNotificationPermissionIfNeeded()
        
        setContent {
            AskAITheme {
                var showSettings by remember { mutableStateOf(false) }
                var showAddDialog by remember { mutableStateOf(false) }
                
                if (showSettings) {
                    SettingsScreen(
                        settingsStore = settingsStore,
                        onNavigateBack = { showSettings = false }
                    )
                } else {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("AskAI - Your Definitions") },
                                actions = {
                                    IconButton(onClick = { showAddDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add word"
                                        )
                                    }
                                    IconButton(onClick = { showSettings = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    titleContentColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        DefinitionListScreen(
                            modifier = Modifier.padding(innerPadding),
                            definitionStore = definitionStore,
                            onDeleteDefinition = { definition ->
                                lifecycleScope.launch {
                                    definitionStore.deleteDefinition(definition.id)
                                }
                            }
                        )
                    }
                }

                // Add Word Dialog
                if (showAddDialog) {
                    AddWordDialog(
                        onDismiss = { showAddDialog = false },
                        onSave = { word ->
                            showAddDialog = false
                            lifecycleScope.launch {
                                val aiService = AIServiceFactory(settingsStore).getService()
                                val definition = aiService.getDefinition(word)
                                definitionStore.saveDefinition(word, definition)
                            }
                        }
                    )
                }
            }
        }
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    setupNotificationsFromSettings()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale and request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Directly request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Permission not needed for older versions
            setupNotificationsFromSettings()
        }
    }
    
    private fun setupNotificationsFromSettings() {
        lifecycleScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                if (settings.notificationEnabled) {
                    reminderManager.schedulePeriodicNotifications(settings.notificationIntervalMinutes)
                } else {
                    reminderManager.cancelPeriodicNotifications()
                }
            }
        }
    }
}

@Composable
fun DefinitionListScreen(
    modifier: Modifier = Modifier,
    definitionStore: DefinitionStore,
    onDeleteDefinition: (Definition) -> Unit
) {
    val definitionsState = remember { mutableStateOf<List<Definition>>(emptyList()) }
    
    // Collect definitions flow
    LaunchedEffect(definitionStore) {
        definitionStore.allDefinitionsFlow.collectLatest { definitions ->
            definitionsState.value = definitions
        }
    }
    
    val definitions = definitionsState.value
    
    if (definitions.isEmpty()) {
        EmptyListMessage(modifier)
    } else {
        DefinitionList(
            definitions = definitions,
            onDeleteDefinition = onDeleteDefinition,
            modifier = modifier
        )
    }
}

@Composable
fun EmptyListMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "No saved definitions yet",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select text in any app and use AskAI to save definitions here",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun DefinitionList(
    definitions: List<Definition>,
    onDeleteDefinition: (Definition) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(definitions) { definition ->
            DefinitionCard(
                definition = definition,
                onDeleteClick = { onDeleteDefinition(definition) }
            )
        }
    }
}

@Composable
fun DefinitionCard(
    definition: Definition,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
    val date = Date(definition.timestamp)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
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
                    text = definition.query,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete definition"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = dateFormat.format(date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = definition.explanation,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
            
            if (!expanded && definition.explanation.length > 150) {
                TextButton(
                    onClick = { expanded = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Read more")
                }
            }
        }
    }
}

// New composable for adding a word
@Composable
fun AddWordDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Word") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Word") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) {
                        onSave(trimmed)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}