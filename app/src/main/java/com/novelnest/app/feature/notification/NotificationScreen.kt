package com.novelnest.app.feature.notification

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.header.NovelNestHeader

@Composable
fun NotificationScreen(onBack: () -> Unit, onNovelClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as NovelNestApplication
    val notifications by app.notificationStore.items.collectAsState()
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    val unread = notifications.count { !it.read }
    val displayed = if (unreadOnly) notifications.filterNot { it.read } else notifications
    Column(modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(title = "Notifications", subtitle = "$unread unread",
            navigationIcon = { NovelNestIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack) },
            actions = { if (unread > 0) TextButton(onClick = app.notificationStore::markAllRead) { Text("Mark all read") } })
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp).let { PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp) },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "controls") { NotificationControls() }
            item(key = "filter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!unreadOnly, { unreadOnly = false }, label = { Text("All (${notifications.size})") })
                    FilterChip(unreadOnly, { unreadOnly = true }, label = { Text("Unread ($unread)") })
                }
            }
            if (displayed.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.NotificationsNone, null, tint = NovelPrimary)
                    Text(if (unreadOnly) "All caught up" else "No notifications yet",
                        style = MaterialTheme.typography.titleMedium, color = NovelTextPrimary)
                    Text("New bookmark updates and reading reminders will appear here.", color = NovelTextSecondary)
                }
            }
            items(displayed, key = { it.id }) { item ->
                Card(onClick = {
                    app.notificationStore.markRead(item.id)
                    item.novelId?.let(onNovelClick)
                }, colors = CardDefaults.cardColors(containerColor = if (item.read) NovelSurfaceVariant else NovelSurface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.AutoStories, null, tint = NovelPrimary)
                            Text(item.title, style = MaterialTheme.typography.titleMedium, color = NovelTextPrimary)
                        }
                        Text(item.message, style = MaterialTheme.typography.bodyMedium, color = NovelTextSecondary)
                        Text(DateUtils.getRelativeTimeSpanString(item.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = MaterialTheme.typography.labelSmall, color = NovelTextSecondary)
                    }
                }
            }
        }
    }
}
