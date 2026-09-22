package com.example.fusioncli.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fusioncli.R
import com.example.fusioncli.data.ChatMessage
import com.example.fusioncli.data.ChatRole
import com.example.fusioncli.data.ToolActivity
import com.example.fusioncli.data.Conversation
import com.example.fusioncli.data.KiloModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.Factory(LocalContext.current.applicationContext)
    )
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConversationId by viewModel.currentConversationId.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var settingsProvider by remember { mutableStateOf(settings.currentProvider) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationsDrawer(
                conversations = conversations,
                currentId = currentConversationId,
                onNewConversation = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                onSelect = {
                    viewModel.selectConversation(it)
                    scope.launch { drawerState.close() }
                },
                onDelete = { viewModel.deleteConversation(it) }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_logo),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Kilo Chat")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Conversas")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenWorkspace) {
                            Icon(Icons.Rounded.Folder, contentDescription = "Workspace do agente")
                        }
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = {
                            settingsProvider = settings.currentProvider
                            showSettings = true
                        }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Configurações")
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
                    .padding(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showModelPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = settings.model,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Agente Kilo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Peça para criar, editar ou mover arquivos — as mudanças " +
                                    "acontecem no workspace e aparecem ao vivo no botão de pasta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (settings.currentConfig.apiKey.isBlank())
                                    "Toque em ⚙ e informe a API key do provedor '${settings.currentProvider}'."
                                else
                                    "Provedor: ${settings.currentProvider}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages) { message -> MessageBubble(message) }
                    }
                }

                if (isSending) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Mensagem...") },
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.send(input)
                            input = ""
                        },
                        enabled = input.isNotBlank() && !isSending
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            provider = settingsProvider,
            apiKey = settings.configFor(settingsProvider).apiKey,
            baseUrl = settings.configFor(settingsProvider).baseUrl,
            model = settings.model,
            onDismiss = { showSettings = false },
            onSave = { key, base, model ->
                viewModel.saveProvider(settingsProvider, key, base, model)
                showSettings = false
            }
        )
    }

    if (showModelPicker) {
        ModelPickerDialog(
            models = models,
            loading = modelsLoading,
            selectedModelId = settings.model,
            onRetry = { viewModel.loadModels() },
            onSelect = { provider, modelId ->
                viewModel.selectModel(provider, modelId)
                showModelPicker = false
                if (settings.configFor(provider).apiKey.isBlank()) {
                    settingsProvider = provider
                    showSettings = true
                }
            },
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun ConversationsDrawer(
    conversations: List<Conversation>,
    currentId: String?,
    onNewConversation: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    ModalDrawerSheet {
        Text(
            text = "Conversas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = onNewConversation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Nova conversa")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn {
            items(conversations, key = { it.id }) { conversation ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = conversation.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    selected = conversation.id == currentId,
                    onClick = { onSelect(conversation.id) },
                    badge = {
                        IconButton(onClick = { onDelete(conversation.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Excluir")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<KiloModel>,
    loading: Boolean,
    selectedModelId: String,
    onRetry: () -> Unit,
    onSelect: (provider: String, modelId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var provider by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf("") }
    val providers = remember(models) { models.map { it.provider }.distinct().sorted() }
    val providerModels = remember(models, provider) {
        provider?.let { selected -> models.filter { it.provider == selected } } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(provider?.let { "Modelos • $it" } ?: "Escolha o provedor") },
        text = {
            when {
                loading && models.isEmpty() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Carregando modelos...")
                    }
                }
                provider == null -> {
                    Column {
                        if (models.isEmpty()) {
                            Text("Nenhum provedor disponível.")
                            TextButton(onClick = onRetry) { Text("Tentar de novo") }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                                items(providers) { item ->
                                    TextButton(
                                        onClick = { provider = item },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val selectedProvider = provider!!
                    Column {
                        OutlinedTextField(
                            value = manual,
                            onValueChange = { manual = it },
                            label = { Text("Modelo (id da API)") },
                            placeholder = { Text("ex.: deepseek-flash") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Modelos disponíveis:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(providerModels) { model ->
                                TextButton(
                                    onClick = { onSelect(model.provider, model.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (model.id == selectedModelId) "✓ ${model.name}" else model.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            item {
                                TextButton(
                                    onClick = { onSelect(selectedProvider, manual.trim()) },
                                    enabled = manual.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Usar modelo digitado")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (provider != null) {
                TextButton(onClick = { provider = null }) { Text("Voltar") }
            } else {
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
        dismissButton = {
            if (provider == null) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    message.tools.forEach { activity -> ToolActivityRow(activity) }

                    if (message.content.isNotEmpty() || message.tools.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = message.content.ifEmpty { "…" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (message.isStreaming) {
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    } else if (message.isStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            if (!isUser && message.content.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    IconButton(
                        onClick = { copyMessageToClipboard(context, message.content) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copiar resposta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolActivityRow(activity: ToolActivity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = toolIcon(activity.name),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (activity.result.isNotBlank()) {
                Text(
                    text = activity.result,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (activity.ok) {
            null -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            true -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Concluído",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp)
            )
            false -> Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = "Falhou",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "list_files" -> Icons.Rounded.FolderOpen
    "read_file" -> Icons.Rounded.Description
    "write_file" -> Icons.Rounded.PostAdd
    "edit_file" -> Icons.Rounded.Edit
    "move_file" -> Icons.Rounded.DriveFileMove
    "delete_file" -> Icons.Rounded.DeleteOutline
    "create_dir" -> Icons.Rounded.CreateNewFolder
    "run_command" -> Icons.Rounded.Terminal
    else -> Icons.Rounded.Build
}

private fun copyMessageToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kilo response", text))
}

@Composable
private fun SettingsDialog(
    provider: String,
    apiKey: String,
    baseUrl: String,
    model: String,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, baseUrl: String, model: String) -> Unit
) {
    var key by remember { mutableStateOf(apiKey) }
    var base by remember { mutableStateOf(baseUrl) }
    var modelId by remember { mutableStateOf(model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Credenciais • $provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key ($provider)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Modelo") },
                    placeholder = { Text("ex.: deepseek-flash") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Cada provedor tem sua própria API key, Base URL e modelo. " +
                        "Se a Base URL não for o gateway do Kilo, o prefixo 'provedor/' é removido do modelo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = { onSave(key, base, modelId) }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
