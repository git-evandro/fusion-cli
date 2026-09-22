package com.example.fusioncli.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The OpenAI-compatible function/tool schemas advertised to the model so it can act on the
 * workspace (read, create, edit, move, delete, run commands) instead of only chatting.
 */
object AgentTools {

    fun definitions(): JSONArray = JSONArray().apply {
        put(
            function(
                name = "list_files",
                description = "List every file and directory in the workspace, recursively. " +
                    "Paths are relative to the workspace root.",
                properties = JSONObject(),
                required = emptyList()
            )
        )
        put(
            function(
                name = "read_file",
                description = "Read a text file from the workspace.",
                properties = JSONObject()
                    .put("path", string("Workspace-relative path, e.g. 'src/main.kt'.")),
                required = listOf("path")
            )
        )
        put(
            function(
                name = "write_file",
                description = "Create a file or overwrite it completely with new content. " +
                    "Use this to create files; prefer edit_file for small changes to existing files.",
                properties = JSONObject()
                    .put("path", string("Workspace-relative path."))
                    .put("content", string("Full file content to write.")),
                required = listOf("path", "content")
            )
        )
        put(
            function(
                name = "edit_file",
                description = "Replace an exact string in an existing file. old_string must match " +
                    "the file content exactly (including indentation). By default old_string must " +
                    "be unique; set replace_all=true to replace every occurrence.",
                properties = JSONObject()
                    .put("path", string("Workspace-relative path."))
                    .put("old_string", string("Exact text to find."))
                    .put("new_string", string("Replacement text."))
                    .put("replace_all", boolean("Replace all occurrences instead of exactly one.")),
                required = listOf("path", "old_string", "new_string")
            )
        )
        put(
            function(
                name = "move_file",
                description = "Move or rename a file or directory inside the workspace.",
                properties = JSONObject()
                    .put("from", string("Current workspace-relative path."))
                    .put("to", string("New workspace-relative path.")),
                required = listOf("from", "to")
            )
        )
        put(
            function(
                name = "delete_file",
                description = "Delete a file or directory (recursively) from the workspace.",
                properties = JSONObject()
                    .put("path", string("Workspace-relative path.")),
                required = listOf("path")
            )
        )
        put(
            function(
                name = "create_dir",
                description = "Create a directory (and any missing parents) in the workspace.",
                properties = JSONObject()
                    .put("path", string("Workspace-relative directory path.")),
                required = listOf("path")
            )
        )
        put(
            function(
                name = "run_command",
                description = "Run a shell command (sh -c) with the workspace as the working " +
                    "directory and return its output. Use it to build, test or inspect files.",
                properties = JSONObject()
                    .put("command", string("Shell command to execute.")),
                required = listOf("command")
            )
        )
    }

    /** A short human-readable description of a tool call, shown in the chat as it happens. */
    fun describe(name: String, args: JSONObject): String = when (name) {
        "list_files" -> "Listando arquivos do workspace"
        "read_file" -> "Lendo ${args.optString("path", "?")}"
        "write_file" -> "Escrevendo ${args.optString("path", "?")}"
        "edit_file" -> "Editando ${args.optString("path", "?")}"
        "move_file" -> "Movendo ${args.optString("from", "?")} → ${args.optString("to", "?")}"
        "delete_file" -> "Excluindo ${args.optString("path", "?")}"
        "create_dir" -> "Criando pasta ${args.optString("path", "?")}"
        "run_command" -> "Executando: ${args.optString("command", "?").take(120)}"
        else -> name
    }

    private fun function(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String>
    ): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", name)
                .put("description", description)
                .put(
                    "parameters",
                    JSONObject()
                        .put("type", "object")
                        .put("properties", properties)
                        .put("required", JSONArray(required))
                )
        )

    private fun string(description: String): JSONObject =
        JSONObject().put("type", "string").put("description", description)

    private fun boolean(description: String): JSONObject =
        JSONObject().put("type", "boolean").put("description", description)
}
