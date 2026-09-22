package com.example.fusioncli.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Tracks how many long tasks are running and drives [AgentService] accordingly: the foreground
 * service stays up while at least one task is active, and the notification is posted when the
 * last one finishes.
 */
object ForegroundTasks {

    private val lock = Any()

    @Volatile
    var activeCount: Int = 0
        private set

    fun begin(context: Context, label: String) {
        synchronized(lock) { activeCount++ }
        val intent = Intent(context, AgentService::class.java).apply {
            action = AgentService.ACTION_START
            putExtra(AgentService.EXTRA_LABEL, label)
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    fun end(context: Context, title: String, text: String) {
        synchronized(lock) { activeCount = (activeCount - 1).coerceAtLeast(0) }
        val intent = Intent(context, AgentService::class.java).apply {
            action = AgentService.ACTION_STOP
            putExtra(AgentService.EXTRA_TITLE, title)
            putExtra(AgentService.EXTRA_TEXT, text)
        }
        runCatching { context.startService(intent) }
    }
}
