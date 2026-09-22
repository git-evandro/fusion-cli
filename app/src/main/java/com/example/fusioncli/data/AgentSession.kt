package com.example.fusioncli.data

import android.content.Context
import com.example.fusioncli.repository.KiloChatRepository
import com.example.fusioncli.service.ForegroundTasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Application-scoped chat session. It owns the conversations and runs the agent off the
 * ViewModel, so a turn keeps running when the user leaves the chat or puts the app in the
 * background, then reports the result through a notification.
 */
@OptIn(FlowPreview::class)
object AgentSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    private var appContext: Context? = null
    private var settingsRepository: ChatSettingsRepository? = null
    private var chatRepository: KiloChatRepository? = null
    private var conversationStore: ConversationStore? = null

    /** Set by the Application so a finished turn only notifies when the app is not on screen. */
    @Volatile
    var isAppForeground: Boolean = true

    private val _settings = MutableStateFlow(ChatSettings())
    val settings: StateFlow<ChatSettings> = _settings.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> =
        combine(_conversations, _currentConversationId) { conversations, id ->
            conversations.firstOrNull { it.id == id }?.messages ?: emptyList()
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _models = MutableStateFlow<List<KiloModel>>(emptyList())
    val models: StateFlow<List<KiloModel>> = _models.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun init(context: Context) {
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            appContext = app
            settingsRepository = ChatSettingsRepository(app)
            chatRepository = KiloChatRepository(WorkspaceRepository.getInstance(app))
            conversationStore = ConversationStore(app)
            initialized = true
        }

        scope.launch {
            settingsRepository?.settings?.collect { _settings.value = it }
        }
        scope.launch {
            val store = conversationStore ?: return@launch
            val persisted = store.load()
            if (persisted.conversations.isEmpty()) {
                newConversation()
            } else {
                _conversations.value = persisted.conversations
                _currentConversationId.value = persisted.currentId
                    ?.takeIf { id -> persisted.conversations.any { it.id == id } }
                    ?: persisted.conversations.first().id
            }
            // Persist changes only after the stored data has been loaded.
            launch {
                combine(_conversations, _currentConversationId) { conversations, id ->
                    conversations to id
                }
                    .debounce(400)
                    .collect { (conversations, id) -> store.save(conversations, id) }
            }
        }
        loadModels()
    }

    fun newConversation() {
        val conversation = Conversation(id = UUID.randomUUID().toString())
        _conversations.update { it + conversation }
        _currentConversationId.value = conversation.id
    }

    fun selectConversation(id: String) {
        _currentConversationId.value = id
    }

    fun deleteConversation(id: String) {
        _conversations.update { list -> list.filterNot { it.id == id } }
        if (_currentConversationId.value == id) {
            val remaining = _conversations.value
            if (remaining.isEmpty()) newConversation() else _currentConversationId.value = remaining.first().id
        }
    }

    fun selectModel(provider: String, modelId: String) {
        scope.launch { settingsRepository?.updateSelection(provider, modelId) }
    }

    fun saveProvider(provider: String, apiKey: String, baseUrl: String, model: String) {
        _error.value = null
        scope.launch {
            settingsRepository?.updateProvider(provider, apiKey, baseUrl)
            if (model.isNotBlank()) settingsRepository?.updateSelection(provider, model)
        }
    }

    fun loadModels() {
        if (_modelsLoading.value) return
        val repository = chatRepository ?: return
        _modelsLoading.value = true
        scope.launch {
            try {
                _models.value = repository.fetchModels(_settings.value)
            } catch (e: Exception) {
                _error.value = "Não foi possível listar os modelos: ${e.message}"
            } finally {
                _modelsLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _isSending.value) return

        val settings = _settings.value
        if (settings.currentConfig.apiKey.isBlank()) {
            _error.value = "Configure a API key do provedor '${settings.currentProvider}' para conversar."
            return
        }
        if (settings.currentConfig.baseUrl.isBlank()) {
            _error.value = "Configure a Base URL do provedor '${settings.currentProvider}'."
            return
        }

        val repository = chatRepository ?: return
        val conversationId = _currentConversationId.value ?: run {
            newConversation()
            _currentConversationId.value!!
        }

        if (messagesOf(conversationId).none { it.role == ChatRole.USER }) {
            _conversations.update { list ->
                list.map { if (it.id == conversationId) it.copy(title = prompt.take(40)) else it }
            }
        }

        updateMessages(conversationId) {
            it + ChatMessage(ChatRole.USER, prompt) + ChatMessage(ChatRole.ASSISTANT, "", isStreaming = true)
        }
        _isSending.value = true

        val context = appContext
        context?.let { ForegroundTasks.begin(it, "Kilo está trabalhando…") }

        scope.launch {
            var resultTitle = "Kilo terminou"
            var resultText = "A resposta está pronta."
            try {
                val history = messagesOf(conversationId).dropLast(1)
                repository.runAgent(settings, history).collect { event ->
                    when (event) {
                        is AgentEvent.Text -> appendToLast(conversationId, event.delta)
                        is AgentEvent.ToolStarted ->
                            addTool(conversationId, ToolActivity(event.name, event.summary))
                        is AgentEvent.ToolFinished -> completeTool(conversationId, event)
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao falar com o Kilo."
                resultTitle = "Kilo falhou"
                resultText = e.message ?: "Falha ao falar com o Kilo."
                updateMessages(conversationId) { list ->
                    val last = list.lastOrNull()
                    if (last != null && last.role == ChatRole.ASSISTANT &&
                        last.content.isEmpty() && last.tools.isEmpty()
                    ) {
                        list.dropLast(1)
                    } else {
                        list
                    }
                }
            } finally {
                _isSending.value = false
                updateMessages(conversationId) { list ->
                    list.mapIndexed { index, message ->
                        if (index == list.lastIndex) message.copy(isStreaming = false) else message
                    }
                }
                val lastMessage = messagesOf(conversationId).lastOrNull()
                val preview = (lastMessage?.content?.takeIf { it.isNotBlank() }
                    ?: lastMessage?.tools?.lastOrNull()?.summary)
                    ?.replace('\n', ' ')
                    ?.trim()
                    ?.take(140)
                if (!preview.isNullOrBlank()) resultText = preview
                context?.let { ForegroundTasks.end(it, resultTitle, resultText) }
            }
        }
    }

    private fun messagesOf(conversationId: String): List<ChatMessage> =
        _conversations.value.firstOrNull { it.id == conversationId }?.messages ?: emptyList()

    private fun updateMessages(conversationId: String, transform: (List<ChatMessage>) -> List<ChatMessage>) {
        _conversations.update { list ->
            list.map { if (it.id == conversationId) it.copy(messages = transform(it.messages)) else it }
        }
    }

    private fun appendToLast(conversationId: String, text: String) {
        if (text.isEmpty()) return
        updateMessages(conversationId) { list ->
            list.mapIndexed { index, message ->
                if (index == list.lastIndex) {
                    message.copy(content = message.content + text, isStreaming = true)
                } else {
                    message
                }
            }
        }
    }

    private fun addTool(conversationId: String, tool: ToolActivity) {
        updateMessages(conversationId) { list ->
            list.mapIndexed { index, message ->
                if (index == list.lastIndex) {
                    message.copy(tools = message.tools + tool, isStreaming = true)
                } else {
                    message
                }
            }
        }
    }

    private fun completeTool(conversationId: String, event: AgentEvent.ToolFinished) {
        updateMessages(conversationId) { list ->
            list.mapIndexed { index, message ->
                if (index != list.lastIndex) return@mapIndexed message
                val running = message.tools.indexOfLast { it.ok == null }
                val tools = message.tools.toMutableList()
                if (running >= 0) {
                    tools[running] = tools[running].copy(result = event.result, ok = event.ok)
                } else {
                    tools.add(ToolActivity(event.name, event.name, event.result, event.ok))
                }
                message.copy(tools = tools, isStreaming = true)
            }
        }
    }
}
