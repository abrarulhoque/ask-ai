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
    val apiKey: String = "",
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
    val model: String = "gpt-4o-mini"
)

// Class that manages app settings using DataStore
class SettingsStore(private val context: Context) {
    
    // Keys for storing settings
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val systemPromptKey = stringPreferencesKey("system_prompt")
    private val modelKey = stringPreferencesKey("model")

    // Get stored API key
    val apiKeyFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[apiKeyKey] ?: ""
        }

    // Get stored system prompt
    val systemPromptFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[systemPromptKey] ?: AppSettings().systemPrompt
        }

    // Get stored model
    val modelFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[modelKey] ?: AppSettings().model
        }

    // Get all settings as a Flow
    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data
        .map { preferences ->
            AppSettings(
                apiKey = preferences[apiKeyKey] ?: "",
                systemPrompt = preferences[systemPromptKey] ?: AppSettings().systemPrompt,
                model = preferences[modelKey] ?: AppSettings().model
            )
        }
    
    // Save API key
    suspend fun saveApiKey(apiKey: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[apiKeyKey] = apiKey
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
    
    // Save all settings
    suspend fun saveSettings(settings: AppSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences[apiKeyKey] = settings.apiKey
            preferences[systemPromptKey] = settings.systemPrompt
            preferences[modelKey] = settings.model
        }
    }
} 