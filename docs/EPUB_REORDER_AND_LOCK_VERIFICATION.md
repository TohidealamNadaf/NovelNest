# EPUB reorder and app-lock verification

Date: 18 November 2026. Project: F:/My Projects/NovelNest.

## Scope and environment

Verified the current full-height Reorder volumes editor, its position dialog, and the
recent lock-window/theme changes, followed by related persistence and navigation
regressions. Existing work in the checkout was preserved. No dependencies,
permissions, database schema, biometric policy, or source-fetching code changed.

Discovered configuration: Gradle wrapper 8.14.3, Android Gradle Plugin 8.13.0,
Kotlin 2.0.21, JDK 21, compile/target SDK 35, min SDK 26. Tests use JUnit 4,
AndroidJUnitRunner, Compose UI testing, and Room. Runtime checks used the existing
Pixel_8 AVD running Android 16 / API 36, not a physical Pixel or Samsung.

## Results

| Command / check | Result | Actual evidence |
| --- | --- | --- |
| Focused testDebugUnitTest filters below | PASS | 42 tests: 29 lock manager, 7 task-session, 6 EPUB-library state; zero failures or skips |
| Focused Android runner, two new classes below | PASS | OK (7 tests), 47.779 seconds |
| Full testDebugUnitTest, offline | PASS | 64 suites; 513 discovered, 509 passed, 4 skipped, zero failures/errors |
| Kotlin and instrumentation compilation | PASS | compileDebugKotlin and compileDebugAndroidTestKotlin completed |
| lintDebug | FAIL | 3 pre-existing errors and 181 warnings; details below |
| assembleDebug + assembleDebugAndroidTest | PASS | Separate final-build.log: BUILD SUCCESSFUL in 3s; prior drag-fix-build.log compiled changed production code successfully in 33s |
| Existing Android regressions, seven classes below | PASS | OK (34 tests), 449.588 seconds |
| Scoped diff, whitespace and artifact review | PASS | Only the reorder editor changed in production during this verification; tested APK hashes match final APKs |
| Release build, physical-device and hardware biometric checks | NOT RUN | Debug verification on one emulator only |

The 42 focused JVM cases are a subset of the full JVM run, not additional unique
cases. Android results are 41 unique passing tests across the two final runs.
The four skipped JVM tests are explicitly live/opt-in checks: AiLiveProviderTest,
AsuraLiveTest, FreeWebNovelLiveSmokeTest and NovelPhoenixLiveSmokeTest. Live
AI/source opt-ins were disabled; no live inference test was requested or run.

## Confirmed defects and fixes

The first meaningful focused UI rerun reproduced two defects in
[the reorder editor](../app/src/main/java/com/novelnest/app/feature/epub/EpubLibraryDialogs.kt):

1. A completed drag could change the order without showing Undo. The callbacks read
   rememberUpdatedState(orderedIds), which updates during recomposition. A drag/end
   event can happen before that recomposition, observing the previous order.
   Callbacks and the edge-scroll coroutine now read the mutable draft directly.
2. A cancelled drag left the live insertion in the draft, with no completed-move
   undo entry. onDragCancel now restores dragStartOrder before clearing drag state.

No layout or persistence contract was changed. The editor still saves only through
Save order, and the repository validates exact series membership transactionally.
No existing test was removed and no assertion was weakened.

New regression files:

- [EpubReorderEditorUiTest.kt](../app/src/androidTest/java/com/novelnest/app/feature/epub/EpubReorderEditorUiTest.kt): six tests cover input bounds, exact save and saved-progress preservation, quick placement/Undo/Cancel without writes, restored drafts, completed drags, cancelled drags, and stale membership/reload.
- [AppLockWindowThemeTest.kt](../app/src/androidTest/java/com/novelnest/app/feature/lock/AppLockWindowThemeTest.kt): verifies the manifest theme's shared background/bar resources under day and night configurations, including API-27 navigation-icon qualification.

During new test development, one incorrect Compose matcher call failed compilation.
The first runtime attempt also had ambiguous selectors matching generated cover text
and child Text nodes rather than actionable buttons. These test mistakes were
corrected. focused-runtime-selectors.log then reproduced the two actual drag
failures; focused-runtime-fixed.log passed all seven unchanged behavioral checks
after the production fix. Failed attempts remain in the diagnostics directory.

## Lint failures and remaining warnings

The combined full-checks command returns failure because lintDebug fails, despite
its unit tests and APK tasks completing. The separate final build passes.

