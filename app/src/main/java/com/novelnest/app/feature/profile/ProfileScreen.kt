package com.novelnest.app.feature.profile

import com.novelnest.app.core.ui.footer.rememberPageBottomPadding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.profile.readingStreak
import com.novelnest.app.feature.notification.NotificationControls
import java.time.LocalDate

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onNotifications: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NovelNestApplication
    val profile by app.profileManager.profile.collectAsState()
    val progress by app.readingProgressManager.progressMap.collectAsState()
    val bookmarks by app.bookmarkManager.bookmarks.collectAsState()

    var nicknameInput by rememberSaveable { mutableStateOf(profile.nickname) }
    var isEditingName by rememberSaveable { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val streak = readingStreak(profile.readingDays)
    val chapters = remember(progress) {
        progress.values.sumOf { record ->
            record.readChapterIds.map {
                it.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
            }.distinct().size
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val old = profile.photoUri
                app.profileManager.savePhoto(uri.toString())
                if (old != null && old != uri.toString()) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(old),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                statusMessage = "Photo updated"
            } catch (_: Exception) {
                statusMessage = "Could not retain photo access. Please choose another."
            }
        }
    }

    val readerTier = remember(streak.current, streak.days, chapters) {
        when {
            streak.current >= 30 || chapters >= 500 -> "Immortal Sovereign ✦"
            streak.current >= 14 || chapters >= 200 -> "Grand Scholar ✦"
            streak.current >= 7 || chapters >= 50 -> "Devoted Reader ✦"
            streak.days > 0 || chapters > 0 -> "Avid Explorer ✦"
            else -> "Apprentice Reader ✦"
        }
    }

    val todayEpoch = remember { LocalDate.now().toEpochDay() }

    Column(Modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(
            title = "Reader Profile",
            subtitle = "Personal Sanctuary",
            navigationIcon = {
                NovelNestIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            },
            actions = {
                NovelNestIconButton(Icons.Rounded.Settings, "Settings", onSettings)
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = rememberPageBottomPadding(28.dp)
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Identity & Avatar Card
            item(key = "identity") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = NovelSurface,
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar with Aura Border & Camera Badge
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .border(
                                        width = 2.5.dp,
                                        brush = Brush.sweepGradient(
                                            listOf(NovelPrimary, NovelSecondary, NovelPrimaryVariant, NovelPrimary)
                                        ),
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(NovelSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profile.photoUri != null) {
                                    AsyncImage(
                                        model = profile.photoUri,
                                        contentDescription = "Profile picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = NovelPrimary,
                                        modifier = Modifier.size(50.dp)
                                    )
                                }
                            }

                            // Camera Edit Badge Button
                            Surface(
                                shape = CircleShape,
                                color = NovelPrimary,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable { photoPicker.launch(arrayOf("image/*")) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoCamera,
                                        contentDescription = "Change photo",
                                        tint = NovelOnPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Remove Photo Action if photo exists
                        if (profile.photoUri != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val old = profile.photoUri
                                        app.profileManager.savePhoto(null)
                                        old?.let {
                                            runCatching {
                                                context.contentResolver.releasePersistableUriPermission(
                                                    Uri.parse(it),
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                )
                                            }
                                        }
                                        statusMessage = "Photo removed"
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = NovelTextTertiary, modifier = Modifier.size(14.dp))
                                Text("Remove photo", style = MaterialTheme.typography.labelSmall, color = NovelTextTertiary)
                            }
                        }

                        // Reader Tier Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NovelBadgeBackground,
                            border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = readerTier,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = NovelPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        // Nickname Display or Edit Field
                        AnimatedVisibility(
                            visible = !isEditingName,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = profile.nickname,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = NovelTextPrimary
                                )
                                IconButton(
                                    onClick = {
                                        nicknameInput = profile.nickname
                                        isEditingName = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit nickname",
                                        tint = NovelTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isEditingName,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OutlinedTextField(
                                    value = nicknameInput,
                                    onValueChange = { nicknameInput = it.take(40) },
                                    label = { Text("Reader Nickname") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    supportingText = { Text("${nicknameInput.length}/40 · Stored locally") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NovelPrimary,
                                        unfocusedBorderColor = NovelSurfaceBorder,
                                        focusedLabelColor = NovelPrimary,
                                        unfocusedLabelColor = NovelTextSecondary,
                                        focusedTextColor = NovelTextPrimary,
                                        unfocusedTextColor = NovelTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { isEditingName = false },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel", color = NovelTextSecondary)
                                    }
                                    Button(
                                        onClick = {
                                            app.profileManager.saveNickname(nicknameInput)
                                            isEditingName = false
                                            statusMessage = "Profile updated"
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NovelPrimary,
                                            contentColor = NovelOnPrimary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Save", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Feedback Status Message
                        statusMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 2. Reading Streak Showcase Card (with 7-Day Activity Pulse)
            item(key = "streak") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NovelSurface,
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(NovelBadgeBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = NovelPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        "Reading Streak",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = NovelTextPrimary
                                    )
                                    Text(
                                        "Consistent daily reading",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovelTextSecondary
                                    )
                                }
                            }

                            // Big Streak Number Pill
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = NovelSurfaceVariant,
                                border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${streak.current}",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = NovelPrimary
                                    )
                                    Text(
                                        text = if (streak.current == 1) "day" else "days",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = NovelTextSecondary
                                    )
                                }
                            }
                        }

                        // 7-Day Activity Pulse Tracker (Last 7 Days)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Last 7 Days Activity",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = NovelTextTertiary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
                                for (i in 6 downTo 0) {
                                    val dayEpoch = todayEpoch - i
                                    val date = LocalDate.ofEpochDay(dayEpoch)
                                    val dayName = dayLetters[date.dayOfWeek.value - 1]
                                    val hasRead = dayEpoch in profile.readingDays
                                    val isToday = dayEpoch == todayEpoch

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = dayName,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isToday) NovelPrimary else NovelTextSecondary
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        hasRead -> NovelPrimary
                                                        isToday -> NovelSurfaceVariant
                                                        else -> NovelSurfaceVariant.copy(alpha = 0.5f)
                                                    }
                                                )
                                                .border(
                                                    width = if (isToday && !hasRead) 1.5.dp else 0.dp,
                                                    color = if (isToday && !hasRead) NovelPrimary else Color.Transparent,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (hasRead) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = NovelOnPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "${date.dayOfMonth}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isToday) NovelPrimary else NovelTextTertiary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Streak Best & Total Record
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NovelSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Personal Best: ${streak.best} ${if (streak.best == 1) "day" else "days"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NovelTextSecondary
                                )
                                Text(
                                    text = "${streak.days} total reading days",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = NovelTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bento Grid: Reading Journey & Activity
            item(key = "stats") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Reading Statistics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NovelTextPrimary,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileBentoCard(
                            title = "Chapters Read",
                            value = chapters.toString(),
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            accentColor = NovelPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileBentoCard(
                            title = "Novels Explored",
                            value = progress.values.count { it.readChapterIds.isNotEmpty() }.toString(),
                            icon = Icons.Rounded.AutoStories,
                            accentColor = NovelSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileBentoCard(
                            title = "In Library",
                            value = bookmarks.size.toString(),
                            icon = Icons.Rounded.Bookmark,
                            accentColor = NovelTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileBentoCard(
                            title = "Active Days",
                            value = streak.days.toString(),
                            icon = Icons.Rounded.DateRange,
                            accentColor = NovelGold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Notifications Card
            item(key = "notifications") {
                NotificationControls()
            }

            // 5. Notification History Action Tile
            item(key = "history") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = NovelSurface,
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onNotifications)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NovelBadgeBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Notification History",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = NovelTextPrimary
                            )
                            Text(
                                "View past chapter releases and alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                            contentDescription = "Open history",
                            tint = NovelTextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 6. Privacy & App Meta Footnote
            item(key = "footer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "NovelNest · Offline First & Private · Stored on Device",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovelTextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileBentoCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = NovelSurface,
        border = BorderStroke(1.dp, NovelSurfaceBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = NovelTextPrimary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovelTextSecondary
                )
            }
        }
    }
}
