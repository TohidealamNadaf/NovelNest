package com.novelnest.app.core.lifecycle

import android.app.Activity
import android.app.Application
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.novelnest.app.R
import com.novelnest.app.data.security.AppAccess
import com.novelnest.app.data.security.AppLockManager
import com.novelnest.app.data.narration.NarrationService
import com.novelnest.app.feature.lock.AppLockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.IdentityHashMap

/** Device-session boundary for Main, EPUB, dictionary and future app Activities. */
class AppLockLifecycle(private val app: Application, private val lock: AppLockManager) :
    Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
    private val covers = IdentityHashMap<Activity, View>()
    private val ownedSecure = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
    private var launching = false
    private val tasks = AppLockSessionTasks()
    private var replacementTaskId: Int? = null
    private val power get() = app.getSystemService(PowerManager::class.java)
    private val keyguard get() = app.getSystemService(KeyguardManager::class.java)
    private val deviceEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_USER_PRESENT) {
                lock.lock()
            }
        }
    }

    /** Recheck security before an app-opened picker; ordinary app switches need no exemption. */
    fun beforeExternalLaunch() = checkDeviceSecurity()

    /** Only called by the authenticated gate immediately before replacing the app task. */
    fun beginAuthenticatedTaskReplacement(taskId: Int) { replacementTaskId = taskId.takeIf { it >= 0 } }
    fun cancelAuthenticatedTaskReplacement() { replacementTaskId = null }

    private fun checkDeviceSecurity() {
        if (!power.isInteractive || keyguard.isDeviceLocked || keyguard.isKeyguardLocked) lock.lock()
    }

    @Suppress("DEPRECATION") // RecentTaskInfo.persistentId also survives Activity destruction on API 26-28.
    private fun checkRemainingTasks() {
        try {
            val remaining = app.getSystemService(ActivityManager::class.java).appTasks
                .map { task ->
                    val info = task.taskInfo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.taskId else info.persistentId
                }.toSet()
            if (tasks.onTasksChanged(remaining)) lock.lock()
        } catch (error: RuntimeException) {
            // An unreadable task list must never authorize a session in a replacement task.
            lock.lock()
            android.util.Log.w("NovelNestLock", "Could not verify app tasks", error)
        }
    }

    init {
        // Application-owned for the process lifetime, including while another app is in front.
        // Both actions are protected system broadcasts. No service, timer, or wake lock is needed.
        ContextCompat.registerReceiver(app, deviceEvents, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            lock.state.collect { state ->
                for (activity in (started + covers.keys + ownedSecure).toSet()) {
                    secure(activity, !state.ready || state.enabled)
                    if (state.blocked) cover(activity) else uncover(activity)
                }
                if (state.blocked) started.lastOrNull { activity ->
                    activity !is AppLockActivity && (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle
                        ?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
                }?.let(::requireUnlock)
            }
        }
        scope.launch {
            lock.state.map { it.access }.distinctUntilChanged().collect { access ->
                if (access == AppAccess.EPUB_ONLY) NarrationService.command(app, NarrationService.STOP)
            }
        }
    }

    private fun secure(activity: Activity, required: Boolean) {
        if (required) {
            if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                ownedSecure.add(activity)
            }
        } else if (ownedSecure.remove(activity)) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun cover(activity: Activity) {
        if (activity is AppLockActivity || activity.isDestroyed) return
        val decor = activity.window.decorView as? ViewGroup ?: return
        val view = covers[activity] ?: View(activity).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Novel Nest is locked"
        }.also {
            covers[activity] = it
            decor.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        // Keep the privacy cover opaque and consistent with the gate while it starts.
        // An unresolved cold launch keeps the normal launcher color until lock storage loads.
        val state = lock.state.value
        val background = if (state.ready && state.enabled) R.drawable.app_lock_background
            else R.color.novelnest_launcher_background
        if (view.tag != background) {
            view.setBackgroundResource(background)
            view.tag = background
        }
    }
    private fun uncover(activity: Activity) { covers.remove(activity)?.let { (it.parent as? ViewGroup)?.removeView(it) } }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (tasks.onTaskStarted(activity.taskId, authenticatedReplacementTaskId = replacementTaskId)) lock.lock()
        replacementTaskId = null
        if (activity is AppLockActivity) launching = false
        secure(activity, !lock.state.value.ready || lock.state.value.enabled)
    }
    override fun onActivityStarted(activity: Activity) {
        checkDeviceSecurity()
        started.add(activity)
        secure(activity, !lock.state.value.ready || lock.state.value.enabled)
        if (lock.state.value.blocked) cover(activity) else uncover(activity)
    }
    override fun onActivityResumed(activity: Activity) {
        // Android can reuse/reorder the gate without onCreate, or supersede a
        // pending launch with a new launcher/notification intent. A real resume
        // is a new foreground opportunity; never retain an old launch latch.
        launching = false
        checkDeviceSecurity()
        requireUnlock(activity)
    }
    private fun requireUnlock(activity: Activity) {
        if (activity !is AppLockActivity && lock.state.value.blocked && !launching && !activity.isFinishing) {
            cover(activity)
            launching = true
            try {
                activity.startActivity(Intent(activity, AppLockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } catch (error: RuntimeException) {
                launching = false
                android.util.Log.e("NovelNestLock", "Could not display app lock", error)
            }
        }
    }
    override fun onActivityStopped(activity: Activity) {
        if (started.remove(activity) && started.isEmpty() && !activity.isChangingConfigurations &&
            !(activity is AppLockActivity && activity.replacingTask)) {
            // Cancel stale authentication results without revoking an already granted session.
            lock.onAppBackgrounded()
            checkDeviceSecurity()
        }
    }
    override fun onActivityDestroyed(activity: Activity) {
        started.remove(activity)
        uncover(activity)
        ownedSecure.remove(activity)
        if (activity is AppLockActivity) launching = false
        if (activity.isFinishing && !activity.isChangingConfigurations && replacementTaskId == null &&
            !(activity is AppLockActivity && activity.replacingTask)) checkRemainingTasks()
    }
    override fun onActivityPaused(activity: Activity) { checkDeviceSecurity() }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}