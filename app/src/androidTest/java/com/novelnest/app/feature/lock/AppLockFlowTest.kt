package com.novelnest.app.feature.lock

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.novelnest.app.MainActivity
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.data.security.AppAccess
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as NovelNestApplication
    private var provisioned = false

    /**
     * runBlocking on the instrumentation thread drains the Compose rule's thread-local runTest
     * event loop, which then measures off the main thread and races the real frame
     * ("performMeasureAndLayout called during measure layout"). Use a dedicated thread instead.
     */
    private fun <T> blockingOffTestThread(block: suspend () -> T): T {
        var result: Result<T>? = null
        kotlin.concurrent.thread { result = runCatching { runBlocking { block() } } }.join()
        return result!!.getOrThrow()
    }

    @Before fun configureOnlyOnAnUnlockedTestInstallation() {
        blockingOffTestThread { app.appLock.initialize() }
        Assume.assumeFalse("Never replace an existing user's app-lock credentials.", app.appLock.state.value.enabled)
        blockingOffTestThread { app.appLock.configure("", "1234", "4321", false) }
        provisioned = true
        app.appLock.lock()
    }

    @After fun removeOnlyTestLock() {
        if (!provisioned) return
        blockingOffTestThread {
            if (app.appLock.state.value.access != AppAccess.FULL) {
                app.appLock.lock()
                app.appLock.unlock("1234")
            }
            app.appLock.disable("1234")
        }
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList()
                .filter { it is MainActivity || it is AppLockActivity }.forEach { it.finish() }
        }
    }

    private fun launchApp(notificationId: String? = null) {
        instrumentation.runOnMainSync {
            app.startActivity(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .apply { if (notificationId != null) putExtra(com.novelnest.app.data.notification.EXTRA_NOTIFICATION_ID, notificationId) })
        }
    }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    private fun launchAppAndAwaitGate(notificationId: String? = null) {
        val mainResumed = java.util.concurrent.atomic.AtomicBoolean()
        val gateResumed = java.util.concurrent.atomic.AtomicBoolean()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = androidx.test.runner.lifecycle.ActivityLifecycleCallback { activity, stage ->
            if (stage == Stage.RESUMED && activity is MainActivity) mainResumed.set(true)
            if (stage == Stage.RESUMED && activity is AppLockActivity && mainResumed.get()) gateResumed.set(true)
        }
        monitor.addLifecycleCallback(callback)
        try {
            launchApp(notificationId)
            // A gate still RESUMED from before this intent is not its completion.
            compose.waitUntil(15000) { gateResumed.get() }
            compose.waitUntil(15000) {
                var focused = false
                instrumentation.runOnMainSync {
                    focused = monitor.getActivitiesInStage(Stage.RESUMED)
                        .any { it is AppLockActivity && it.hasWindowFocus() }
                }
                focused
            }
            instrumentation.waitForIdleSync()
        } finally { monitor.removeLifecycleCallback(callback) }
    }

    private fun enter(pin: String) {
        pin.forEach { digit ->
            // The lock screen only scrolls when its content overflows (small screens, large fonts).
            val key = compose.onNodeWithText(digit.toString())
            if (runCatching { key.assertIsDisplayed() }.isFailure) key.performScrollTo()
            key.performClick()
        }
    }

    /** Real touch input: down, a few moves drifting [dyPx] vertically, up. Not synchronized with Compose. */
    private fun driftingTap(label: String, dyPx: Float) {
        val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInWindow
        val x = bounds.center.x; val y = bounds.center.y
        val down = android.os.SystemClock.uptimeMillis()
        fun inject(action: Int, dy: Float) = instrumentation.uiAutomation.injectInputEvent(
            android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, x, y + dy, 0)
                .apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }, true)
        inject(android.view.MotionEvent.ACTION_DOWN, 0f)
        for (step in 1..3) inject(android.view.MotionEvent.ACTION_MOVE, dyPx * step / 3)
        inject(android.view.MotionEvent.ACTION_UP, dyPx)
    }

    @Test fun keypadTapsThatDriftVerticallyAreStillEntered() {
        launchApp()
        compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
        compose.waitForIdle()
        // Only meaningful where the lock screen fits; overflowing content legitimately scrolls.
        val range = androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
        Assume.assumeTrue(compose.onAllNodes(SemanticsMatcher.keyIsDefined(range)).fetchSemanticsNodes()
            .all { it.config[range].maxValue() == 0f })
        // Regression: past touch slop (8dp), the empty scroll container claimed the gesture and the digit was lost.
        val drift = 14f * instrumentation.targetContext.resources.displayMetrics.density
        driftingTap("0", drift)
        driftingTap("0", -drift)
        driftingTap("8", drift)
        compose.waitForIdle()
        val entered = compose.onAllNodesWithContentDescription("3 of 4 digits entered").fetchSemanticsNodes().size
        // End unlocked, like the other tests, so teardown never unlocks with Compose work pending.
        compose.onAllNodesWithContentDescription("Clear PIN").fetchSemanticsNodes().takeIf { it.isNotEmpty() }
            ?.let { compose.onNodeWithContentDescription("Clear PIN").performClick() }
        enter("1234")
        compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.FULL }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertEquals("Vertically drifting taps on 0, 0, 8 must all be entered", 1, entered)
    }

    @Test fun backgroundingClearsUnsubmittedPin() {
        launchApp()
        enter("123")
        compose.onNodeWithContentDescription("3 of 4 digits entered").assertExists()
        val revision = app.appLock.state.value.revision
        shell("input keyevent KEYCODE_HOME")
        compose.waitUntil(15000) { app.appLock.state.value.revision > revision }
        launchApp()
        compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 of 4 digits entered").assertExists()
        Assert.assertTrue(app.appLock.state.value.blocked)
    }

    @Test fun normalUnlockTaskReplacementPreservesPendingNotification() {
        val id = "app-lock-test-" + java.util.UUID.randomUUID()
        try {
            launchApp()
            enter("4321")
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("EPUB reading preferences").fetchSemanticsNodes().isNotEmpty() }
            app.notificationStore.add(com.novelnest.app.data.notification.AppNotification(
                id, "Test reminder", "Test authenticated notification handoff", System.currentTimeMillis(), reminder = true))
            instrumentation.runOnMainSync { app.appLock.lock() }
            compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
            launchAppAndAwaitGate(notificationId = id)
            compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
            enter("1234")
            compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.FULL }
            compose.waitUntil(15000) { app.notificationStore.items.value.any { it.id == id && it.read } }
            compose.onNodeWithText("Home").assertIsDisplayed()
        } finally { app.notificationStore.delete(id) }
    }

    @Test fun epubUnlockDiscardsPendingNotificationInsteadOfReplayingItLater() {
        val id = "app-lock-restricted-test-" + java.util.UUID.randomUUID()
        try {
            app.notificationStore.add(com.novelnest.app.data.notification.AppNotification(
                id, "Test reminder", "Restricted handoff must be discarded", System.currentTimeMillis(), reminder = true))
            launchApp(notificationId = id)
            compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
            enter("4321")
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("EPUB reading preferences").fetchSemanticsNodes().isNotEmpty() }
            Assert.assertNull(app.pendingNotificationId)
            Assert.assertFalse(app.notificationStore.items.value.single { it.id == id }.read)
            compose.onNodeWithText("Home").assertDoesNotExist()
            // A fresh tap while already in restricted mode is discarded too.
            launchApp(notificationId = id)
            compose.waitForIdle()
            Assert.assertEquals(AppAccess.EPUB_ONLY, app.appLock.state.value.access)
            Assert.assertNull(app.pendingNotificationId)
        } finally { app.notificationStore.delete(id) }
    }

    @Test fun secondPinCannotReachHomeAndFirstPinRestoresNormalHome() {
        launchApp()
        compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
        enter("1111")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Incorrect PIN.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Incorrect PIN.").assertIsDisplayed()
        Assert.assertTrue(app.appLock.state.value.blocked)
        enter("4321")
        compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.EPUB_ONLY }
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Novel Nest").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Novel Nest").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Discover").assertDoesNotExist()
        compose.onNodeWithContentDescription("EPUB reading preferences").performClick()
        compose.onNodeWithText("Lock & Biometrics").assertDoesNotExist()
        compose.onNodeWithText("Lock app").performScrollTo().performClick()
        compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
        enter("1234")
        compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.FULL }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Novel Nest").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Discover").assertIsDisplayed()
    }

    @Test fun recreatedLockScreenCanFinishItsAuthenticatedNavigation() {
        launchApp()
        compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AppLockActivity>().single().recreate()
        }
        enter("4321")
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("EPUB reading preferences").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertEquals(AppAccess.EPUB_ONLY, app.appLock.state.value.access)
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test fun repeatedMainIntentsCannotStrandAnExistingLockScreen() {
        launchApp()
        compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
        repeat(3) {
            launchAppAndAwaitGate()
            compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
            Assert.assertTrue(app.appLock.state.value.blocked)
        }
        enter("4321")
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("EPUB reading preferences").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            android.util.Log.e("AppLockFlowTest", "Repeated-entry state: " + app.appLock.state.value)
            compose.onRoot().printToLog("AppLockFlowTest")
            throw failure
        }
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test fun screenOffRequiresAuthenticationAfterWake() {
        launchApp()
        enter("1234")
        compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.FULL }
        // Access is granted before the cold-start task handoff finishes. Wait for
        // the real destination before sleeping, and for RESUMED before typing.
        compose.waitUntil(15000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        try {
            shell("input keyevent KEYCODE_SLEEP")
            compose.waitUntil(15000) { app.appLock.state.value.blocked }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        launchAppAndAwaitGate()
        compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
        enter("1234")
        compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.FULL }
    }

    @Test fun externalPickerReturnKeepsTheSameModesSettingsPageWithoutRelocking() {
        launchApp()
        enter("4321")
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("EPUB reading preferences").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("EPUB reading preferences").performClick()
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            @Suppress("DEPRECATION")
            activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/epub+zip")
                .addCategory(Intent.CATEGORY_OPENABLE), 8129)
        }
        compose.waitUntil(15000) { !appActivityResumed() }
        Assert.assertFalse(app.appLock.state.value.blocked)
        shell("input keyevent KEYCODE_BACK")
        compose.waitUntil(15000) { appActivityResumed() }
        compose.onNodeWithText("Unlock Novel Nest").assertDoesNotExist()
        Assert.assertEquals(AppAccess.EPUB_ONLY, app.appLock.state.value.access)
        compose.onNodeWithText("EPUB Reading Preferences").assertIsDisplayed()
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    private fun appActivityResumed(): Boolean {
        var resumed = false
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { it is MainActivity || it is AppLockActivity }
        }
        return resumed
    }

    /** EPUB-only mode, Import icon -> system picker is in front of every app Activity. */
    private fun openImportPickerInEpubOnlyMode() {
        launchApp()
        enter("4321")
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("EPUB reading preferences").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Import EPUBs").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Import EPUBs").performClick()
        compose.waitUntil(15000) { !appActivityResumed() }
        // Give the last app Activity time to reach onStop, which is where the lock decision runs.
        compose.waitUntil(15000) {
            var stopped = false
            instrumentation.runOnMainSync {
                stopped = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                    .any { it is MainActivity }
            }
            stopped
        }
    }

    @Test fun epubOnlyImportPickerReturnKeepsTheSession() {
        openImportPickerInEpubOnlyMode()
        Assert.assertFalse("Opening the app's own import picker must not lock", app.appLock.state.value.blocked)
        shell("input keyevent KEYCODE_BACK")
        compose.waitUntil(15000) { appActivityResumed() }
        compose.waitForIdle()
        Assert.assertEquals(AppAccess.EPUB_ONLY, app.appLock.state.value.access)
        Assert.assertFalse(app.appLock.state.value.blocked)
        compose.onNodeWithText("Unlock Novel Nest").assertDoesNotExist()
        compose.onNodeWithContentDescription("Import EPUBs").assertExists()
    }

    @Test fun screenOffWhileInTheImportPickerStillLocks() {
        openImportPickerInEpubOnlyMode()
        try {
            shell("input keyevent KEYCODE_SLEEP")
            compose.waitUntil(15000) { app.appLock.state.value.blocked }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        shell("input keyevent KEYCODE_BACK")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Unlock Novel Nest").fetchSemanticsNodes().isNotEmpty() }
        enter("4321")
        compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.EPUB_ONLY }
    }

    private fun awaitAppStopped() {
        compose.waitUntil(15000) {
            var stopped = false
            instrumentation.runOnMainSync {
                stopped = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                    .any { it is MainActivity }
            }
            stopped
        }
    }

    @Test fun homeGestureAndAnotherAppKeepTheUnlockedSession() {
        launchApp()
        enter("1234")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        shell("input keyevent KEYCODE_HOME")
        awaitAppStopped()
        Assert.assertEquals(AppAccess.FULL, app.appLock.state.value.access)
        Assert.assertFalse(app.appLock.state.value.blocked)
        shell("am start -W -a android.settings.SETTINGS")
        launchApp()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Unlock Novel Nest").assertDoesNotExist()
        Assert.assertEquals(AppAccess.FULL, app.appLock.state.value.access)
    }

    @Test fun epubOnlyAppSwitchKeepsPreferencesAndNavigationRestrictions() {
        launchApp()
        enter("4321")
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("EPUB reading preferences").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("EPUB reading preferences").performClick()
        shell("am start -W -a android.settings.SETTINGS")
        awaitAppStopped()
        Assert.assertEquals(AppAccess.EPUB_ONLY, app.appLock.state.value.access)
        launchApp()
        compose.onNodeWithText("EPUB Reading Preferences").assertIsDisplayed()
        compose.onNodeWithText("Unlock Novel Nest").assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test fun screenOffInAnotherAppStillRequiresAuthentication() {
        launchApp()
        enter("1234")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        shell("am start -W -a android.settings.SETTINGS")
        awaitAppStopped()
        Assert.assertFalse(app.appLock.state.value.blocked)
        try {
            shell("input keyevent KEYCODE_SLEEP")
            compose.waitUntil(15000) { app.appLock.state.value.blocked }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        launchAppAndAwaitGate()
        enter("1234")
        compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.FULL }
    }

    @Test fun removingTaskRequiresAuthenticationEvenWithTheProcessStillAlive() {
        launchApp()
        enter("1234")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        lateinit var removed: MainActivity
        instrumentation.runOnMainSync {
            removed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            removed.finishAndRemoveTask()
        }
        compose.waitUntil(15000) { removed.isDestroyed }
        // The instrumentation process remains alive, so process restart cannot mask the task boundary.
        launchAppAndAwaitGate()
        compose.onNodeWithText("Unlock Novel Nest").assertIsDisplayed()
        Assert.assertTrue(app.appLock.state.value.blocked)
        enter("1234")
        compose.waitUntil(15000) { app.appLock.state.value.access == AppAccess.FULL }
    }
}
