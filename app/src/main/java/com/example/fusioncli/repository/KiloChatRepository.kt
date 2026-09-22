package com.example.fusioncli.repository

import com.example.fusioncli.data.AgentEvent
import com.example.fusioncli.data.AgentTools
import com.example.fusioncli.data.ChatMessage
import com.example.fusioncli.data.ChatRole
import com.example.fusioncli.data.ChatSettings
import com.example.fusioncli.data.KiloModel
import com.example.fusioncli.data.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the Kilo AI Gateway (OpenAI-compatible) and drives an autonomous coding agent.
 *
 * The model is given function tools that act on a real [WorkspaceRepository]; this class runs the
 * tool-calling loop: stream the assistant turn, execute any requested tools, feed the results
 * back, and repeat until the model answers without tools.
 *
 * @see <a href="https://kilo.ai/docs/gateway/api-reference">API reference</a>
 */
class KiloChatRepository(private val workspace: WorkspaceRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming responses stay open
        .build()

    /**
     * Runs the agent for one user turn, emitting text deltas and tool activity as they happen.
     */
    fun runAgent(settings: ChatSettings, history: List<ChatMessage>): Flow<AgentEvent> = flow {
        val config = settings.currentConfig
        val messages = buildMessages(settings, history)

        var toolsEnabled = true
        var round = 0
        while (round < MAX_ROUNDS) {
            round++

            val content = StringBuilder()
            val toolCalls = LinkedHashMap<Int, ToolCallAccumulator>()

            var streamDone = false
            while (!streamDone) {
                val request = buildRequest(config.baseUrl, config.apiKey, settings.requestModelId(), messages, toolsEnabled)
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty().take(300)
                    response.close()
                    if (toolsEnabled && looksLikeToolsUnsupported(response.code, text)) {
                        // Some models/endpoints reject function calling; fall back to plain chat.
                        toolsEnabled = false
                        continue
                    }
                    throw IOException("HTTP ${response.code}: $text")
                }

                val body = response.body ?: throw IOException("Resposta vazia do gateway.")
                response.use {
                    val source = body.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.substring(5).trim()
                        if (data == "[DONE]") break

                        val choice = JSONObject(data).optJSONArray("choices")?.optJSONObject(0) ?: continue
                        val delta = choice.optJSONObject("delta") ?: continue

                        // org.json's optString turns a JSON null into the literal "null".
                        val textDelta = if (!delta.isNull("content")) delta.optString("content", "") else ""
                        if (textDelta.isNotEmpty() && textDelta != "null") {
                            content.append(textDelta)
                            emit(AgentEvent.Text(textDelta))
                        }

                        accumulateToolCalls(delta.optJSONArray("tool_calls"), toolCalls)
                    }
                }
                streamDone = true
            }

            if (toolCalls.isEmpty()) break

            val assistantTurn = JSONObject().put("role", "assistant")
            assistantTurn.put("content", if (content.isEmpty()) JSONObject.NULL else content.toString())
            val callArray = JSONArray()
            toolCalls.values.forEach { call ->
                if (call.id.isBlank()) call.id = "call_${round}_${call.index}"
                callArray.put(
                    JSONObject()
                        .put("id", call.id)
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", call.name)
                                .put("arguments", call.arguments.toString().ifBlank { "{}" })
                        )
                )
            }
            assistantTurn.put("tool_calls", callArray)
            messages.put(assistantTurn)

            toolCalls.values.forEach { call ->
                val args = runCatching {
                    JSONObject(call.arguments.toString().ifBlank { "{}" })
                }.getOrElse { JSONObject() }

                emit(AgentEvent.ToolStarted(call.name, AgentTools.describe(call.name, args)))
                val result = workspace.executeTool(call.name, args)
                emit(
                    AgentEvent.ToolFinished(
                        name = call.name,
                        ok = result.ok,
                        result = result.output.lineSequence().firstOrNull().orEmpty().take(160)
                    )
                )
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.id)
                        .put("content", result.output)
                )
            }

            if (round == MAX_ROUNDS) {
                emit(AgentEvent.Text("\n\n(limite de $MAX_ROUNDS rodadas de ferramentas atingido)"))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun accumulateToolCalls(
        deltas: JSONArray?,
        accumulator: LinkedHashMap<Int, ToolCallAccumulator>
    ) {
        if (deltas == null) return
        for (i in 0 until deltas.length()) {
            val item = deltas.optJSONObject(i) ?: continue
            val index = item.optInt("index", i)
            val call = accumulator.getOrPut(index) { ToolCallAccumulator(index) }
            if (!item.isNull("id")) {
                val id = item.optString("id", "")
                if (id.isNotEmpty()) call.id = id
            }
            val function = item.optJSONObject("function") ?: continue
            if (!function.isNull("name")) {
                call.name = mergeFragment(call.name, function.optString("name", ""))
            }
            if (!function.isNull("arguments")) {
                call.arguments.append(function.optString("arguments", ""))
            }
        }
    }

    /**
     * Providers send streamed names either whole or split; merge without duplicating a name that
     * arrives complete in a single chunk.
     */
    private fun mergeFragment(current: String, fragment: String): String = when {
        fragment.isEmpty() -> current
        fragment == current -> current
        current.isEmpty() || fragment.startsWith(current) -> fragment
        else -> current + fragment
    }

    private fun buildMessages(settings: ChatSettings, history: List<ChatMessage>): JSONArray =
        JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt(settings, workspace.rootPath)))
            history.forEach { message ->
                put(
                    JSONObject()
                        .put("role", if (message.role == ChatRole.USER) "user" else "assistant")
                        .put("content", historyContent(message))
                )
            }
        }

    /** Assistant turns carry their tool activity as text so the model keeps the context. */
    private fun historyContent(message: ChatMessage): String {
        if (message.tools.isEmpty()) return message.content
        return buildString {
            if (message.content.isNotBlank()) append(message.content).append('\n')
            message.tools.forEach { tool ->
                append("[tool ").append(tool.name).append("] ").append(tool.summary)
                if (tool.result.isNotBlank()) append(" -> ").append(tool.result)
                append('\n')
            }
        }.trim()
        }

    private fun buildRequest(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        messages: JSONArray,
        toolsEnabled: Boolean
    ): Request {
        val payload = JSONObject()
            .put("model", modelId)
            .put("stream", true)
            .put("messages", messages)
        if (toolsEnabled) {
            payload.put("tools", AgentTools.definitions())
            payload.put("tool_choice", "auto")
        }

        return Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun looksLikeToolsUnsupported(code: Int, body: String): Boolean {
        if (code != 400 && code != 404 && code != 422) return false
        val text = body.lowercase()
        return text.contains("tool") || text.contains("function")
    }

    /**
     * Lists the models offered by the Kilo gateway catalog (the source for provider/model ids).
     */
    suspend fun fetchModels(settings: ChatSettings): List<KiloModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$CATALOG_BASE_URL/models")
            .apply {
                if (settings.currentConfig.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${settings.currentConfig.apiKey}")
                }
            }
            .get()
            .build()

        client.newCall(request).execute().use { response: Response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(300)}")
            }
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id")
                if (id.isBlank()) null else KiloModel(id = id, name = item.optString("name").ifBlank { id })
            }
        }
    }

    private class ToolCallAccumulator(val index: Int) {
        var id: String = ""
        var name: String = ""
        val arguments: StringBuilder = StringBuilder()
    }

    private companion object {
        const val CATALOG_BASE_URL = "https://api.kilo.ai/api/gateway"
        const val MAX_ROUNDS = 12

        fun systemPrompt(settings: ChatSettings, workspaceRoot: String): String = buildString {
            append(
                "You are Kilo, an autonomous AI coding agent running on Android. " +
                    "You work inside a real workspace directory and can act on it through the " +
                    "provided tools: list_files, read_file, write_file, edit_file, move_file, " +
                    "delete_file, create_dir and run_command.\n\n"
            )
            append(
                "Rules:\n" +
                    "- When the user asks you to create, edit, move, delete or inspect files, " +
                    "use the tools; do not just describe what you would do.\n" +
                    "- All paths are relative to the workspace root. Never use absolute paths.\n" +
                    "- Read a file before editing it. Use edit_file for small changes and " +
                    "write_file to create files or rewrite them completely.\n" +
                    "- Work step by step until the task is complete, then reply with a short " +
                    "summary of what changed.\n"
            )
            append(
                "- Answer in the same language the user writes in. Be concise. " +
                    "Use Markdown for code.\n"
            )
            append(
                "\nCurrent model: ${settings.model}\n" +
                    "Workspace root: $workspaceRoot"
            )
        }
    }
}
