package com.example.askai

import android.util.Log
import com.example.askai.data.SettingsStore
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import kotlinx.coroutines.flow.first

class GeminiService(private val settingsStore: SettingsStore) : AIService {
    
    private suspend fun getGenerativeModel(): GenerativeModel {
        val settings = settingsStore.settingsFlow.first()
        val apiKey = settingsStore.geminiApiKeyFlow.first()
        
        // Ensure we're using a valid Gemini model
        val modelName = if (settings.model.startsWith("gemini")) {
            settings.model
        } else {
            "gemini-2.0-flash" // Default to Gemini 2.0 Flash if not set correctly
        }
        
        return GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )
    }
    
    override suspend fun getDefinition(text: String): String {
        try {
            val settings = settingsStore.settingsFlow.first()
            
            if (settings.geminiApiKey.isEmpty()) {
                return "Error: Gemini API key not configured. Please go to Settings and enter your API key."
            }
            
            val model = getGenerativeModel()
            
            val prompt = """
                ${settings.systemPrompt}
                
                provided text by user: "${text}"
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            return response.text ?: "No definition available."
            
        } catch (e: Exception) {
            Log.e("GeminiService", "Error: ${e.message}")
            return "Error getting definition: ${e.message}"
        }
    }
} 