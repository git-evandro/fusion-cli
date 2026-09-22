package com.example.fusioncli.ui.workspace

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fusioncli.data.StorageAccess
import com.example.fusioncli.data.WorkspaceRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Asks for storage access as soon as the app opens and keeps it valid. On Android 10 and below
 * this is the normal runtime dialog; on Android 11+ it opens the "All files access" screen, which
 * is the only way to keep writing to `/sdcard/FusionCLI`.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StoragePermissionGate() {
    val context = LocalContext.current

    if (StorageAccess.usesRuntimePermission()) {
        val permission = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
            if (granted) WorkspaceRepository.applyStoragePermission(context)
        }
        LaunchedEffect(Unit) {
            if (permission.status.isGranted) {
                WorkspaceRepository.applyStoragePermission(context)
            } else {
                permission.launchPermissionRequest()
            }
        }
    } else {
        // Coming back from the settings screen: re-check and prepare the folder.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && StorageAccess.hasAccess(context)) {
                    WorkspaceRepository.applyStoragePermission(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(Unit) {
            if (!StorageAccess.hasAccess(context)) {
                runCatching { context.startActivity(StorageAccess.grantIntent(context)) }
            }
        }
    }
}

/**
 * Shown while the storage permission is missing, with a button that opens the right settings
 * screen for the device's Android version.
 */
@Composable
fun StorageAccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StorageAccess.hasAccess(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = StorageAccess.hasAccess(context)
                if (hasAccess) WorkspaceRepository.applyStoragePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasAccess) return

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Para criar e editar arquivos, o app precisa acessar a pasta interna " +
                    "/sdcard/${StorageAccess.FOLDER_NAME}. Toque para liberar o acesso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = {
                runCatching { context.startActivity(StorageAccess.grantIntent(context)) }
            }) {
                Text("Conceder acesso")
            }
        }
    }
}
