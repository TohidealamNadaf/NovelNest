package com.novelnest.app.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.profile.readingStreak
import com.novelnest.app.feature.notification.NotificationControls

@Composable
fun ProfileScreen(onBack: () -> Unit, onSettings: () -> Unit, onNotifications: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NovelNestApplication
    val profile by app.profileManager.profile.collectAsState()
    val progress by app.readingProgressManager.progressMap.collectAsState()
    val bookmarks by app.bookmarkManager.bookmarks.collectAsState()
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var message by remember { mutableStateOf<String?>(null) }
    val streak = readingStreak(profile.readingDays)
    val chapters = remember(progress) { progress.values.sumOf { record ->
        record.readChapterIds.map { it.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/') }.distinct().size
    } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val old = profile.photoUri
                app.profileManager.savePhoto(uri.toString())
                if (old != null && old != uri.toString()) runCatching {
                    context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                message = null
            } catch (_: Exception) { message = "Could not keep access to that photo. Please choose another." }
        }
    }
    Column(Modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(title = "Profile",
            navigationIcon = { NovelNestIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack) },
            actions = { NovelNestIconButton(Icons.Rounded.Settings, "Settings", onSettings) })
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "identity") {
                Card(colors = CardDefaults.cardColors(containerColor = NovelSurface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(100.dp).clip(CircleShape).background(NovelSurfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Person, null, tint = NovelPrimary, modifier = Modifier.size(46.dp))
                            profile.photoUri?.let { uri ->
                                AsyncImage(uri, "Profile picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        Row {
                            TextButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text("Choose photo") }
                            if (profile.photoUri != null) TextButton(onClick = {
                                val old = profile.photoUri
                                app.profileManager.savePhoto(null)
                                old?.let { runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                            }) { Text("Remove") }
                        }
                        OutlinedTextField(nickname, { nickname = it.take(40) }, label = { Text("Name or nickname") },
                            singleLine = true, supportingText = { Text("${nickname.length}/40 · Stored on this device") },
                            modifier = Modifier.fillMaxWidth())
                        Button(onClick = { app.profileManager.saveNickname(nickname); nickname = app.profileManager.profile.value.nickname; message = "Profile saved" }) {
                            Text("Save name")
                        }
                        message?.let { Text(it, color = NovelTextSecondary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item(key = "streak") {
                Card(colors = CardDefaults.cardColors(containerColor = NovelSurface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Your reading streak", style = MaterialTheme.typography.titleLarge, color = NovelTextPrimary)
                        Text("${streak.current} ${if (streak.current == 1) "day" else "days"}", style = MaterialTheme.typography.headlineLarge, color = NovelPrimary)
                        Text("Best: ${streak.best} days · ${streak.days} reading days", color = NovelTextSecondary)
                        Text("Open and read a chapter each day to keep your streak going.", style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary)
                    }
                }
            }
            item(key = "stats") {
                Card(colors = CardDefaults.cardColors(containerColor = NovelSurface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Reading activity", style = MaterialTheme.typography.titleLarge, color = NovelTextPrimary)
                        ProfileStat("Chapters read", chapters.toString())
                        ProfileStat("Novels read", progress.values.count { it.readChapterIds.isNotEmpty() }.toString())
                        ProfileStat("Bookmarked novels", bookmarks.size.toString())
                    }
                }
            }
            item(key = "notifications") { NotificationControls() }
            item(key = "history") { OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) { Text("Notification history") } }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = NovelTextSecondary)
        Text(value, color = NovelTextPrimary, style = MaterialTheme.typography.titleMedium)
    }
}
