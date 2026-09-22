package com.example.fusioncli

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.ViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.fusioncli.ui.Route
import com.example.fusioncli.ui.chat.ChatScreen
import com.example.fusioncli.ui.dashboard.DashboardScreen
import com.example.fusioncli.ui.theme.FusionCLITheme
import com.example.fusioncli.ui.workspace.StoragePermissionGate
import com.example.fusioncli.ui.workspace.WorkspaceScreen

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            FusionCLITheme {
                // Ask for storage access as soon as the app opens.
                StoragePermissionGate()

                val backStack = rememberNavBackStack(Route.Dashboard)
                val viewModelStoreProvider = rememberViewModelStoreProvider()
                
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        ViewModelStoreNavEntryDecorator(viewModelStoreProvider)
                    )
                ) { route ->
                    when (route) {
                        Route.Dashboard -> {
                            NavEntry(route) {
                                DashboardScreen(
                                    onOpenChat = { backStack.add(Route.Chat) },
                                    onOpenWorkspace = { backStack.add(Route.Workspace) }
                                )
                            }
                        }
                        Route.Chat -> {
                            NavEntry(route) {
                                ChatScreen(
                                    onBack = {
                                        if (backStack.size > 1) {
                                            backStack.removeAt(backStack.lastIndex)
                                        }
                                    },
                                    onOpenWorkspace = { backStack.add(Route.Workspace) }
                                )
                            }
                        }
                        Route.Workspace -> {
                            NavEntry(route) {
                                WorkspaceScreen(
                                    onBack = {
                                        if (backStack.size > 1) {
                                            backStack.removeAt(backStack.lastIndex)
                                        }
                                    }
                                )
                            }
                        }
                        else -> NavEntry(route) { }
                    }
                }
            }
        }
    }
}
