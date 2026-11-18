package com.novelnest.app.core.lifecycle

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import com.novelnest.app.NovelNestApplication

/** App-opened picker entry point. Screen-off/device lock still revoke the current session. */
fun <I> ActivityResultLauncher<I>.launchKeepingSession(context: Context, input: I) {
    (context.applicationContext as NovelNestApplication).appLockLifecycle.beforeExternalLaunch()
    launch(input)
}
