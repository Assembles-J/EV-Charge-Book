package com.evchargebook

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/** Small process-local counter that answers whether at least one Activity is visibly started. */
internal class ActivityVisibilityCounter {
    private val startedActivityCount = AtomicInteger(0)

    val hasVisibleActivity: Boolean
        get() = startedActivityCount.get() > 0

    fun onActivityStarted() {
        startedActivityCount.incrementAndGet()
    }

    fun onActivityStopped() {
        startedActivityCount.updateAndGet { current ->
            if (current > 0) current - 1 else 0
        }
    }
}

/**
 * Activity visibility authority for background Bluetooth execution decisions.
 *
 * Process importance is intentionally not used here: a process executing a BroadcastReceiver can
 * temporarily be treated as important even though no Activity is visible, which would make Android
 * 12+ foreground-service checks overly optimistic.
 */
object AppVisibilityTracker : Application.ActivityLifecycleCallbacks {
    private val counter = ActivityVisibilityCounter()

    val hasVisibleActivity: Boolean
        get() = counter.hasVisibleActivity

    override fun onActivityStarted(activity: Activity) {
        counter.onActivityStarted()
    }

    override fun onActivityStopped(activity: Activity) {
        counter.onActivityStopped()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
