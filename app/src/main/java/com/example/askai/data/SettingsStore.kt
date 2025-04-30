package com.example.askai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val model: String = "gpt-4o-mini",
    val provider: String = "openai" // Default to OpenAI
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
    private val modelKey = stringPreferencesKey("model")
    private val providerKey = stringPreferencesKey("provider")

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
                model = preferences[modelKey] ?: AppSettings().model,
                provider = preferences[providerKey] ?: AppSettings().provider
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
    
    // Save all settings
    suspend fun saveSettings(settings: AppSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences[openaiApiKeyKey] = settings.openaiApiKey
            preferences[geminiApiKeyKey] = settings.geminiApiKey
            preferences[systemPromptKey] = settings.systemPrompt
            preferences[modelKey] = settings.model
            preferences[providerKey] = settings.provider
        }
    }
} 