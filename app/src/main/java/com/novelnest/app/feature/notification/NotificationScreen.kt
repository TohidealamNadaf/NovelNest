package com.novelnest.app.feature.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSecondary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.designsystem.NovelTextTertiary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.core.ui.header.NovelNestHeader

/**
 * Notification model representing updates, alerts, and chapter releases.
 */
data class NotificationItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: String,
    val icon: ImageVector,
    val isUnread: Boolean = true,
    val novelId: String? = null
)

private enum class NotificationFilter {
    ALL,
    UNREAD
}

/**
 * Polished, modern Notification screen.
 * Designed with UI/UX Pro Max principles:
 * - 100% solid, opaque cards with zero visible borders (border = null)
 * - Zero translucent alpha washes or glassmorphism
 * - Cohesive obsidian surfaces (NovelSurface for unread, calm solid dark slate for read)
 * - Solid icon badges and status chips with no borders
 * - Fluid tactile bounce feedback on item clicks
 */
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val playHaptic = rememberNovelNestHaptic()
    var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }

    var notifications by remember {
        mutableStateOf(
            listOf(
                NotificationItem(
                    id = "notif-1",
                    title = "New Chapter Released!",
                    description = "Shadow Slave - Chapter 1285: The Twilight Path is now available on NovelPhoenix.",
                    timestamp = "15m ago",
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    isUnread = true,
                    novelId = "novelphoenix-shadow-slave"
                ),
                NotificationItem(
                    id = "notif-2",
                    title = "New Chapter Released!",
                    description = "Lord of the Mysteries - Chapter 820: King of Yellow and Black is updated.",
                    timestamp = "1h ago",
                    icon = Icons.Rounded.AutoStories,
                    isUnread = true,
                    novelId = "novelphoenix-lord-of-the-mysteries"
                ),
                NotificationItem(
                    id = "notif-3",
                    title = "Offline Cache Ready",
                    description = "Offline reader cache synchronized successfully for offline reading.",
                    timestamp = "3h ago",
                    icon = Icons.Rounded.DoneAll,
                    isUnread = false
                ),
                NotificationItem(
                    id = "notif-4",
                    title = "Source Updated",
                    description = "Asura Scans crawler refreshed latest manhwa releases and episode indexes.",
                    timestamp = "Yesterday",
                    icon = Icons.Rounded.SystemUpdate,
                    isUnread = false
                )
            )
        )
    }

    val displayedNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            NotificationFilter.ALL -> notifications
            NotificationFilter.UNREAD -> notifications.filter { it.isUnread }
        }
    }

    val unreadCount = remember(notifications) { notifications.count { it.isUnread } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        NovelNestHeader(
            title = "Notifications",
            subtitle = if (unreadCount > 0) "$unreadCount unread updates" else "All caught up",
            navigationIcon = {
                NovelNestIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
            },
            actions = {
                if (unreadCount > 0) {
                    TextButton(
                        onClick = {
                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                            notifications = notifications.map { it.copy(isUnread = false) }
                        }
                    ) {
                        Text(
                            text = "Mark all read",
                            color = NovelPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        )

        // Solid, borderless filter pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotificationFilterChip(
                label = "All (${notifications.size})",
                isSelected = selectedFilter == NotificationFilter.ALL,
                onClick = {
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    selectedFilter = NotificationFilter.ALL
                }
            )

            NotificationFilterChip(
                label = "Unread ($unreadCount)",
                isSelected = selectedFilter == NotificationFilter.UNREAD,
                onClick = {
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    selectedFilter = NotificationFilter.UNREAD
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (displayedNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(NovelSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selectedFilter == NotificationFilter.UNREAD) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.NotificationsNone
                            },
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (selectedFilter == NotificationFilter.UNREAD) {
                            "No unread notifications"
                        } else {
                            "No notifications yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NovelTextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (selectedFilter == NotificationFilter.UNREAD) {
                            "You have caught up with all latest chapter releases."
                        } else {
                            "New chapters and updates will appear right here."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = NovelTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedNotifications, key = { it.id }) { item ->
                    NotificationCard(
                        item = item,
                        onClick = {
                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                            // Mark item as read
                            notifications = notifications.map {
                                if (it.id == item.id) it.copy(isUnread = false) else it
                            }
                            if (item.novelId != null) {
                                onNovelClick(item.novelId)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Solid, borderless card for a notification item.
 */
@Composable
private fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)

    // Solid container backgrounds: 100% opaque, zero transparency bleed
    val containerColor = if (item.isUnread) NovelSurface else Color(0xFF14181E)

    // Solid icon background: Warm solid tint for unread, calm slate for read
    val iconBadgeColor = if (item.isUnread) Color(0xFF262017) else NovelSurfaceVariant
    val iconTint = if (item.isUnread) NovelPrimary else NovelTextSecondary

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = cardShape,
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .bounceClick(
                scaleDown = BounceScale.Medium,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Solid, borderless icon badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBadgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NovelTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (item.isUnread) FontWeight.Bold else FontWeight.SemiBold
                    )

                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovelTextTertiary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovelTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            if (item.isUnread) {
                Spacer(modifier = Modifier.width(10.dp))
                // Solid, vibrant unread indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NovelPrimary)
                )
            }
        }
    }
}

/**
 * Solid, borderless filter chip for switching between All and Unread notifications.
 */
@Composable
private fun NotificationFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipShape = RoundedCornerShape(20.dp)
    val backgroundColor = if (isSelected) NovelPrimary else NovelSurface
    val contentColor = if (isSelected) NovelBackground else NovelTextSecondary

    Box(
        modifier = modifier
            .clip(chipShape)
            .background(backgroundColor)
            .bounceClick(
                scaleDown = BounceScale.Small,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
