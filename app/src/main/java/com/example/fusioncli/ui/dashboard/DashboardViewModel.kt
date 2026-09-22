package com.example.fusioncli.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.fusioncli.data.ExecutionState
import com.example.fusioncli.data.InstallManager

/**
 * Thin facade over the application-scoped [InstallManager]. The install runs in that manager so
 * it continues in the background and notifies when it finishes.
 */
class DashboardViewModel : ViewModel() {

    val logs = InstallManager.logs
    val executionState = InstallManager.executionState
    val installedVersion = InstallManager.installedVersion

    fun runKiloInstall() = InstallManager.run()

    fun copyLogsToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("FusionCLI Logs", logs.value.joinToString("\n"))
        clipboard.setPrimaryClip(clip)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            InstallManager.init(context.applicationContext)
            return DashboardViewModel() as T
        }
    }
}
