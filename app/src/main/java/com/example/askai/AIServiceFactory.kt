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
        val provider = settingsStore.settingsFlow.first().provider
        
        return when (provider) {
            "gemini" -> GeminiService(settingsStore)
            else -> OpenAIService(settingsStore) // Default to OpenAI
        }
    }
} 