# App lock: test and build verification

Date: 18 November 2026. Project: F:/My Projects/NovelNest.

## Environment and discovery

Actual tasks were discovered using `./gradlew.bat :app:tasks --all --offline`.
Gradle 8.14.3, AGP 8.13.0, Kotlin 2.0.21, JDK 21 (Adoptium 21.0.9), minSdk 26,
compile/target SDK 35, JUnit and Android Lint are configured. Pixel_8 / Android 16
(API 36) was the available emulator. No physical phone was connected.

Gradle commands used `--offline --max-workers=2 --console=plain`. APK builds also
used `-Pandroid.injected.testOnly=false`. JAVA_HOME was set for each process to the
installed JDK 21; no global runtime, dependency, signing or permission was changed.
AI_LIVE_SMOKE, ASURA_LIVE_SMOKE and FWN_LIVE_SMOKE were 0 for JVM tests.

## Commands, results and relevant output

| Command | Result | Actual output/results |
| --- | --- | --- |
| `./gradlew.bat :app:testDebugUnitTest --tests com.novelnest.app.data.security.AppLockManagerTest` | PASS | 14 tests; zero failures |
| `./gradlew.bat :app:testDebugUnitTest` with related filters below | PASS | 97 tests: 96 passed, 1 skipped; zero failures |
| `./gradlew.bat :app:testDebugUnitTest` | PASS | 491 tests: 487 passed, 4 skipped; zero failures/errors |
| `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest` | PASS | BUILD SUCCESSFUL; final standalone build took 6 seconds, 70 tasks up-to-date |
| `adb -s emulator-5554 install -r APK` for app and test APKs | PASS | Success for both |
| `adb -s emulator-5554 shell am instrument -w -r -e class CLASS_LIST com.novelnest.app.test/androidx.test.runner.AndroidJUnitRunner` | PASS | Final combined run: OK (24 tests), 321.251 seconds |
| `./gradlew.bat :app:lintDebug` | FAIL | 3 pre-existing errors, 180 warnings; no suppression/baseline added |
| `git diff --check` | PASS | No whitespace errors; line-ending conversion warnings only |

Related JVM filters (each passed with `--tests`):

- com.novelnest.app.data.security.*
- com.novelnest.app.ProfileNotificationTest
- com.novelnest.app.feature.search.*
- com.novelnest.app.feature.home.*
- com.novelnest.app.navigation.ReaderEntrySourceTest
- com.novelnest.app.data.remote.novelphoenix.*
- com.novelnest.app.NovelRepositoryTest

CLASS_LIST is a single comma-separated value containing these exact classes:

- com.novelnest.app.data.security.AndroidAppLockStorageTest
- com.novelnest.app.feature.lock.EpubOnlyNavigationTest
- com.novelnest.app.feature.lock.AppLockSettingsUiTest
- com.novelnest.app.feature.lock.AppLockFlowTest
- com.novelnest.app.navigation.ReaderEntryNavigationTest
- com.novelnest.app.feature.epub.EpubLibraryNavigationTest
- com.novelnest.app.data.local.epub.EpubLifecycleTest

Kotlin type checking ran through compileDebugKotlin and compileDebugAndroidTestKotlin.
There is no separate TypeScript/typecheck script. The four skipped JVM classes are
AiLiveProviderTest, AsuraLiveTest, FreeWebNovelLiveSmokeTest and
NovelPhoenixLiveSmokeTest. Skips are not counted as passes.

## Failures, causes and changes

Both review defects were reproduced before production changes:

1. backgroundingClearsUnsubmittedPinAndVisibility failed with EditableText = '123456'
   after background/return. AppLockActivity now owns the ephemeral input and clears
   both PIN input and visibility synchronously in onStop.
2. normalUnlockTaskReplacementPreservesPendingNotification reached FULL access but
   the tapped notification stayed unread. MainActivity now records a validated ID
   in process memory before asynchronous inbox lookup. AppLockActivity relays only
   that ID through normal-access task replacement; Main resolves it against the
   trusted inbox and clears it on consumption. EPUB-only authentication and taps
   during active EPUB-only access discard it.

The two regressions plus a restricted-mode rejection test passed together (3 tests)
and subsequently passed again within the final 24-test selection.

The first broader run reported three more failures. Investigation and reruns found:

- The screen-wake test could use an old RESUMED gate before a newer Main intent
  finished. The helper now waits for new Main-to-gate resume callbacks and window
  focus before typing. Original security and destination assertions remain.
- Android reported EPUB reader startup at 6.8 seconds while lint was competing for
  resources, beyond the existing test's 5-second bound. That unchanged navigation
  test passed on an isolated rerun and in the final combined run without lint.
