package com.example.fusioncli.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.fusioncli.data.AgentSession
import com.example.fusioncli.data.ChatMessage
import com.example.fusioncli.data.ChatSettings
import com.example.fusioncli.data.Conversation
import com.example.fusioncli.data.KiloModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin facade over the application-scoped [AgentSession]. The agent runs in that session, not
 * here, so a turn keeps going when the user leaves the chat.
 */
class ChatViewModel : ViewModel() {

    val settings: StateFlow<ChatSettings> = AgentSession.settings
    val conversations: StateFlow<List<Conversation>> = AgentSession.conversations
    val currentConversationId: StateFlow<String?> = AgentSession.currentConversationId
    val messages: StateFlow<List<ChatMessage>> = AgentSession.messages
    val models: StateFlow<List<KiloModel>> = AgentSession.models
    val modelsLoading: StateFlow<Boolean> = AgentSession.modelsLoading
    val isSending: StateFlow<Boolean> = AgentSession.isSending
    val error: StateFlow<String?> = AgentSession.error

    fun newConversation() = AgentSession.newConversation()

    fun selectConversation(id: String) = AgentSession.selectConversation(id)

    fun deleteConversation(id: String) = AgentSession.deleteConversation(id)

    fun selectModel(provider: String, modelId: String) = AgentSession.selectModel(provider, modelId)

    fun saveProvider(provider: String, apiKey: String, baseUrl: String, model: String) =
        AgentSession.saveProvider(provider, apiKey, baseUrl, model)

    fun loadModels() = AgentSession.loadModels()

    fun clearError() = AgentSession.clearError()

    fun send(text: String) = AgentSession.send(text)

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            AgentSession.init(context.applicationContext)
            return ChatViewModel() as T
        }
    }
}
