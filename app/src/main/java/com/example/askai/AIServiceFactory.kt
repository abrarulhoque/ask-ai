package com.example.askai

import com.example.askai.data.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Interface that defines an AI service that can provide definitions
 */
interface AIService {
    suspend fun getDefinition(text: String): String
}

/**
 * Factory class that returns the appropriate AIService based on provider selection
 */
class AIServiceFactory(private val settingsStore: SettingsStore) {
    
    suspend fun getService(): AIService {
        val settings = settingsStore.settingsFlow.first()
        val provider = settings.provider
        
        return when (provider) {
            "gemini" -> {
                // If using Gemini provider but model isn't a Gemini model, update it
                if (!settings.model.startsWith("gemini")) {
                    settingsStore.saveModel("gemini-2.0-flash")
                }
                GeminiService(settingsStore)
            }
            else -> OpenAIService(settingsStore) // Default to OpenAI
        }
    }
} 