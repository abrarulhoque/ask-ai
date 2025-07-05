package com.example.askai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

// Extension property for Context to create Settings DataStore
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Data class for app settings
@Serializable
data class AppSettings(
    val openaiApiKey: String = "",
    val geminiApiKey: String = "",
    val systemPrompt: String = "You are a friendly assistant helping non-native English speakers understand English vocabulary and expressions. Your explanations use simple, clear language.\n" +
            "For Single Words:\n" +
            "\n" +
            "Provide a clear, simple definition\n" +
            "Include 1 example sentence showing common usage\n" +
            "List 3-5 synonyms (similar words) without explanations\n" +
            "List 1-3 antonyms (opposite words) without explanations\n" +
            "If the word has multiple meanings, briefly explain the most common ones\n" +
            "\n" +
            "For Sentences or Phrases:\n" +
            "\n" +
            "Rephrase the sentence using simpler vocabulary\n" +
            "Explain the overall meaning\n" +
            "Identify any idioms or expressions that might confuse learners\n" +
            "\n" +
            "Always use plain text format (no markdown, bullet points, or special formatting).",
    val reviseSystemPrompt: String = "You are a professional writing assistant. Your task is to revise and improve the text provided by the user.\n" +
            "Follow these guidelines:\n" +
            "\n" +
            "1. Improve grammar, spelling, and punctuation\n" +
            "2. Enhance clarity and readability\n" +
            "3. Make the text more concise where appropriate\n" +
            "4. Maintain the original meaning and tone\n" +
            "5. Format your response as the revised text only without any explanation\n" +
            "\n" +
            "Do not add introductory phrases like 'Here's the revised text:'. Just provide the improved version.",
    // NEW: default prompt for summarization
    val summarySystemPrompt: String = "You are a helpful assistant. Provide a concise summary of the provided text in simple, clear language. Use plain text without bullet points or special formatting.",
    val model: String = "gpt-4o-mini",
    val provider: String = "openai", // Default to OpenAI
    val notificationEnabled: Boolean = false,
    val notificationIntervalMinutes: Int = 30
) {
    // Get the appropriate API key based on current provider
    val apiKey: String
        get() = when (provider) {
            "gemini" -> geminiApiKey
            else -> openaiApiKey
        }
}

// Class that manages app settings using DataStore
class SettingsStore(private val context: Context) {
    
    // Keys for storing settings
    private val openaiApiKeyKey = stringPreferencesKey("openai_api_key")
    private val geminiApiKeyKey = stringPreferencesKey("gemini_api_key")
    private val systemPromptKey = stringPreferencesKey("system_prompt")
    private val reviseSystemPromptKey = stringPreferencesKey("revise_system_prompt")
    // NEW key for summary prompt
    private val summarySystemPromptKey = stringPreferencesKey("summary_system_prompt")
    private val modelKey = stringPreferencesKey("model")
    private val providerKey = stringPreferencesKey("provider")
    private val notificationEnabledKey = booleanPreferencesKey("notification_enabled")
    private val notificationIntervalKey = intPreferencesKey("notification_interval_minutes")

    // Legacy key for backward compatibility
    private val legacyApiKeyKey = stringPreferencesKey("api_key")

