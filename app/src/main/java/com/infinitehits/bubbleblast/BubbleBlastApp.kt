package com.infinitehits.bubbleblast

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Application entry point: builds [AppServices] once and keeps the music in sync
 * with the app's foreground state.
 */
class BubbleBlastApp : Application() {

    lateinit var services: AppServices
        private set

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        services = AppServices(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacksAdapter() {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1) services.onAppForegroundState(true)
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) services.onAppForegroundState(false)
            }
        })
    }

    override fun onTerminate() {
        services.audio.release()
        super.onTerminate()
    }

    /** Adapter with empty implementations so only the interesting callbacks are overridden. */
    private open class ActivityLifecycleCallbacksAdapter : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
