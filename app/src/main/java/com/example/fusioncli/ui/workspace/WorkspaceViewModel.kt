package com.example.fusioncli.ui.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.fusioncli.data.WorkspaceEntry
import com.example.fusioncli.data.WorkspaceEvent
import com.example.fusioncli.data.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the workspace screen: the live file tree and the content of the selected file. When
 * [followEdits] is on, the file the agent just changed is opened automatically so the user can
 * watch the edits in real time.
 */
class WorkspaceViewModel(private val repository: WorkspaceRepository) : ViewModel() {

    private val _selectedPath = MutableStateFlow<String?>(null)
    val selectedPath: StateFlow<String?> = _selectedPath.asStateFlow()

    private val _followEdits = MutableStateFlow(true)
    val followEdits: StateFlow<Boolean> = _followEdits.asStateFlow()

    private val _lastEvent = MutableStateFlow<WorkspaceEvent?>(null)
    val lastEvent: StateFlow<WorkspaceEvent?> = _lastEvent.asStateFlow()

    val rootPath: StateFlow<String> = repository.version
        .map { repository.rootPath }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.rootPath)

    /** The current workspace directory (can change once the storage permission is granted). */
    fun rootDirectory(): File = repository.rootDirectory

    val tree: StateFlow<List<WorkspaceEntry>> = repository.version
        .map { repository.listTree() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val fileContent: StateFlow<String> = combine(_selectedPath, repository.version) { path, _ ->
        if (path == null) {
            "Selecione um arquivo à esquerda para ver o conteúdo."
        } else {
            runCatching { repository.read(path) }
                .getOrElse { "Não foi possível ler '$path': ${it.message}" }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            repository.lastEvent.collect { event ->
                _lastEvent.value = event
                if (event == null) return@collect
                if (_followEdits.value && repository.isFile(event.path)) {
                    _selectedPath.value = event.path
                }
            }
        }
    }

    fun select(path: String) {
        _selectedPath.value = path
    }

    fun setFollowEdits(enabled: Boolean) {
        _followEdits.value = enabled
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return WorkspaceViewModel(WorkspaceRepository.getInstance(context.applicationContext)) as T
        }
    }
}
