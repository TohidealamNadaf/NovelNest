package com.novelnest.app.feature.notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*

@Composable
fun NotificationControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as NovelNestApplication
    val settings by app.notificationStore.settings.collectAsState()
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var awaitingSettings by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (allowed) app.notificationCoordinator.configure(enabled = true)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (awaitingSettings && allowed) {
                    app.notificationCoordinator.configure(enabled = true)
                    awaitingSettings = false
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("System notifications", style = MaterialTheme.typography.titleMedium, color = NovelTextPrimary)
            Text(when {
                !allowed -> "Allow notifications to receive updates outside the app."
                settings.enabled -> "Bookmark confirmations arrive when saved. Chapter checks and reading reminders run in the background."
                else -> "Choose which reading updates you want."
            }, style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary)
            if (!settings.enabled || !allowed) Button(
                onClick = {
                    when {
                        Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                            {
                                val activity = context as? android.app.Activity
                                if (app.notificationStore.permissionRequested &&
                                    (activity == null || !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))) {
                                    awaitingSettings = true
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                } else {
                                    app.notificationStore.permissionRequested = true
                                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        !NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
                            awaitingSettings = true
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                        else -> { allowed = true; app.notificationCoordinator.configure(enabled = true) }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovelPrimary,
                    contentColor = NovelOnPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (!allowed) "Allow notifications" else "Enable notifications",
                    color = NovelOnPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Bookmark confirmations", color = NovelTextPrimary)
                    Text("Notify when a novel is added to your library", style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary)
                }
                Switch(settings.bookmarkConfirmations, { app.notificationCoordinator.configure(bookmarks = it) }, enabled = settings.enabled)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Bookmarked novel updates", color = NovelTextPrimary)
                    Text("Periodic checks for new chapters", style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary)
                }
                Switch(settings.chapterUpdates, { app.notificationCoordinator.configure(chapters = it) }, enabled = settings.enabled)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("A little reading nudge", color = NovelTextPrimary)
                    Text("A doodle and joke after 24 hours away", style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary)
                }
                Switch(settings.reminders, { app.notificationCoordinator.configure(reminders = it) }, enabled = settings.enabled)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Android notification settings",
                    color = NovelPrimary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
                if (settings.enabled) {
                    Text(
                        text = "Turn off notifications",
                        color = NovelPrimary.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .clickable {
                                app.notificationCoordinator.configure(enabled = false)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }
        }
    }
}
