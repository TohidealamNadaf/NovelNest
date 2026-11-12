package com.novelnest.app.core.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.Collections
import java.util.IdentityHashMap

/** Reminders follow the entire application, including the isolated EPUB reader activity. */
class AppActivityTracker(private val onForegroundChanged: (Boolean) -> Unit) : Application.ActivityLifecycleCallbacks {
    private val started = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
    override fun onActivityStarted(activity: Activity) {
        val wasEmpty = started.isEmpty()
        started.add(activity)
        if (wasEmpty && started.isNotEmpty()) onForegroundChanged(true)
    }
    override fun onActivityStopped(activity: Activity) = remove(activity)
    override fun onActivityDestroyed(activity: Activity) = remove(activity)
    private fun remove(activity: Activity) {
        if (started.remove(activity) && started.isEmpty()) onForegroundChanged(false)
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
