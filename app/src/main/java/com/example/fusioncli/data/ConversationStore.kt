package com.example.fusioncli.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything persisted about the chat: the conversations and which one is open. */
data class PersistedChat(
    val conversations: List<Conversation>,
    val currentId: String?
)

/**
 * Persists conversations as JSON in app-private storage so they survive app restarts.
 */
class ConversationStore(context: Context) {

    private val file = File(context.filesDir, "conversations.json")

    suspend fun load(): PersistedChat = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext PersistedChat(emptyList(), null)
        try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("conversations") ?: JSONArray()
            val conversations = (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                if (id.isBlank()) return@mapNotNull null
                val messagesArray = obj.optJSONArray("messages") ?: JSONArray()
                val messages = (0 until messagesArray.length()).mapNotNull { messageIndex ->
                    val message = messagesArray.optJSONObject(messageIndex) ?: return@mapNotNull null
                    val role = if (message.optString("role") == "user") ChatRole.USER else ChatRole.ASSISTANT
                    val toolsArray = message.optJSONArray("tools") ?: JSONArray()
                    val storedTools = (0 until toolsArray.length()).mapNotNull { toolIndex ->
                        val tool = toolsArray.optJSONObject(toolIndex) ?: return@mapNotNull null
                        ToolActivity(
                            name = tool.optString("name"),
                            summary = tool.optString("summary"),
                            result = tool.optString("result"),
                            ok = if (tool.has("ok") && !tool.isNull("ok")) tool.optBoolean("ok") else null
                        )
                    }
                    val rawContent = message.optString("content")
                    // Older versions wrote the tool activity into the message text ("▶ ..." / "✓ ...").
                    // Convert it to proper tool rows so old conversations render cleanly too.
                    val (content, legacyTools) = if (storedTools.isEmpty()) {
                        splitLegacyTools(rawContent)
                    } else {
                        rawContent to emptyList()
                    }
                    ChatMessage(
                        role = role,
                        content = content,
                        isStreaming = false,
                        tools = storedTools.ifEmpty { legacyTools }
                    )
                }
                Conversation(
                    id = id,
                    title = obj.optString("title").ifBlank { "Nova conversa" },
                    messages = messages
                )
            }
            PersistedChat(conversations, root.optString("currentId").ifBlank { null })
        } catch (e: Exception) {
            PersistedChat(emptyList(), null)
        }
    }

    /**
     * Splits the legacy text markers out of a message: "▶ description" starts a tool,
     * "✓ result" / "✗ result" complete it. Everything else stays as the message text.
     */
    private fun splitLegacyTools(content: String): Pair<String, List<ToolActivity>> {
        if (!content.contains('▶') && !content.contains('✓') && !content.contains('✗')) {
            return content to emptyList()
        }
        val text = StringBuilder()
        val tools = mutableListOf<ToolActivity>()
        content.split('\n').forEach { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("▶ ") -> {
                    val summary = trimmed.removePrefix("▶ ").trim()
                    tools.add(ToolActivity(inferToolName(summary), summary))
                }
                trimmed.startsWith("✓ ") -> completeLast(tools, true, trimmed.removePrefix("✓ ").trim())
                trimmed.startsWith("✗ ") -> completeLast(tools, false, trimmed.removePrefix("✗ ").trim())
                else -> text.append(line).append('\n')
            }
        }
        return text.toString().trim() to tools
    }

    private fun completeLast(tools: MutableList<ToolActivity>, ok: Boolean, result: String) {
        val index = tools.indexOfLast { it.ok == null }
        if (index >= 0) {
            tools[index] = tools[index].copy(result = result, ok = ok)
        } else {
            tools.add(ToolActivity(inferToolName(result), result, result = "", ok = ok))
        }
    }

    private fun inferToolName(summary: String): String = when {
        summary.startsWith("Listando") -> "list_files"
        summary.startsWith("Lendo") -> "read_file"
        summary.startsWith("Escrevendo") -> "write_file"
        summary.startsWith("Editando") -> "edit_file"
        summary.startsWith("Movendo") -> "move_file"
        summary.startsWith("Excluindo") -> "delete_file"
        summary.startsWith("Criando pasta") -> "create_dir"
        summary.startsWith("Executando") -> "run_command"
        else -> ""
    }

    suspend fun save(conversations: List<Conversation>, currentId: String?) =
        withContext(Dispatchers.IO) {
            try {
                val root = JSONObject().put("currentId", currentId ?: "")
                val array = JSONArray()
                conversations.forEach { conversation ->
                    val messages = JSONArray()
                    conversation.messages.forEach { message ->
                        val tools = JSONArray()
                        message.tools.forEach { tool ->
                            tools.put(
                                JSONObject()
                                    .put("name", tool.name)
                                    .put("summary", tool.summary)
                                    .put("result", tool.result)
                                    .put("ok", tool.ok ?: JSONObject.NULL)
                            )
                        }
                        messages.put(
                            JSONObject()
                                .put("role", if (message.role == ChatRole.USER) "user" else "assistant")
                                .put("content", message.content)
                                .put("tools", tools)
                        )
                    }
                    array.put(
                        JSONObject()
                            .put("id", conversation.id)
                            .put("title", conversation.title)
                            .put("messages", messages)
                    )
                }
                root.put("conversations", array)
                file.parentFile?.mkdirs()
                file.writeText(root.toString())
            } catch (_: Exception) {
            }
        }
}
