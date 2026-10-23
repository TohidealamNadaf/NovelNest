package com.novelnest.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.novelnest.app.data.notification.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novelnest.app.core.designsystem.NovelNestTheme
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
        // The starting-window theme belongs to the system splash, not the Activity UI.
        setTheme(R.style.Theme_NovelNest)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptNotification(intent)
        val app = application as NovelNestApplication
        setContent {
            NovelNestTheme {
                // Android owns the launch splash. Draw the app's first frame directly,
                // without a second logo overlay or an artificial startup delay.
                NovelNestApp(repository = novelRepository,
                    navigationPreferences = app.navigationPreferences,
                    notificationRequest = notificationOpen,
                    onNotificationHandled = { notificationOpen = null })
            }
        }
    }
}
