package com.example.fusioncli.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.TimeUnit

/** One node of the workspace file tree, with a path relative to the workspace root. */
data class WorkspaceEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long
)

/** The most recent file mutation, used by the workspace screen to follow edits live. */
data class WorkspaceEvent(
    val path: String,
    val action: String,
    val at: Long = System.currentTimeMillis()
)

/**
 * The agent's real workspace: an app-private directory where the model reads, creates, edits,
 * moves and deletes files. Every mutation bumps [version] and publishes a [WorkspaceEvent] so the
 * UI can refresh (and follow) the changes in real time.
 */
class WorkspaceRepository(root: File) {

    private var root: File = root

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    private val _lastEvent = MutableStateFlow<WorkspaceEvent?>(null)
    val lastEvent: StateFlow<WorkspaceEvent?> = _lastEvent.asStateFlow()

    val rootPath: String get() = root.absolutePath

    /** The workspace directory itself, used to open it in the device's file manager. */
    val rootDirectory: File get() = root

    init {
        root.mkdirs()
    }

    /** Refreshes listeners (e.g. once the storage folder becomes available). */
    fun refresh() {
        notifyChange(".", "storage")
    }

    private fun notifyChange(path: String, action: String) {
        _lastEvent.value = WorkspaceEvent(path, action)
        _version.update { it + 1 }
    }

    /** Resolves a workspace-relative path, refusing anything that escapes [root]. */
    fun resolve(relativePath: String): File {
        val cleaned = relativePath.trim().trimStart('/', '\\')
        val canonicalRoot = root.canonicalFile
        val candidate = File(root, cleaned).canonicalFile
        val insideRoot = candidate == canonicalRoot ||
            candidate.path.startsWith(canonicalRoot.path + File.separator)
        require(insideRoot) { "Caminho fora do workspace: $relativePath" }
        return candidate
    }

    private fun rel(file: File): String =
        file.canonicalFile.relativeTo(root.canonicalFile).path.replace(File.separatorChar, '/')

    fun listTree(limit: Int = MAX_ENTRIES): List<WorkspaceEntry> {
        root.mkdirs()
        val out = ArrayList<WorkspaceEntry>()
        fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH || out.size >= limit) return
            val children = dir.listFiles()?.sortedWith(
                compareBy({ !it.isDirectory }, { it.name.lowercase() })
            ) ?: return
            for (child in children) {
                if (out.size >= limit) return
                val isDirectory = child.isDirectory
                out.add(WorkspaceEntry(rel(child), isDirectory, if (isDirectory) 0L else child.length()))
                if (isDirectory) walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    fun read(relativePath: String): String {
        val file = resolve(relativePath)
        require(file.isFile) { "Arquivo não encontrado: $relativePath" }
        val text = file.readText()
        return if (text.length > MAX_READ_CHARS) {
            text.take(MAX_READ_CHARS) + "\n\n... (arquivo truncado em $MAX_READ_CHARS caracteres)"
        } else {
            text
        }
    }

    /**
     * Executes a tool by name. Always returns a result (never throws) so the model can recover
     * from bad arguments instead of failing the whole turn.
     */
    fun executeTool(name: String, args: org.json.JSONObject): ToolResult = try {
        when (name) {
            "list_files" -> listFilesTool()
            "read_file" -> readFileTool(args.required("path"))
            "write_file" -> writeFileTool(args.required("path"), args.optString("content", ""))
            "edit_file" -> editFileTool(
                path = args.required("path"),
                oldString = args.optString("old_string", ""),
                newString = args.optString("new_string", ""),
                replaceAll = args.optBoolean("replace_all", false)
            )
            "move_file" -> moveFileTool(args.required("from"), args.required("to"))
            "delete_file" -> deleteFileTool(args.required("path"))
            "create_dir" -> createDirTool(args.required("path"))
            "run_command" -> runCommand(args.required("command"))
            else -> ToolResult(false, "Unknown tool: $name")
        }
    } catch (e: Exception) {
        ToolResult(false, "Error: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun listFilesTool(): ToolResult {
        val entries = listTree()
        if (entries.isEmpty()) return ToolResult(true, "The workspace is empty.")
        val text = entries.joinToString("\n") { entry ->
            if (entry.isDirectory) "${entry.path}/" else "${entry.path} (${entry.size} bytes)"
        }
        return ToolResult(true, text)
    }

    private fun readFileTool(path: String): ToolResult {
        val content = read(path)
        val lines = content.count { it == '\n' } + 1
        return ToolResult(true, "$path ($lines lines)\n$content")
    }

    private fun writeFileTool(path: String, content: String): ToolResult {
        val file = resolve(path)
        val existed = file.isFile
        file.parentFile?.mkdirs()
        file.writeText(content)
        notifyChange(rel(file), if (existed) "update" else "create")
        return ToolResult(
            true,
            "${if (existed) "Updated" else "Created"} $path (${content.length} chars)."
        )
    }

    private fun editFileTool(
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean
    ): ToolResult {
        if (oldString.isEmpty()) return ToolResult(false, "old_string must not be empty.")
        val file = resolve(path)
        if (!file.isFile) return ToolResult(false, "File not found: $path")

        var text = file.readText()
        val occurrences = countOccurrences(text, oldString)
        if (occurrences == 0) return ToolResult(false, "old_string not found in $path")
        if (occurrences > 1 && !replaceAll) {
            return ToolResult(
                false,
                "old_string appears $occurrences times in $path; add more context or set replace_all=true."
            )
        }

        text = if (replaceAll) text.replace(oldString, newString)
        else text.replaceFirst(oldString, newString)
        file.writeText(text)
        notifyChange(rel(file), "edit")
        return ToolResult(true, "Edited $path (${if (replaceAll) occurrences else 1} replacement(s)).")
    }

    private fun moveFileTool(from: String, to: String): ToolResult {
        val source = resolve(from)
        if (!source.exists()) return ToolResult(false, "Source not found: $from")
        val target = resolve(to)
        target.parentFile?.mkdirs()
        val moved = source.renameTo(target) || run {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        if (!moved) return ToolResult(false, "Could not move $from to $to")
        notifyChange(rel(target), "move")
        return ToolResult(true, "Moved $from to $to.")
    }

    private fun deleteFileTool(path: String): ToolResult {
        val file = resolve(path)
        if (!file.exists()) return ToolResult(false, "Not found: $path")
        val deleted = file.deleteRecursively()
        if (!deleted) return ToolResult(false, "Could not delete $path")
        notifyChange(path, "delete")
        return ToolResult(true, "Deleted $path.")
    }

    private fun createDirTool(path: String): ToolResult {
        val dir = resolve(path)
        if (dir.isDirectory) return ToolResult(true, "Directory already exists: $path")
        val created = dir.mkdirs()
        if (!created) return ToolResult(false, "Could not create directory $path")
        notifyChange(rel(dir), "mkdir")
        return ToolResult(true, "Created directory $path.")
    }

    /**
     * Runs a shell command inside the workspace (`sh -c`, since Android has no bash). This is the
     * same interpreter family the installer uses.
     */
    fun runCommand(command: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): ToolResult {
        val processBuilder = ProcessBuilder("sh", "-c", command)
        processBuilder.directory(root)
        processBuilder.redirectErrorStream(true)
        val environment = processBuilder.environment()
        environment["HOME"] = root.absolutePath
        environment["PWD"] = root.absolutePath
        environment["TERM"] = "xterm"

        val process = processBuilder.start()
        val builder = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().use { input ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        builder.append(buffer, 0, read)
                        if (builder.length > MAX_COMMAND_CHARS) break
                    }
                }
            } catch (_: Exception) {
            }
        }
        reader.isDaemon = true
        reader.start()

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        reader.join(2000)

        notifyChange(".", "command")

        val output = builder.toString().take(MAX_COMMAND_CHARS)
        val exit = if (finished) {
            runCatching { process.exitValue() }.getOrDefault(-1)
        } else {
            -1
        }
        val header = if (finished) "exit=$exit" else "timeout after ${timeoutMs / 1000}s"
        return ToolResult(finished && exit == 0, "$header\n$output")
    }

