package com.example.fusioncli.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fusioncli.R
import com.example.fusioncli.data.ExecutionState
import com.example.fusioncli.ui.theme.FusionCLITheme
import com.example.fusioncli.ui.workspace.StorageAccessBanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DashboardScreen(
    onOpenChat: () -> Unit,
    onOpenWorkspace: () -> Unit,
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(LocalContext.current.applicationContext)
    )
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val installedVersion by viewModel.installedVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = rememberListDetailPaneScaffoldNavigator<Nothing>()
    val coroutineScope = rememberCoroutineScope()

    // Keep the dashboard visible during execution and return to it when an execution ends,
    // so the status banner is always shown.
    LaunchedEffect(executionState) {
        if (executionState == ExecutionState.SUCCESS || executionState == ExecutionState.FAILED) {
            if (navigator.canNavigateBack()) navigator.navigateBack()
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                DashboardControls(
                    executionState = executionState,
                    installedVersion = installedVersion,
                    onRunClick = { viewModel.runKiloInstall() },
                    onOpenChat = onOpenChat,
                    onOpenWorkspace = onOpenWorkspace,
                    onCopyClick = { viewModel.copyLogsToClipboard(context) },
                    onViewLogsClick = {
                        coroutineScope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                        }
                    }
                )
            }
        },
        detailPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                LogStreamer(
                    logs = logs,
                    onBackClick = {
                        coroutineScope.launch {
                            navigator.navigateBack()
                        }
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun DashboardControls(
    executionState: ExecutionState,
    installedVersion: String?,
    onRunClick: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onCopyClick: () -> Unit,
    onViewLogsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "FusionCLI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        StorageAccessBanner()

        StatusBanner(state = executionState)

        Text(
            text = installedVersion?.let { "Kilo installed: v$it" }
                ?: "Kilo is not installed yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (executionState == ExecutionState.IN_PROGRESS) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = onRunClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = executionState != ExecutionState.IN_PROGRESS,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("One-Click Kilo Install")
        }

        OutlinedButton(
            onClick = onCopyClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Copy Logs to Clipboard")
        }

        TextButton(
            onClick = onViewLogsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Live Logs")
        }

        OutlinedButton(
            onClick = onOpenChat,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Rounded.Chat, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Chat with Kilo")
        }

        OutlinedButton(
            onClick = onOpenWorkspace,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Agent Workspace")
        }
    }
}

@Composable
fun StatusBanner(state: ExecutionState) {
    val backgroundColor by animateColorAsState(
        targetValue = when (state) {
            ExecutionState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            ExecutionState.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer
            ExecutionState.SUCCESS -> Color(0xFF4CAF50) // Green
            ExecutionState.FAILED -> MaterialTheme.colorScheme.errorContainer
        }, label = "BannerBackground"
    )

    val textColor = when (state) {
        ExecutionState.SUCCESS -> Color.White
        ExecutionState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when (state) {
        ExecutionState.IDLE -> "System Idle"
        ExecutionState.IN_PROGRESS -> "Executing..."
        ExecutionState.SUCCESS -> "Success"
        ExecutionState.FAILED -> "Failed"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogStreamer(
    logs: List<String>,
    onBackClick: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Console Logs", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onBackClick) {
                        Text("Dashboard")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(logs) { log ->
                    val displayLog = log.takeIf { it != "null" } ?: ""
                    if (displayLog.isNotEmpty()) {
                        Text(
                            text = displayLog,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun DashboardPreviewPhone() {
    FusionCLITheme {
        DashboardScreen(onOpenChat = {}, onOpenWorkspace = {})
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun DashboardPreviewTablet() {
    FusionCLITheme {
        DashboardScreen(onOpenChat = {}, onOpenWorkspace = {})
    }
}