    // Get stored API key based on current provider
    val apiKeyFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            val provider = preferences[providerKey] ?: AppSettings().provider
            when (provider) {
                "gemini" -> preferences[geminiApiKeyKey] ?: preferences[legacyApiKeyKey] ?: ""
                else -> preferences[openaiApiKeyKey] ?: preferences[legacyApiKeyKey] ?: ""
            }
        }

    // Get OpenAI API key
    val openaiApiKeyFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[openaiApiKeyKey] ?: preferences[legacyApiKeyKey] ?: ""
        }
        
    // Get Gemini API key
    val geminiApiKeyFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[geminiApiKeyKey] ?: ""
        }

    // Get stored system prompt
    val systemPromptFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[systemPromptKey] ?: AppSettings().systemPrompt
        }

    // Get stored revise system prompt
    val reviseSystemPromptFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[reviseSystemPromptKey] ?: AppSettings().reviseSystemPrompt
        }

    // Get stored summary system prompt
    val summarySystemPromptFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[summarySystemPromptKey] ?: AppSettings().summarySystemPrompt
        }

    // Get stored model
    val modelFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            val model = preferences[modelKey] ?: AppSettings().model
            val provider = preferences[providerKey] ?: AppSettings().provider
            
            // Return provider-appropriate model
            when {
                provider == "gemini" && !model.startsWith("gemini") -> "gemini-2.0-flash"
                provider == "openai" && model.startsWith("gemini") -> "gpt-4o-mini"
                else -> model
            }
        }

    // Get all settings as a Flow
    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data
        .map { preferences ->
            // Handle migration from legacy single API key
            val legacyApiKey = preferences[legacyApiKeyKey] ?: ""
            
            // Use openai/gemini-specific keys if they exist, otherwise fall back to legacy key
            val openaiApiKey = preferences[openaiApiKeyKey] ?: legacyApiKey
            val geminiApiKey = preferences[geminiApiKeyKey] ?: legacyApiKey
            
            AppSettings(
                openaiApiKey = openaiApiKey,
                geminiApiKey = geminiApiKey,
                systemPrompt = preferences[systemPromptKey] ?: AppSettings().systemPrompt,
                reviseSystemPrompt = preferences[reviseSystemPromptKey] ?: AppSettings().reviseSystemPrompt,
                summarySystemPrompt = preferences[summarySystemPromptKey] ?: AppSettings().summarySystemPrompt,
                model = preferences[modelKey] ?: AppSettings().model,
                provider = preferences[providerKey] ?: AppSettings().provider,
                notificationEnabled = preferences[notificationEnabledKey] ?: AppSettings().notificationEnabled,
                notificationIntervalMinutes = preferences[notificationIntervalKey] ?: AppSettings().notificationIntervalMinutes
            )
        }
    
    // Save OpenAI API key
    suspend fun saveOpenAIApiKey(apiKey: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[openaiApiKeyKey] = apiKey
        }
    }
    
    // Save Gemini API key
    suspend fun saveGeminiApiKey(apiKey: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[geminiApiKeyKey] = apiKey
        }
    }
    
    // Save system prompt
    suspend fun saveSystemPrompt(systemPrompt: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[systemPromptKey] = systemPrompt
        }
    }
    
    // Save revise system prompt
    suspend fun saveReviseSystemPrompt(reviseSystemPrompt: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[reviseSystemPromptKey] = reviseSystemPrompt
        }
    }
    
    // Save summary system prompt
    suspend fun saveSummarySystemPrompt(summarySystemPrompt: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[summarySystemPromptKey] = summarySystemPrompt
        }
    }
    
    // Save model
    suspend fun saveModel(model: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[modelKey] = model
        }
    }
    
    // Save provider
    suspend fun saveProvider(provider: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[providerKey] = provider
        }
    }
    
    // Save notification enabled setting
    suspend fun saveNotificationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[notificationEnabledKey] = enabled
        }
    }
    
    // Save notification interval setting
    suspend fun saveNotificationInterval(intervalMinutes: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[notificationIntervalKey] = intervalMinutes
        }
    }
    
    // Save all settings
    suspend fun saveSettings(settings: AppSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences[openaiApiKeyKey] = settings.openaiApiKey
            preferences[geminiApiKeyKey] = settings.geminiApiKey
            preferences[systemPromptKey] = settings.systemPrompt
            preferences[reviseSystemPromptKey] = settings.reviseSystemPrompt
            preferences[summarySystemPromptKey] = settings.summarySystemPrompt
            preferences[modelKey] = settings.model
            preferences[providerKey] = settings.provider
            preferences[notificationEnabledKey] = settings.notificationEnabled
            preferences[notificationIntervalKey] = settings.notificationIntervalMinutes
        }
    }
} 