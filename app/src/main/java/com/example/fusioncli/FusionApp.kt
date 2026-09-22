package com.example.fusioncli

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.fusioncli.data.AgentSession
import com.example.fusioncli.data.InstallManager
import com.example.fusioncli.data.WorkspaceRepository
import com.example.fusioncli.service.Notifications

/**
 * Application entry point. It wires the application-scoped sessions (so work survives leaving a
 * screen), creates the notification channel, initializes the workspace storage and tracks whether
 * the app is on screen (used to decide when a completion notification is worth showing).
 */
class FusionApp : Application(), Application.ActivityLifecycleCallbacks {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        WorkspaceRepository.getInstance(this)
        AgentSession.init(this)
        InstallManager.init(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        AgentSession.isAppForeground = true
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        AgentSession.isAppForeground = startedActivities > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
