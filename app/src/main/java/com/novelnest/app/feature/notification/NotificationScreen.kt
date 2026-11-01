package com.novelnest.app.feature.notification

import com.novelnest.app.core.ui.footer.rememberPageBottomPadding

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.header.NovelNestHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as NovelNestApplication
    val notifications by app.notificationStore.items.collectAsState()
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    val unread = remember(notifications) { notifications.count { !it.read } }
    val displayed = remember(notifications, unreadOnly) {
        if (unreadOnly) notifications.filterNot { it.read } else notifications
    }

    Column(modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(
            title = "Notifications",
            subtitle = if (unread > 0) "$unread unread updates" else "Inbox up to date",
            navigationIcon = {
                NovelNestIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            },
            actions = {
                if (unread > 0) {
                    TextButton(onClick = app.notificationStore::markAllRead) {
                        Text(
                            "Mark all read",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NovelPrimary
                        )
                    }
                }
                if (notifications.isNotEmpty()) {
                    NovelNestIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear all notifications",
                        tint = NovelTextTertiary,
                        onClick = app.notificationStore::clearAll
                    )
                }
                NovelNestIconButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = "Notification Settings",
                    tint = NovelPrimary,
                    onClick = { showSettingsSheet = true }
                )
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = rememberPageBottomPadding(24.dp)
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Filter Pills: All vs Unread (No notification settings here - as requested)
            item(key = "filters") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // All Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (!unreadOnly) NovelPrimary else NovelSurfaceVariant,
                        border = if (!unreadOnly) null else BorderStroke(1.dp, NovelSurfaceBorder.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { unreadOnly = false }
                    ) {
                        Text(
                            text = "All (${notifications.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (!unreadOnly) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (!unreadOnly) NovelOnPrimary else NovelTextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }

                    // Unread Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (unreadOnly) NovelPrimary else NovelSurfaceVariant,
                        border = if (unreadOnly) null else BorderStroke(1.dp, NovelSurfaceBorder.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { unreadOnly = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            if (unread > 0 && !unreadOnly) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NovelPrimary)
                                )
                            }
                            Text(
                                text = "Unread ($unread)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (unreadOnly) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (unreadOnly) NovelOnPrimary else NovelTextSecondary
                            )
                        }
                    }
                }
            }

            // Empty State
            if (displayed.isEmpty()) {
                item(key = "empty") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = NovelSurface,
                        border = BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(NovelSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (unreadOnly) Icons.Rounded.Check else Icons.Rounded.NotificationsNone,
                                    contentDescription = null,
                                    tint = NovelPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = if (unreadOnly) "All Caught Up" else "No Notifications Yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = NovelTextPrimary
                            )
                            Text(
                                text = if (unreadOnly)
                                    "You have read all recent chapter updates and alerts."
                                else
                                    "Bookmark confirmations, new-chapter updates and reading reminders will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            if (unreadOnly && notifications.isNotEmpty()) {
                                Button(
                                    onClick = { unreadOnly = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NovelPrimary,
                                        contentColor = NovelOnPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Show All Notifications",
                                        color = NovelOnPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notification Item Cards
            items(displayed, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (item.read) NovelSurfaceVariant.copy(alpha = 0.5f) else NovelSurface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (item.read) NovelSurfaceBorder.copy(alpha = 0.45f) else NovelPrimary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            app.notificationStore.markRead(item.id)
                            item.novelId?.let(onNovelClick)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Category Icon Badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (item.read) NovelSurfaceVariant else NovelBadgeBackground
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.reminder) Icons.Rounded.Schedule else Icons.Rounded.AutoStories,
                                contentDescription = null,
                                tint = if (item.read) NovelTextTertiary else NovelPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Notification Details
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (item.read) FontWeight.Medium else FontWeight.Bold
                                        ),
                                        color = if (item.read) NovelTextSecondary else NovelTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!item.read) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(NovelPrimary)
                                        )
                                    }
                                }

                                Text(
                                    text = DateUtils.getRelativeTimeSpanString(
                                        item.createdAt,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    ).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NovelTextTertiary,
                                    fontSize = 11.sp
                                )
                            }

                            Text(
                                text = item.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.read) NovelTextTertiary else NovelTextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (item.novelId != null) {
                                Text(
                                    text = "Tap to read ➔",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = NovelPrimary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        // Delete Individual Notification Button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { app.notificationStore.delete(item.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Delete notification",
                                tint = NovelTextTertiary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = NovelSurface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = { BottomSheetDefaults.DragHandle(color = NovelSurfaceBorder) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
                ) {
                    NotificationControls(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
