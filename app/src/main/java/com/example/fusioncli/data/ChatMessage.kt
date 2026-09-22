package com.example.fusioncli.data

enum class ChatRole {
    USER,
    ASSISTANT
}

/**
 * One tool call made by the agent, kept so the chat can render it with an icon instead of a raw
 * log line. [ok] is null while the tool is still running.
 */
data class ToolActivity(
    val name: String,
    val summary: String,
    val result: String = "",
    val ok: Boolean? = null
)

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false,
    val tools: List<ToolActivity> = emptyList()
)
