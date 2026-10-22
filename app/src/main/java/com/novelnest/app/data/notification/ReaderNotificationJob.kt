package com.novelnest.app.data.notification

import android.app.job.JobParameters
import android.app.job.JobService
import com.novelnest.app.NovelNestApplication
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class ReaderNotificationJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = scope.launch {
            var retry = false
            try {
                val coordinator = (application as NovelNestApplication).notificationCoordinator
                withTimeout(4 * 60_000L) {
                    if (params.jobId == CHAPTER_JOB) coordinator.checkBookmarkedUpdates()
                    else coordinator.sendInactivityReminder()
                }
            } catch (_: TimeoutCancellationException) { retry = true
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { retry = true
            } finally {
                running.remove(params.jobId)
                if (isActive) withContext(Dispatchers.Main) { jobFinished(params, retry) }
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
