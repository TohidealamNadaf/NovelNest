package com.novelnest.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.window.SplashScreenView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.notification.*
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var notificationOpen by mutableStateOf<NotificationOpen?>(null)
    private var notificationResolution: Job? = null
    private var handledNotificationId: String? = null
    private var splashExitAnimation: AnimatorSet? = null
    private var exitingSplash: SplashScreenViewProvider? = null

    private fun notificationId(intent: Intent): String? = try {
        intent.getStringExtra(EXTRA_NOTIFICATION_ID)?.takeIf { it.isNotBlank() && it.length <= 1024 }
    } catch (error: RuntimeException) {
        Log.w("NovelNestLaunch", "Invalid notification extras: ${error.javaClass.simpleName}")
        null
    }

    private fun acceptNotification(intent: Intent) {
        val id = notificationId(intent) ?: return
        if (id == handledNotificationId) return
        val app = application as NovelNestApplication
        notificationResolution?.cancel()
        notificationOpen = null
        notificationResolution = lifecycleScope.launch {
            val request = withContext(Dispatchers.IO) {
                try {
                    val item = app.notificationStore.items.value.firstOrNull { it.id == id }
                    // Resolve only persisted, trusted inbox data. An expired/deleted
                    // notification opens Home, never a route from arbitrary extras.
                    NotificationOpen(id, item?.novelId?.takeIf {
                        it.length <= 512 && it.matches(Regex("[a-zA-Z0-9_-]+"))
                    })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("NovelNestLaunch", "Notification lookup failed: ${error.javaClass.simpleName}")
                    NotificationOpen(id, null)
                }
            }
            notificationOpen = request
        }
    }

    private fun notificationHandled(request: NotificationOpen) {
        if (notificationOpen != request) return
        notificationOpen = null
        handledNotificationId = request.eventId
        if (notificationId(intent) == request.eventId) {
            intent.removeExtra(EXTRA_NOTIFICATION_ID)
            intent.removeExtra(EXTRA_NOVEL_ID)
        }
        // Inbox persistence must not hold up Activity creation or navigation.
        (application as NovelNestApplication).notificationCoordinator.markRead(request.eventId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A fresh tap is a new action even if it targets a previously read item.
        handledNotificationId = null
        acceptNotification(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as NovelNestApplication).aiForeground.update(true)
        (application as NovelNestApplication).notificationCoordinator.activityChanged(true)
    }

    override fun onStop() {
        finishSplashExit()
        if (!isChangingConfigurations) (application as NovelNestApplication).aiForeground.update(false)
        (application as NovelNestApplication).notificationCoordinator.activityChanged(false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("handled_notification_id", handledNotificationId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        finishSplashExit()
        super.onDestroy()
    }

    private fun finishSplashExit() {
        val animation = splashExitAnimation
        splashExitAnimation = null
        val provider = exitingSplash
        exitingSplash = null
        try {
            animation?.removeAllListeners()
            animation?.cancel()
        } catch (error: RuntimeException) {
            Log.w("NovelNestLaunch", "Splash cancellation failed: ${error.javaClass.simpleName}")
        }
        try {
            provider?.remove()
        } catch (error: RuntimeException) {
            Log.w("NovelNestLaunch", "Splash removal failed: ${error.javaClass.simpleName}")
        }
    }

    private fun animateSplashExit(provider: SplashScreenViewProvider) {
        finishSplashExit()
        exitingSplash = provider
        if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            finishSplashExit()
            return
        }
        try {
            val splashView = provider.view
            // Android 12+ may provide a solid-color splash with no icon. The
            // AndroidX 1.0.1 iconView getter asserts non-null; bypass it on API 31+.
            val iconView = if (Build.VERSION.SDK_INT >= 31) {
                (splashView as? SplashScreenView)?.iconView
            } else provider.iconView
            val animations = mutableListOf<Animator>(
                ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)
            )
            iconView?.let { icon ->
                animations += ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.16f)
                animations += ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.16f)
            }
            val animation = AnimatorSet().apply {
                duration = 420L
                interpolator = AccelerateDecelerateInterpolator()
                playTogether(animations)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { finishSplashExit() }
                    override fun onAnimationCancel(animation: Animator) { finishSplashExit() }
                })
            }
            splashExitAnimation = animation
            animation.start()
        } catch (error: RuntimeException) {
            Log.w("NovelNestLaunch", "Splash exit skipped: ${error.javaClass.simpleName}")
            finishSplashExit()
        }
    }

    private val novelRepository: NovelRepository by lazy {
        (application as NovelNestApplication).novelRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install AndroidX SplashScreen API before calling super.onCreate
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handledNotificationId = savedInstanceState?.getString("handled_notification_id")
        acceptNotification(intent)

        // Apply smooth exit animation directly to the Android system splash screen
        splashScreen.setOnExitAnimationListener(::animateSplashExit)

        val app = application as NovelNestApplication
        setContent {
            NovelNestTheme {
                NovelNestApp(
                    repository = novelRepository,
                    navigationPreferences = app.navigationPreferences,
                    notificationRequest = notificationOpen,
                    onNotificationHandled = ::notificationHandled
                )
            }
        }
    }
}
