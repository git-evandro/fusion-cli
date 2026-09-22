package com.example.fusioncli.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service that keeps the app process alive while a task (agent turn or Kilo install)
 * runs in the background, and posts a notification when the last task finishes.
 */
class AgentService : Service() {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Trabalhando em segundo plano…"
                startForeground(Notifications.PROGRESS_ID, Notifications.progress(this, label))
            }
            ACTION_STOP -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Tarefa concluída"
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (ForegroundTasks.activeCount <= 0) {
                    Notifications.finish(this, title, text)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.fusioncli.action.TASK_START"
        const val ACTION_STOP = "com.example.fusioncli.action.TASK_STOP"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
    }
}