- NewApi: app/src/main/res/values/themes.xml:15, existing launcher
  windowLightNavigationBar attribute requires API 27 while min SDK is 26.
  The offending line is present in HEAD; the new lock-theme attribute is correctly
  placed in values-v27.
- AppCompatCustomView: app/src/main/java/com/novelnest/app/feature/reader/components/MarkdownNoteEditor.kt:489,
  ScrollableNoteEditText extends EditText.
- LifecycleCurrentStateInComposition: app/src/main/java/com/novelnest/app/feature/reader/components/ReaderTextSelection.kt:111,
  lifecycle.currentState is read from composition.

Both reader files are unchanged versus HEAD. No lint suppression, baseline, or
unrelated reader/launcher refactor was added to hide these findings. Test compilation
also reports platform deprecation warnings for inspecting legacy system-bar
attributes; those assertions check supported older-Android theme behavior.

## Commands

Run from the project root with JDK 21. The SDK's adb executable used here is
E:/android/Sdk/platform-tools/adb.exe. Installation used install -r for both APKs;
no app data was cleared.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.novelnest.app.data.security.AppLockManagerTest --tests com.novelnest.app.core.lifecycle.AppLockSessionTasksTest --tests com.novelnest.app.feature.epub.EpubLibraryStateTest --offline --max-workers=2 --console=plain

# Full suite and lint/build tasks were run with --continue to preserve all results.
$env:AI_LIVE_SMOKE='0'
$env:ASURA_LIVE_SMOKE='0'
$env:FWN_LIVE_SMOKE='0'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --continue --offline --max-workers=2 --console=plain '-Pandroid.injected.testOnly=false'

# Independent final build, successful despite the separate lint failures.
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --max-workers=2 --console=plain '-Pandroid.injected.testOnly=false'

adb -s emulator-5554 shell am instrument -w -r -e class 'com.novelnest.app.feature.epub.EpubReorderEditorUiTest,com.novelnest.app.feature.lock.AppLockWindowThemeTest' com.novelnest.app.test/androidx.test.runner.AndroidJUnitRunner

adb -s emulator-5554 shell am instrument -w -r -e class 'com.novelnest.app.feature.epub.EpubLibraryUiTest,com.novelnest.app.feature.epub.EpubLibraryNavigationTest,com.novelnest.app.data.local.epub.EpubOrganizationIntegrationTest,com.novelnest.app.data.security.AndroidAppLockStorageTest,com.novelnest.app.feature.lock.EpubOnlyNavigationTest,com.novelnest.app.feature.lock.AppLockSettingsUiTest,com.novelnest.app.feature.lock.AppLockFlowTest' com.novelnest.app.test/androidx.test.runner.AndroidJUnitRunner
```

Instrumentation shell exit codes alone were not treated as proof: the final
OK (N tests) summaries and per-test statuses were inspected.

## Runtime coverage, cleanup, and remaining verification

The existing 34 Android cases cover library actions and saved volume ordering,
actual Home/library/series/reader navigation, preserving website source selection,
Room organization transactions, stale reorders, partial/batch import behavior,
reading positions and file identity, encrypted lock storage/tampering and legacy
PIN records, both PIN modes and confirmations, notification handoffs, restricted
navigation, task replacement/recreation, draft-PIN clearing, repeated launcher
intents, Home/other-app return, screen-off/wake in the app and in another app,
import-picker return/screen-off, and task removal with the process still alive.

Post-run inspection confirmed no app_lock.bin remained in no_backup; test locks
were removed. The sampled final AndroidRuntime log had normal VM exits and no
fatal exception. Test fixtures are isolated or test-owned and cleaned up by the
existing tests. No uninstall, data reset, or device credential replacement occurred.

Not verified: physical Samsung S25 Ultra transition frames, other OEMs, API 26-35
runtime behavior, enrolled biometric hardware, long-list edge-drag stress across
different display/font sizes, long-duration memory/thermal profiling, release
signing/distribution, or live AI/narration/source availability. Theme/resource and
wake/re-entry checks pass on API 36; they are not visual proof that every Samsung
transition frame is now identical. No all-scenarios or coverage-percentage claim.

Evidence: build/diagnostics/current-verification contains focused.log,
focused-runtime.log, focused-runtime-selectors.log, focused-runtime-fixed.log,
drag-fix-build.log, full-checks.log, final-build.log, regression-runtime.log,
results.json (JVM cases and tested APK hashes), and the original reorder source.
Detailed standard reports remain under app/build/test-results/testDebugUnitTest
and app/build/reports/lint-results-debug.*.
