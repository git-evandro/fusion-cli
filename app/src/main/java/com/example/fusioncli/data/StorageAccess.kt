package com.example.fusioncli.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Access to the single folder the app uses: `<internal storage>/FusionCLI`, i.e.
 * `/sdcard/FusionCLI`. Nothing is ever stored in `Android/data`.
 *
 * Android 10 and below grant this with the runtime storage permission. Android 11+ removed that
 * and requires the "All files access" permission, which the user grants from a settings screen.
 */
object StorageAccess {

    const val FOLDER_NAME = "FusionCLI"

    /** The workspace folder: `/sdcard/FusionCLI`. */
    fun directory(): File = File(Environment.getExternalStorageDirectory(), FOLDER_NAME)

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    /** Android 10 and below ask for the storage permission with a normal dialog. */
    fun usesRuntimePermission(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q

    /** Android 11+ opens the "All files access" screen; earlier versions open app settings. */
    fun grantIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            if (context.packageManager.resolveActivity(appIntent, 0) != null) return appIntent
            return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
