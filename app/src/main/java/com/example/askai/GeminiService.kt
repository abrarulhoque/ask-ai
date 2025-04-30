package com.example.askai

import android.util.Log
import com.example.askai.data.SettingsStore
import com.google.android.libraries.ai.TextGenerationRequest
import com.google.android.libraries.ai.GeminiClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GeminiService(private val settingsStore: SettingsStore) : AIService {
    
    private suspend fun getGeminiClient(): GeminiClient {
        val apiKey = settingsStore.apiKeyFlow.first()
        return GeminiClient.create(apiKey = apiKey)
    }
    
    suspend fun getDefinition(text: String): String {
        try {
            val settings = settingsStore.settingsFlow.first()
            
            if (settings.apiKey.isEmpty()) {
                return "Error: Gemini API key not configured. Please go to Settings and enter your API key."
            }
            
            val client = getGeminiClient()
            
            val request = TextGenerationRequest.newBuilder()
                .setModel(settings.model) // e.g., "gemini-1.5-flash" or "gemini-2.0-flash"
                .setPrompt(
                    """
                    ${settings.systemPrompt}
                    
                    provided text by user: "${text}"
                    """
                )
                .build()
            
            // Convert callback to coroutine
            return suspendCancellableCoroutine { continuation ->
                client.generateText(request)
                    .addOnSuccessListener { response ->
                        val result = response.text
                        continuation.resume(result)
                    }
                    .addOnFailureListener { e ->
                        Log.e("GeminiService", "Error: ${e.message}")
                        continuation.resume("Error getting definition: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            return "Error getting definition: ${e.message}"
        }
    }
} 