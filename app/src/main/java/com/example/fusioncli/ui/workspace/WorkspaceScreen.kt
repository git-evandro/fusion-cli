package com.example.fusioncli.ui.workspace

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fusioncli.data.WorkspaceEntry
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shows the agent's workspace as a list of files; tapping a file opens its content in a second
 * pane. On a phone the two panes are separate screens, so the user sees the files first and then
 * the content. The folder can be opened in the device's own file manager.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun WorkspaceScreen(
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel = viewModel(
        factory = WorkspaceViewModel.Factory(LocalContext.current.applicationContext)
    )
) {
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val selectedPath by viewModel.selectedPath.collectAsStateWithLifecycle()
    val content by viewModel.fileContent.collectAsStateWithLifecycle()
    val followEdits by viewModel.followEdits.collectAsStateWithLifecycle()
    val lastEvent by viewModel.lastEvent.collectAsStateWithLifecycle()
    val rootPath by viewModel.rootPath.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val navigator = rememberListDetailPaneScaffoldNavigator<Nothing>()
    val scope = rememberCoroutineScope()

    // Only the list is shown until a file is selected; on phones the detail becomes its own screen.
    LaunchedEffect(selectedPath) {
        if (selectedPath != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
        }
    }

    val openFolder = { openFolderInDevice(context, viewModel.rootDirectory()) }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                WorkspaceListPane(
                    rootPath = rootPath,
                    entries = tree,
                    selectedPath = selectedPath,
                    followEdits = followEdits,
                    lastEventLabel = lastEvent?.let { "${it.action} • ${it.path}" },
                    onBack = onBack,
                    onSelect = { viewModel.select(it) },
                    onFollowChange = { viewModel.setFollowEdits(it) },
                    onOpenFolder = openFolder
                )
            }
        },
        detailPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                WorkspaceDetailPane(
                    path = selectedPath,
                    content = content,
                    onBack = {
                        scope.launch {
                            if (navigator.canNavigateBack()) navigator.navigateBack()
                        }
                    },
                    onOpenFolder = openFolder
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceListPane(
    rootPath: String,
    entries: List<WorkspaceEntry>,
    selectedPath: String?,
    followEdits: Boolean,
    lastEventLabel: String?,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onFollowChange: (Boolean) -> Unit,
    onOpenFolder: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arquivos do workspace") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenFolder) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Abrir pasta no dispositivo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = rootPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(checked = followEdits, onCheckedChange = onFollowChange)
                Spacer(Modifier.width(8.dp))
                Text("Seguir edições", style = MaterialTheme.typography.bodySmall)
            }
            lastEventLabel?.let { label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FileTree(
                entries = entries,
                selectedPath = selectedPath,
                onSelect = onSelect,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceDetailPane(
    path: String?,
    content: String,
    onBack: () -> Unit,
    onOpenFolder: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = path ?: "Conteúdo",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (path != null) {
                        IconButton(onClick = { copyToClipboard(context, content) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar conteúdo")
                        }
                    }
                    IconButton(onClick = onOpenFolder) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Abrir pasta no dispositivo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        FileContent(
            content = content,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        )
    }
}

@Composable
private fun FileTree(
    entries: List<WorkspaceEntry>,
    selectedPath: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "O workspace está vazio.\nPeça ao agente para criar arquivos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                items(entries, key = { it.path }) { entry ->
                    val depth = entry.path.count { it == '/' }
                    val selected = entry.path == selectedPath
                    val background by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        label = "TreeRow"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(background)
                            .clickable(enabled = !entry.isDirectory) { onSelect(entry.path) }
                            .padding(
                                start = (10 + depth * 12).dp,
                                end = 10.dp,
                                top = 10.dp,
                                bottom = 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (entry.isDirectory) Icons.Rounded.Folder
                            else Icons.Rounded.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.path.substringAfterLast('/'),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileContent(
    content: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = content,
                color = Color(0xFFB9F6CA),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("FusionCLI file", text))
}

/**
 * Opens the workspace folder in the device's file manager, positioned at the folder itself.
 * When it lives in shared internal storage we point the system picker at it via a documents URI,
 * so it opens on `/sdcard/FusionCLI` instead of the default Downloads folder.
 */
private fun openFolderInDevice(context: Context, directory: File) {
    val initialUri = externalDocumentsUri(directory)
    if (initialUri != null) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra("android.provider.extra.INITIAL_URI", initialUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafely(context, intent)) return
    }

    // Fallback for app-specific storage: let a file manager open the directory directly.
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", directory)
    }.getOrNull()
    if (uri != null) {
        for (type in listOf("vnd.android.document/directory", "resource/folder", "*/*")) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startSafely(context, intent)) return
        }
    }

    Toast.makeText(
        context,
        "Nenhum gerenciador de arquivos encontrado. Pasta: ${directory.absolutePath}",
        Toast.LENGTH_LONG
    ).show()
}

/** Builds a documents URI for a folder under the primary shared storage, e.g. primary:FusionCLI. */
private fun externalDocumentsUri(directory: File): Uri? {
    val storage = Environment.getExternalStorageDirectory() ?: return null
    val storagePath = storage.absolutePath
    val path = directory.absolutePath
    if (!path.startsWith(storagePath)) return null
    val relative = path.removePrefix(storagePath).trim('/')
    if (relative.isEmpty()) return null
    val documentId = Uri.encode("primary:$relative")
    return Uri.parse("content://com.android.externalstorage.documents/document/$documentId")
}

private fun startSafely(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