- EpubLifecycleTest also failed in isolation: advanceUntilIdle cannot finish cover
  work on real Dispatchers.IO. The test now awaits the actual Complete/Failed flow
  result using a real-time bounded dispatcher and retains the Complete and opens == 2
  assertions. No EPUB production code or import/cache behavior was changed.

Files changed during this verification: AppLockActivity.kt, MainActivity.kt,
NovelNestApplication.kt, AppLockFlowTest.kt, EpubLifecycleTest.kt and these documents.
Unrelated existing source/poster changes were preserved. No tests were deleted,
assertions weakened, or lint failures suppressed to obtain a pass.

## Remaining lint failures

These files match HEAD and were left unchanged:

| File/line | Rule | Cause |
| --- | --- | --- |
| app/src/main/res/values/themes.xml:15 | NewApi | windowLightNavigationBar requires API 27; minSdk is 26 |
| app/src/main/java/com/novelnest/app/feature/reader/components/MarkdownNoteEditor.kt:489 | AppCompatCustomView | Custom editor extends EditText rather than AppCompatEditText |
| app/src/main/java/com/novelnest/app/feature/reader/components/ReaderTextSelection.kt:111 | LifecycleCurrentStateInComposition | Direct Lifecycle.currentState read in composition |

Build and selected tests pass; the project-wide lint check remains FAIL.

## Evidence and remaining verification

Local logs: build/diagnostics/verification/focused-tests.log, related-tests.log,
final-unit-build.log, review-regressions-before.log, review-regressions-after.log,
runtime-final.log (initial broader failures), epub-isolated.log, runtime-verified.log
(final 24-test pass), debug-build-final.log, lint-final.log,
final-harness-build-lint.log and diff-check.log. These are ignored build artifacts.
Lint reports: app/build/reports/lint-results-debug.{html,xml,txt}.
Unit reports: app/build/test-results/testDebugUnitTest and
app/build/reports/tests/testDebugUnitTest.

NOT RUN: enrolled physical fingerprint/face authentication and enrollment invalidation,
other Android/API/OEM devices, release build/signing, live cloud/source smoke tests,
and long-duration memory/thermal profiling. The notification regression exercises
inbox-to-authentication handoff, not provider delivery or system permission dialogs.
No code coverage percentage was collected.

Tests refuse to replace an existing user lock and clean up synthetic notifications,
test PINs and their own EPUB fixtures. Final cleanup confirmed no temporary lock
record remained. The headless emulator started for this run was shut down.
## Device-session policy follow-up (18 November 2026)

### Confirmed cause and implementation

The former AppLockLifecycle.onActivityStopped called lock.lock whenever the last app
Activity stopped, except for a bounded picker exemption. Another app covering Novel Nest
also causes onStop. The existing Home/return test required a relock, so it enforced the
previous policy rather than detecting the newly reported behavior. The installed baseline
reproduced that policy: baseline.log reports OK (1 test). This was a lifecycle policy issue,
not evidence of a blocked thread or network failure.

- App switches, Home, and external pickers retain already granted FULL/EPUB_ONLY access
  and the existing page. No unlocked state is written to preferences or the database.
- Backgrounding a locked gate increments its revision, rejecting late PIN/biometric
  completion. Already granted sessions emit no extra state update on app switching.
- Application-scoped protected SCREEN_OFF and USER_PRESENT broadcasts revoke access.
  Activity start/resume also checks PowerManager/KeyguardManager before exposing content.
- AppLockSessionTasks tracks task identities. Finishing/removing the last task revokes
  access; a different task cannot inherit access if its removal callback was missed.
  Authenticated task replacement is bound to its task ID and consumed on creation.
- The existing narration service revokes the session in onTaskRemoved while retaining
  its original continue-playing/stop behavior. No lock service or polling was added.
- Task lookup uses persistentId on API 26-28 and taskId on API 29+, avoiding a new-API
  access on supported Android 8-9 devices.
- Process restart, force-stop, reboot, and a memory cleaner that kills the process require
  authentication. Memory-pressure/UI_HIDDEN callbacks alone do not revoke a live session.
- Manual Lock now/Lock app and the two access modes retain their existing behavior.
- Both new PINs remain four digits. Existing six-digit records retain their stored
  length until explicitly replaced; encryption, KDF, and cooldown protections remain.

The first broader run also confirmed a pending-notification regression in the current
gate: clearing/replacing the task dropped its validated inbox ID. complete now forwards
only that ID for FULL access and discards it for EPUB_ONLY. The original inbox lookup
still validates the destination; route/URL extras are not forwarded. Both handoff tests
pass in the final suite.

