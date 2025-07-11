package com.example.askai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.askai.data.AppSettings
import com.example.askai.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    var openaiApiKey by remember { mutableStateOf("") }
    var geminiApiKey by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var reviseSystemPrompt by remember { mutableStateOf("") }
    // NEW state for summary prompt
    var summarySystemPrompt by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("openai") }
    var notificationEnabled by remember { mutableStateOf(false) }
    var notificationInterval by remember { mutableStateOf(30) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Load current settings
    LaunchedEffect(settingsStore) {
        val settings = settingsStore.settingsFlow.first()
        openaiApiKey = settings.openaiApiKey
        geminiApiKey = settings.geminiApiKey
        systemPrompt = settings.systemPrompt
        reviseSystemPrompt = settings.reviseSystemPrompt
        summarySystemPrompt = settings.summarySystemPrompt
        modelName = settings.model
        provider = settings.provider
        notificationEnabled = settings.notificationEnabled
        notificationInterval = settings.notificationIntervalMinutes
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "AI Provider",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Provider selection
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = provider == "openai",
                    onClick = { 
                        provider = "openai" 
                        if (modelName.startsWith("gemini")) {
                            modelName = "gpt-4o-mini"
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("OpenAI")
                }
                SegmentedButton(
                    selected = provider == "gemini",
                    onClick = { 
                        provider = "gemini" 
                        if (!modelName.startsWith("gemini")) {
                            modelName = "gemini-2.0-flash"
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Gemini")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (provider == "openai") "OpenAI Settings" else "Gemini Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = if (provider == "openai") openaiApiKey else geminiApiKey,
                onValueChange = { 
                    if (provider == "openai") {
                        openaiApiKey = it
                    } else {
                        geminiApiKey = it
                    }
                },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name") },
                placeholder = { 
                    Text(
                        if (provider == "openai") 
                            "E.g. gpt-4o-mini, gpt-4o, gpt-3.5-turbo" 
                        else 
                            "E.g. gemini-1.5-flash, gemini-2.0-flash"
                    ) 
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "System Prompt",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Revise System Prompt",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = reviseSystemPrompt,
                onValueChange = { reviseSystemPrompt = it },
                label = { Text("Revise System Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Summary System Prompt",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = summarySystemPrompt,
                onValueChange = { summarySystemPrompt = it },
                label = { Text("Summary System Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable word reminders",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = notificationEnabled,
                    onCheckedChange = { notificationEnabled = it }
                )
            }
            
            if (notificationEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = notificationInterval.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { interval ->
                            if (interval >= 15) { // Minimum 15 minutes
                                notificationInterval = interval
                            }
                        }
                    },
                    label = { Text("Reminder interval (minutes)") },
                    placeholder = { Text("Minimum 15 minutes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("How often to show word reminders (minimum 15 minutes)")
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    isSaving = true
                    coroutineScope.launch {
                        val settings = AppSettings(
                            openaiApiKey = openaiApiKey,
                            geminiApiKey = geminiApiKey,
                            systemPrompt = systemPrompt,
                            reviseSystemPrompt = reviseSystemPrompt,
                            summarySystemPrompt = summarySystemPrompt,
                            model = modelName,
                            provider = provider,
                            notificationEnabled = notificationEnabled,
                            notificationIntervalMinutes = notificationInterval
                        )
                        settingsStore.saveSettings(settings)
                        isSaving = false
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save Settings")
            }
        }
    }
} 