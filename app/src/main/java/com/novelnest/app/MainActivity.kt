package com.novelnest.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.notification.*
import com.novelnest.app.data.repository.NovelRepository

class MainActivity : ComponentActivity() {
    private var notificationOpen by mutableStateOf<NotificationOpen?>(null)

    private fun acceptNotification(intent: Intent) {
        val app = application as NovelNestApplication
        val id = intent.getStringExtra(EXTRA_NOTIFICATION_ID) ?: return
        val item = app.notificationStore.items.value.firstOrNull { it.id == id } ?: return
        notificationOpen = NotificationOpen(id, item.novelId?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]+")) })
        app.notificationStore.markRead(id)
        intent.removeExtra(EXTRA_NOTIFICATION_ID)
        intent.removeExtra(EXTRA_NOVEL_ID)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptNotification(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as NovelNestApplication).notificationCoordinator.activityChanged(true)
    }

    override fun onStop() {
        (application as NovelNestApplication).notificationCoordinator.activityChanged(false)
        super.onStop()
    }

    private val novelRepository: NovelRepository by lazy {
        (application as NovelNestApplication).novelRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install AndroidX SplashScreen API before calling super.onCreate
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptNotification(intent)

        // Apply smooth exit animation directly to the Android system splash screen
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashView = splashScreenViewProvider.view
            val iconView = splashScreenViewProvider.iconView

            val scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.16f)
            val scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.16f)
            val fadeOut = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)

            AnimatorSet().apply {
                duration = 420L
                interpolator = AccelerateDecelerateInterpolator()
                playTogether(scaleX, scaleY, fadeOut)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashScreenViewProvider.remove()
                    }
                })
                start()
            }
        }

        val app = application as NovelNestApplication
        setContent {
            NovelNestTheme {
                NovelNestApp(
                    repository = novelRepository,
                    navigationPreferences = app.navigationPreferences,
                    notificationRequest = notificationOpen,
                    onNotificationHandled = { notificationOpen = null }
                )
            }
        }
    }
}
