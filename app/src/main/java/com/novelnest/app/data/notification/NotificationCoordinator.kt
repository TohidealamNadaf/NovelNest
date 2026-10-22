package com.novelnest.app.data.notification

import android.app.*
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.novelnest.app.MainActivity
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.R
import com.novelnest.app.data.model.NovelIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit

const val CHAPTER_JOB = 7101
const val REMINDER_JOB = 7102
const val EXTRA_NOTIFICATION_ID = "notification_id"
const val EXTRA_NOVEL_ID = "notification_novel_id"
data class NotificationOpen(val eventId: String, val novelId: String?)

class NotificationCoordinator(private val app: NovelNestApplication) {
    private val store get() = app.notificationStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var foreground = false
    private val scheduler get() = app.getSystemService(JobScheduler::class.java)

    fun initialize() {
        val system = app.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(NotificationChannel("bookmarks", "Bookmark confirmations", NotificationManager.IMPORTANCE_DEFAULT))
        system.createNotificationChannel(NotificationChannel("chapters", "Bookmarked novel updates", NotificationManager.IMPORTANCE_DEFAULT))
        system.createNotificationChannel(NotificationChannel("reading_reminders", "Reading reminders", NotificationManager.IMPORTANCE_DEFAULT))
        scope.launch {
            combine(store.settings, app.bookmarkManager.bookmarks) { settings, bookmarks -> settings to bookmarks }
                .collect { (_, bookmarks) ->
                    store.reconcile(bookmarks.associate { NovelIdentity.normalize(it).id to it.chapterMarker() })
                    schedule()
                }
        }
    }
    fun activityChanged(active: Boolean) {
        foreground = active
        store.recordActive(System.currentTimeMillis())
        scheduleReminder()
    }
    fun configure(enabled: Boolean = store.settings.value.enabled,
        chapters: Boolean = store.settings.value.chapterUpdates, reminders: Boolean = store.settings.value.reminders,
        bookmarks: Boolean = store.settings.value.bookmarkConfirmations) {
        store.configure(enabled, chapters, reminders, bookmarks)
    }
    fun bookmarkAdded(novel: com.novelnest.app.data.model.Novel) {
        val item = bookmarkConfirmation(novel)
        if (!store.settings.value.allows(item)) return
        scope.launch { publish(item) }
    }

    fun schedule() {
        val settings = store.settings.value
        if (settings.enabled && settings.chapterUpdates && app.bookmarkManager.bookmarks.value.isNotEmpty()) {
            if (scheduler.getPendingJob(CHAPTER_JOB) == null) {
                scheduler.schedule(JobInfo.Builder(CHAPTER_JOB, ComponentName(app, ReaderNotificationJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(TimeUnit.HOURS.toMillis(2), TimeUnit.MINUTES.toMillis(30))
                    .setPersisted(true).build())
            }
        } else scheduler.cancel(CHAPTER_JOB)
        scheduleReminder()
    }
    private fun scheduleReminder() {
        val settings = store.settings.value
        if (!settings.enabled || !settings.reminders || store.lastActive == 0L || store.remindedFor == store.lastActive) {
            scheduler.cancel(REMINDER_JOB)
            return
        }
        val delay = (store.lastActive + INACTIVITY_MILLIS - System.currentTimeMillis()).coerceAtLeast(60_000)
        scheduler.schedule(JobInfo.Builder(REMINDER_JOB, ComponentName(app, ReaderNotificationJob::class.java))
            .setMinimumLatency(delay).setPersisted(true).build())
    }

    suspend fun checkBookmarkedUpdates() {
        BookmarkUpdateChecker(app.novelRepository, app.bookmarkManager, store, { publish(it) }).check()
    }

    fun sendInactivityReminder() {
        val now = System.currentTimeMillis()
        if (!store.settings.value.let { it.enabled && it.reminders }) return
        if (foreground) { store.recordActive(now); scheduleReminder(); return }
        val activity = store.lastActive
        if (!reminderDue(now, activity, store.remindedFor, foreground)) { scheduleReminder(); return }
        val index = ((now / INACTIVITY_MILLIS) % readingNudges.size).toInt()
        val delivered = publish(AppNotification("reminder:$activity", "A little plot twist for your day",
            readingNudges[index], now, reminder = true))
        if (delivered) store.recordReminder(activity)
    }

    private fun publish(item: AppNotification): Boolean {
        val settings = store.settings.value
        if (!settings.allows(item)) return false
        if (item.reminder && !reminderDue(System.currentTimeMillis(), store.lastActive, store.remindedFor, foreground)) return false
        if (item.novelId != null && !app.bookmarkManager.isBookmarked(item.novelId)) return false
        store.add(item)
        val channel = when { item.reminder -> "reading_reminders"; item.bookmark -> "bookmarks"; else -> "chapters" }
        val system = app.getSystemService(NotificationManager::class.java)
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled() ||
            system.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) {
            android.util.Log.w("Notifications", "System delivery disabled for channel: " + channel)
            return false
        }
        val intent = Intent(app, MainActivity::class.java)
            .setData(Uri.parse("novelnest://notification/${Uri.encode(item.id)}"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_NOTIFICATION_ID, item.id).putExtra(EXTRA_NOVEL_ID, item.novelId)
        val pending = PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_notification_book).setContentTitle(item.title).setContentText(item.message)
            .setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(pending).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
        if (item.reminder) builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bookDoodle()).setSummaryText(item.message))
        return try {
            NotificationManagerCompat.from(app).notify(item.id, 0, builder.build())
            true
        } catch (error: SecurityException) {
            android.util.Log.w("Notifications", "System notification permission rejected", error)
            false
        }
    }

    private fun bookDoodle(): Bitmap {
        val bitmap = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(22, 27, 34))
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(233, 176, 96); style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
        val book = Path().apply {
            moveTo(240f, 72f); cubicTo(200f, 45f, 150f, 52f, 120f, 65f); lineTo(120f, 182f)
            cubicTo(160f, 166f, 205f, 167f, 240f, 188f)
            cubicTo(275f, 167f, 320f, 166f, 360f, 182f); lineTo(360f, 65f)
            cubicTo(330f, 52f, 280f, 45f, 240f, 72f); lineTo(240f, 188f)
        }
        canvas.drawPath(book, pen)
        pen.style = Paint.Style.FILL
        canvas.drawCircle(205f, 104f, 5f, pen); canvas.drawCircle(275f, 104f, 5f, pen)
        pen.style = Paint.Style.STROKE
        canvas.drawArc(211f, 104f, 269f, 146f, 10f, 160f, false, pen)
        listOf(72f to 70f, 402f to 140f, 390f to 43f).forEach { (x, y) ->
            canvas.drawLine(x - 8f, y, x + 8f, y, pen); canvas.drawLine(x, y - 8f, x, y + 8f, pen)
        }
        return bitmap
    }
}
