package com.example.askai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.example.askai.data.SettingsStore
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.flow.first

class OpenAIService(private val settingsStore: SettingsStore) : AIService {
    
    private suspend fun getOpenAI(): OpenAI {
        val apiKey = settingsStore.openaiApiKeyFlow.first()
        
        return OpenAI(
            config = OpenAIConfig(
                token = apiKey,
                engine = Android.create()
            )
        )
    }
    
    override suspend fun getDefinition(text: String): String {
        try {
            val settings = settingsStore.settingsFlow.first()
            
            if (settings.openaiApiKey.isEmpty()) {
                return "Error: OpenAI API key not configured. Please go to Settings and enter your API key."
            }
            
            val openAI = getOpenAI()
            
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId(settings.model),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = settings.systemPrompt
                    ),
                    ChatMessage(
                        role = ChatRole.User,
                        content = "provided text by user: \"$text\""
                    )
                )
            )
            
            val completion = openAI.chatCompletion(chatCompletionRequest)
            return completion.choices.first().message.content ?: "No definition available."
        } catch (e: Exception) {
            return "Error getting definition: ${e.message}"
        }
    }
} 