    fun isFile(relativePath: String): Boolean =
        runCatching { resolve(relativePath).isFile }.getOrDefault(false)

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun org.json.JSONObject.required(key: String): String {
        val value = optString(key, "")
        if (value.isBlank()) throw IllegalArgumentException("Missing required argument: $key")
        return value
    }

    companion object {
        private const val MAX_DEPTH = 8
        private const val MAX_ENTRIES = 600
        private const val MAX_READ_CHARS = 40_000
        private const val MAX_COMMAND_CHARS = 8_000
        private const val COMMAND_TIMEOUT_MS = 60_000L

        @Volatile
        private var instance: WorkspaceRepository? = null

        /**
         * The single workspace folder: `/sdcard/FusionCLI`. There is no fallback to
         * `Android/data`; without the storage permission the folder simply is not available yet.
         */
        fun getInstance(context: Context): WorkspaceRepository =
            instance ?: synchronized(this) {
                instance ?: WorkspaceRepository(StorageAccess.directory()).also { instance = it }
            }

        /**
         * Called once the storage permission is granted: creates `/sdcard/FusionCLI`, imports any
         * files left by older versions of the app, and refreshes the UI.
         */
        fun applyStoragePermission(context: Context): WorkspaceRepository {
            val repository = getInstance(context)
            if (!StorageAccess.hasAccess(context)) return repository
            val root = StorageAccess.directory()
            val wasMissing = !root.isDirectory
            root.mkdirs()
            if (wasMissing || root.listFiles()?.isEmpty() != false) {
                importLegacy(context, root)
            }
            repository.refresh()
            return repository
        }

        /** One-time import from the folders used by earlier versions (read-only source). */
        private fun importLegacy(context: Context, target: File) {
            val candidates = listOfNotNull(
                context.getExternalFilesDir(null)?.let { File(it, "workspace") },
                File(context.filesDir, "workspace")
            )
            candidates
                .filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
                .forEach { source ->
                    source.listFiles()?.forEach { child ->
                        runCatching { child.copyRecursively(File(target, child.name), overwrite = false) }
                    }
                }
        }
    }
}
