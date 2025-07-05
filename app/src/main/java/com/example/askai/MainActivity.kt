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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.ui.graphics.graphicsLayer
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

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
                    MainScreenContent(
                        definitionStore = definitionStore,
                        onSettingsClick = { showSettings = true },
                        onAddWordClick = { showAddDialog = true }
                    )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MainScreenContent(
    definitionStore: DefinitionStore,
    onSettingsClick: () -> Unit,
    onAddWordClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    
    fun refresh() = refreshScope.launch {
        isRefreshing = true
        delay(1000) // Simulate refresh delay
        isRefreshing = false
    }
    
    val pullRefreshState = rememberPullRefreshState(isRefreshing, ::refresh)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    AnimatedVisibility(
                        visible = !isSearchActive,
                        enter = fadeIn(spring(Spring.DampingRatioMediumBouncy)),
                        exit = fadeOut(spring(Spring.DampingRatioMediumBouncy))
                    ) {
                        Text(
                            text = "AskAI",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = slideInHorizontally(spring(Spring.DampingRatioMediumBouncy)) { it },
                        exit = slideOutHorizontally(spring(Spring.DampingRatioMediumBouncy)) { it }
                    ) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClose = {
                                isSearchActive = false
                                searchQuery = ""
                            }
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(
                            onClick = { isSearchActive = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search definitions",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        IconButton(onClick = onAddWordClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add word",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .pullRefresh(pullRefreshState)
        ) {
            DefinitionListScreen(
                definitionStore = definitionStore,
                searchQuery = searchQuery,
                onDeleteDefinition = { definition ->
                    refreshScope.launch {
                        definitionStore.deleteDefinition(definition.id)
                    }
                }
            )
            
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { 
            Text(
                text = "Search definitions...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun DefinitionListScreen(
    definitionStore: DefinitionStore,
    searchQuery: String,
    onDeleteDefinition: (Definition) -> Unit
) {
    val definitionsState = remember { mutableStateOf<List<Definition>>(emptyList()) }
    
    // Collect definitions flow
    LaunchedEffect(definitionStore) {
        definitionStore.allDefinitionsFlow.collectLatest { definitions ->
            definitionsState.value = definitions
        }
    }
    
    val allDefinitions = definitionsState.value
    
    // Filter definitions based on search query
    val filteredDefinitions = remember(allDefinitions, searchQuery) {
        if (searchQuery.isEmpty()) {
            allDefinitions
        } else {
            allDefinitions.filter { definition ->
                definition.query.contains(searchQuery, ignoreCase = true) ||
                definition.explanation.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    if (allDefinitions.isEmpty()) {
        EnhancedEmptyListMessage()
    } else if (filteredDefinitions.isEmpty() && searchQuery.isNotEmpty()) {
        SearchEmptyState(searchQuery = searchQuery)
    } else {
        EnhancedDefinitionList(
            definitions = filteredDefinitions,
            onDeleteDefinition = onDeleteDefinition
        )
    }
}

@Composable
fun EnhancedEmptyListMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Empty state icon
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Start Your Learning Journey",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Select text in any app and use AskAI to save definitions here, or tap the + button to add a word manually.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
            )
        }
    }
}

@Composable
fun SearchEmptyState(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No results found",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "No definitions match \"$searchQuery\"",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedDefinitionList(
    definitions: List<Definition>,
    onDeleteDefinition: (Definition) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = definitions,
            key = { it.id }
        ) { definition ->
            EnhancedDefinitionCard(
                definition = definition,
                onDeleteClick = { onDeleteDefinition(definition) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedDefinitionCard(
    definition: Definition,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
    val date = Date(definition.timestamp)
    val haptic = LocalHapticFeedback.current

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                expanded = !expanded
            }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = definition.query,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )

                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDeleteClick()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete definition",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = dateFormat.format(date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = definition.explanation,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4
            )

            if (!expanded && definition.explanation.length > 150) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Read more",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// Enhanced Add Word Dialog with better styling
@Composable
fun AddWordDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Add New Word",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column {
                Text(
                    text = "Enter a word or phrase to get its definition",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Word or phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) {
                        onSave(trimmed)
                    }
                }
            ) {
                Text("Add Definition")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}