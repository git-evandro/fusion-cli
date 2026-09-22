package com.example.fusioncli.data

import android.content.Context
import com.example.fusioncli.repository.CommandRepository
import com.example.fusioncli.service.ForegroundTasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application-scoped Kilo installer. It runs off the ViewModel so the install continues in the
 * background, and it notifies when it finishes.
 */
object InstallManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    private var appContext: Context? = null
    private var repository: CommandRepository? = null

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _executionState = MutableStateFlow(ExecutionState.IDLE)
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()

    private val _installedVersion = MutableStateFlow<String?>(null)
    val installedVersion: StateFlow<String?> = _installedVersion.asStateFlow()

    fun init(context: Context) {
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            appContext = app
            val repo = CommandRepository(app, app.filesDir, File(app.cacheDir, "tmp"))
            repository = repo
            _installedVersion.value = repo.installedVersion()
            initialized = true
            scope.launch { repo.executionState.collect { _executionState.value = it } }
        }
    }

    fun run() {
        val repo = repository ?: return
        if (_executionState.value == ExecutionState.IN_PROGRESS) return
        val context = appContext

        _logs.value = emptyList()
        context?.let { ForegroundTasks.begin(it, "Instalando o Kilo…") }

        scope.launch {
            var title = "Kilo instalado"
            var text = "A instalação terminou com sucesso."
            try {
                repo.executeKiloInstall().collect { output ->
                    val line = when (output) {
                        is CommandOutput.Stdout -> output.line
                        is CommandOutput.Stderr -> "[STDERR] ${output.line}"
                        is CommandOutput.Error -> "[ERROR] ${output.message}"
                    }
                    _logs.update { it + line }
                }
            } catch (e: Exception) {
                title = "Falha na instalação"
                text = e.message ?: "Erro desconhecido."
            } finally {
                _installedVersion.value = repo.installedVersion()
            }

            if (repo.executionState.value == ExecutionState.FAILED && title != "Falha na instalação") {
                title = "Falha na instalação"
                text = _logs.value.lastOrNull()?.take(140) ?: text
            }
            context?.let { ForegroundTasks.end(it, title, text) }
        }
    }
}