### Files and scope

- core/lifecycle/AppLockLifecycle.kt, new AppLockSessionTasks.kt, and ExternalLaunch.kt.
- data/security/AppLockManager.kt: background authentication invalidation.
- feature/lock/AppLockActivity.kt: task-bound handoff and pending notification forwarding.
- data/narration/NarrationService.kt: existing task-removal callback.
- feature/settings/AppLockPage.kt: accurate session/PIN descriptions; layout preserved.
- AppLockManagerTest, new AppLockSessionTasksTest, AppLockFlowTest, and AppLockSettingsUiTest.
- Removed the superseded ExternalHandoff picker timeout and its old-policy tests.
- APP_LOCK.md and this report. Novel/EPUB fetching, Room schema, progress, cache owners,
  and reader UI were not changed by this session-policy fix.

### Actual final verification

Environment: JDK 21, Gradle wrapper 8.14.3, offline Gradle checks; Pixel_8 emulator,
Android 16 / API 36. Android runtime tests use real Activities, Keystore, Home/Settings,
screen sleep/wake, document picker, and finishAndRemoveTask.

| Command / check | Result | Evidence |
| --- | --- | --- |
| `:app:testDebugUnitTest --tests com.novelnest.app.data.security.AppLockManagerTest --tests com.novelnest.app.core.lifecycle.AppLockSessionTasksTest` | PASS | 29 manager + 7 task tests; zero failures/errors/skips |
| `:app:assembleDebug :app:assembleDebugAndroidTest` | PASS | task-bound-build.log: BUILD SUCCESSFUL |
| `adb shell am instrument -w -r -e class <four Android test classes> com.novelnest.app.test/androidx.test.runner.AndroidJUnitRunner` | PASS | runtime-final.log: OK (22 tests), 304.341 seconds |
| `:app:lintDebug` | FAIL | Same 3 existing errors; 181 warnings |
| Scoped diff/whitespace checks | PASS | Changed files, new task policy/tests, and obsolete policy removals reviewed |

The four Android test classes were AndroidAppLockStorageTest (4), EpubOnlyNavigationTest
(1), AppLockSettingsUiTest (2), and AppLockFlowTest (15). Cases include both PIN modes,
four-digit input limits, encrypted/malformed legacy records, pending notifications,
unsubmitted PIN clearing, Home/other-app return, same EPUB preferences preservation,
screen-off in the app/another app/import picker, task removal with the process alive,
gate recreation, repeated intents, and picker cancellation.

Initial runtime.log had three failures: an erroneous Android-runner selection of a JVM-only
test class, an input assertion against masked EditableText instead of actual InputText,
and the genuine dropped-notification regression. The runner selection and assertion were
corrected, and the handoff was fixed. No production password masking was removed and no
assertion was weakened. runtime-verified.log passed 22 tests; runtime-final.log reran all
22 after binding replacement to the actual task ID.

Lint errors remain in unchanged HEAD files: themes.xml:15 (NewApi),
MarkdownNoteEditor.kt:489 (AppCompatCustomView), ReaderTextSelection.kt:111
(LifecycleCurrentStateInComposition). No suppressions or baseline were added.
The added fallback diagnostic follows the existing Android Log convention and contributes
one LogNotTimber style warning; Timber is not a direct project dependency.

Logs are in build/diagnostics/lock-session: baseline.log, build.log, final-build.log,
runtime.log (initial failures), verified-build.log, runtime-verified.log,
task-bound-build.log, runtime-final.log, and lint.log. Reports are under
app/build/test-results/testDebugUnitTest and app/build/reports/lint-results-debug.*.

### Remaining verification

Not run: physical Samsung/other OEMs, API 26-35 runtime checks, enrolled hardware biometrics,
release build/signing, live source/AI requests, long-duration memory/thermal profiling.
Android may defer broadcasts for cached processes; prolonged background/cached-process
and OEM battery-policy behavior still need physical-device verification. Current-state
Power/Keyguard checks supplement the receivers; they are not a historical device-lock log.
No zero-risk/all-scenarios claim or coverage percentage is made.

Final device cleanup confirmed no app_lock.bin remained. No application data was cleared.
The headless emulator created for these checks was shut down after verification.

Android guidance: [Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle),
[process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle),
[broadcast lifetime and delivery](https://developer.android.com/develop/background-work/background-tasks/broadcasts),
[RecentTaskInfo identifiers](https://developer.android.com/reference/android/app/ActivityManager.RecentTaskInfo).
