package com.novelnest.app.feature.lock

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockWindowThemeTest {
    @Test fun dayAndNightWindowsUseTheLockColorsBeforeComposeDraws() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            val context = ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_NovelNest_Lock)
            val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground,
                android.R.attr.statusBarColor, android.R.attr.navigationBarColor, android.R.attr.windowLightStatusBar))
            try {
                assertEquals(R.drawable.app_lock_background, attrs.getResourceId(0, 0))
                assertEquals(context.getColor(R.color.app_lock_background_top), attrs.getColor(1, 0))
                assertEquals(context.getColor(R.color.app_lock_background_bottom), attrs.getColor(2, 0))
                assertTrue(attrs.getBoolean(3, false))
                assertTrue(attrs.getDrawable(0) is GradientDrawable)
            } finally { attrs.recycle() }
            if (Build.VERSION.SDK_INT >= 27) {
                val bars = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowLightNavigationBar))
                try { assertTrue(bars.getBoolean(0, false)) } finally { bars.recycle() }
            }
        }
    }
}
