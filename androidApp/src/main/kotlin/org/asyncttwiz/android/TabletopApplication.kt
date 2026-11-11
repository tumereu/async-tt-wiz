package org.asyncttwiz.android

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import org.asyncttwiz.appui.AppPlatform
import java.io.File

/** Open only for Robolectric's TestTabletopApplication (test sources). */
open class TabletopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The shared UI's platform services (docs/specs/web-client.md §1 item 2), before anything that uses them.
        AppPlatform.install(AndroidAppPlatform(this))
        AppServices.install(AndroidAppServices(this))
        AppLog.install(File(filesDir, "app-log.txt"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            // Logging must never replace Android's crash handling.
            try { AppLog.error("crash", "Uncaught exception on thread ${thread.name}", failure) } catch (_: Throwable) {
            } finally { previous?.uncaughtException(thread, failure) }
        }
        AppLog.info("app", "Started. ${appVersionText()}; ${Build.MANUFACTURER} ${Build.MODEL}; API ${Build.VERSION.SDK_INT}")
        AutomatumReports.install()
        Diagnostics.start(AndroidReportHooks(this))
    }
}

/** Reports' platform side on Android ([ReportHooks]): kept in the app's files; the activities' lifecycle tells
 * [DebugLatency] when the app goes to the background, and JankStats gives [DebugJank] each activity's frames. */
internal class AndroidReportHooks(private val application: Application) : ReportHooks {
    override val directory: File get() = File(application.filesDir, "debug-crashes")

    override fun start() {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityStopped(activity: Activity) {
                if (--started == 0 && !activity.isChangingConfigurations) DebugLatency.appBackgrounded()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activity.window.decorView.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    val holder = PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView)
                    val stats = JankStats.createAndTrack(activity.window) { frame ->
                        if (!Diagnostics.enabled) return@createAndTrack
                        DebugJank.frame(frame.frameStartNanos, frame.frameDurationUiNanos, frame.isJank,
                            activity.window.decorView.display?.refreshRate ?: 60f,
                            frame.states.take(16).associate { it.key to it.value })
                    }.apply { jankHeuristicMultiplier = 1f }
                    DebugJank.track(activity, JankTracking({ stats.isTrackingEnabled = false },
                        { key, value -> holder.state?.putState(key, value) }))
                }
            }
            override fun onActivityPaused(activity: Activity) { DebugJank.untrack(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = onActivityPaused(activity)
        })
    }
}
