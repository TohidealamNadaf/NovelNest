# App lock and EPUB-only access

Implemented 18 November 2026.

## Use

Settings -> Lock & Biometrics sets two distinct four-digit PINs, each with confirmation.
The normal PIN grants full access; the EPUB PIN grants only the EPUB library, series,
reader, dictionary within the reader, and EPUB reading preferences. Changing PINs,
enabling biometrics, or disabling the lock requires the current normal PIN.
There is no secondary-PIN administration bypass.

Forgot PIN: when the normal PIN is replaced, the replaced normal PIN's digest is kept
(salted PBKDF2, same as live PINs) as the single recovery credential. "Forgot password?"
on the lock screen accepts only that previous normal PIN, never the current PIN or any
EPUB PIN. It requires new, confirmed four-digit normal and EPUB PINs, then grants
normal access and deletes the recovery credential so it cannot be replayed. Wrong old
PINs count toward the shared unlock cooldown. Malformed input is rejected without
counting as an attempt. Changing only the EPUB PIN does not create a recovery
credential. Locks that were never changed have none, so the dialog explains that
recovery is unavailable. The credential has no expiry, and disabling the lock deletes it.

Biometrics are optional and grant normal access. They require an enrolled Android
Class 3 biometric. Unsupported hardware, cancellation, lockout, and enrollment/key
changes leave the app PIN available. Re-enable biometric access with the normal PIN
after an enrolled-biometric change invalidates its key. There is no device-lock-PIN
fallback that could bypass the two app PINs.

Cold launcher unlocks open Home or EPUB Library. A warm unlock using the same access mode
resumes the existing page, preserving reader/editor and document-picker state.
Switching access mode clears the entire old task before entering its allowed root.
Back from the lock screen backgrounds the task; it cannot dismiss authentication.
Draft PIN input and its visibility toggle are cleared synchronously when the lock
Activity stops, so a returning user cannot submit another foreground session's draft.

## Enforcement and lifecycle

- AppLockManager owns process-memory access. No unlocked state is persisted.
- AppLockLifecycle covers MainActivity, EpubReaderActivity, DictionaryLookupActivity,
  and future application activities through Application.ActivityLifecycleCallbacks.
- Startup loading and locked screens receive an opaque input-blocking cover.
  FLAG_SECURE protects activity previews and screenshots while app lock is enabled.
- Ordinary Home gestures, switching apps, and document/photo pickers keep the existing
  access mode and page while the device remains active. No background timeout is used.
- Screen-off and device-unlock broadcasts revoke the session, including when another
  app/picker is in front. Activity start/resume also checks PowerManager and KeyguardManager.
- Removing the last task revokes access on Activity destruction or the existing narration
  service's task-removal callback. A different task ID cannot inherit an old session if
  a removal callback was missed. Only an authenticated gate can authorize task replacement.
- Process death, force-stop, reboot, or a memory cleaner that kills the process loses
  the in-memory session; reopening requires authentication. Low-memory warnings and
  UI_HIDDEN alone do not lock an otherwise live session.
- Configuration recreation and internal navigation keep their task/session. Backgrounding
  increments the revision only while locked to reject pending PIN/biometric completions;
  an already granted session is unchanged. Permissions are never persisted as unlocked.
- Revision checks reject PIN/biometric results from a previous foreground session,
  including a background transition while credential storage is being written.
- The second PIN uses EpubOnlyApp, an independent allow-list NavHost. It never
  registers Home, Discover, Search, webnovel/manhwa readers, Profile, Notifications,
  AI configuration, narration setup, or full Settings. It has no main-app footer.
- The library root replaces Back with the Novel Nest icon and adds a reading-preferences
  action. Series/readers retain Back within the EPUB flow.
- Cold notification taps still require authentication. Notifications cannot install a
  normal-app route into EPUB-only navigation. Existing notification resolution and
  source/reader routing are retained for normal access. A validated pending notification ID
  survives authenticated task replacement for normal access and is re-resolved against
  the trusted inbox. EPUB-only authentication discards the pending tap; taps received
  while already in EPUB-only mode are discarded too.
- Entering EPUB-only access stops an existing webnovel narration session. A restricted
  session cannot start narration through stale service actions. Normal background
  narration otherwise retains its existing playback lifecycle.

## Credential storage and work ownership

AppPin uses PBKDF2-HMAC-SHA256, 210,000 iterations, random 16-byte per-PIN salts,
32-byte digests, and constant-time digest comparison. PINs are never written to disk,
logs, intents, saved instance state, or Room. PIN fields use password input and
ephemeral Compose state.

AndroidAppLockStorage atomically writes an AES-GCM authenticated record using a
separate Android Keystore key. Its file is in noBackupFilesDir and is excluded from
cloud backup/device transfer by Android. Biometrics use a distinct per-operation
Keystore key, invalidated by biometric enrollment changes, through AndroidX
BiometricPrompt and a real cryptographic operation.

Record version 2 stores the PIN length explicitly. Version 1 records are read as
six-digit PINs with the same KDF. Existing PINs remain valid until the owner enters
the current normal PIN and replaces both with four-digit PINs. Leaving replacement
fields blank preserves the existing length; PINs are never truncated or reset.
Corrupt records, authentication-tag failures and missing Keystore keys fail closed. They never silently disable the lock.

Five failed PIN attempts trigger 30 seconds of cooldown; ten or more trigger 60
seconds. Failed-attempt state survives process restart. Only normal-PIN or biometric
success clears these attempts: knowing the EPUB PIN cannot reset attempts against
normal access. Restored cooldowns are bounded against backwards clock changes.

Keystore, KDF, and file operations run on Dispatchers.IO behind one mutex. The UI
does not run password hashing, write per frame, poll for authentication, keep a
background lock service, or make network requests. Only the visible cooldown label
updates once per second while needed. Existing library, bookmark, progress, and
publication caches retain their current owners.

## Implementation map

| Location | Responsibility |
| --- | --- |
| data/security/AppLockModels.kt | Access modes, PIN derivation and session policy |
| data/security/AndroidAppLockStorage.kt | Atomic AES-GCM credential record |
| data/security/AppLockManager.kt | Authentication, cooldowns, revisions and administration |
| data/security/AppBiometrics.kt | Strong-biometric availability and per-use Keystore cipher |
| core/lifecycle/AppLockLifecycle.kt | Device events, privacy covers and lock-screen launch deduplication |
| core/lifecycle/AppLockSessionTasks.kt | Process-local task identities and authenticated replacement binding |
| feature/lock/AppLockActivity.kt | PIN entry and native BiometricPrompt |
| feature/lock/EpubOnlyApp.kt and EpubOnlySettings.kt | Restricted navigation and reading-only preferences |
| feature/settings/AppLockPage.kt | Configure, change, disable and explicitly lock |
| MainActivity and NovelNestApplication | Startup/session wiring |
| EpubLibraryScreen and SettingsMainView/SettingsScreen | Existing-page entry points |
| NarrationService | Reject restricted or locked new playback requests |

A wake/launcher race found during device testing is handled in AppLockLifecycle:
Android can reorder an existing lock Activity without calling onCreate, and a newer
Main intent can supersede a pending gate launch. Every actual Activity resume now
resets the pending-launch latch before checking access. State emissions still use
the deduplicated helper, so no timer, retry loop, or repeated UI polling is needed.
The opaque cover remains until authentication succeeds.

## Configuration

- AndroidX biometric 1.1.0 (compatible with the existing minSdk 26 / compileSdk 35).
- USE_BIOMETRIC normal manifest permission; no new runtime permission dialog.
- AppLockActivity is private, singleTop, and uses the existing reader-compatible theme.
- No Room migration, source parser change, cloud account, or remote API is required.
- Existing reading/app preference reset does not disable app lock.
- A fresh install or restore to another device requires configuring app lock again;
  the device-bound credential record is deliberately not transferred.
- This feature gates application access. It does not encrypt existing EPUB files or
  the library database, and does not alter the app's existing notification privacy.

## Verification

AppLockManagerTest covers fresh installs, both PINs, leading zeroes, mode transitions,
cold-session reset, duplicate/partial PIN rejection, normal-PIN administration,
restart-persistent cooldowns, lower-privilege retry protection, storage failures,
corrupt records, interrupted unlocks and stale biometric completions.

AndroidAppLockStorageTest exercises Android Keystore round-trip encryption and
tamper rejection using an isolated test file. EpubOnlyNavigationTest checks the
brand/no-Back library root, absent full-app destinations and reading-only settings.
AppLockFlowTest drives actual PIN entry, wrong-PIN rejection, restricted-to-normal
switching and device-session boundaries on an Android emulator.

Initial verification on 18 November 2026 (the former foreground-lock policy; see the
device-session follow-up in APP_LOCK_VERIFICATION.md for the updated behavior):

- Debug APK and instrumentation APK assembled successfully offline.
- 20 focused JVM tests passed: 14 AppLockManagerTest and 6 existing SearchFunctionalityTest checks.
- All 9 instrumentation tests passed together on Pixel 8 / Android 16 (API 36):
  Keystore encryption/tamper handling; restricted library/preferences; Settings setup,
  confirmation and normal-PIN-only disable; wrong PIN and mode switching; Home/return;
  external document-picker return preserving the same page; screen off/wake;
  lock-screen recreation; repeated Main intents reusing an existing lock Activity.
- Tests never replace an existing user lock. Temporary test PIN configuration is
  removed in cleanup; EPUB/library data is not cleared.
- Tracked diff checks and whitespace/conflict-marker review of the new files passed.

Build evidence is in build/diagnostics/app-lock/resume-latch-build.log and
reorder-synchronization-build.log. The final device run is recorded in
build/diagnostics/app-lock/complete-device-tests.log. These are local build artifacts.

Actual enrolled fingerprint/face authentication, biometric enrollment invalidation,
OEM-specific multitasking, real notification/narration combinations, and process
termination while editing still need physical-device checks. The tests verify the
manager's stale-biometric-result rejection, not an enrolled hardware biometric.
No device data was erased and no live AI/narration request was required.

## Follow-up verification

The subsequent review reproduced and fixed retained PIN drafts and lost notification
handoffs. See [APP_LOCK_VERIFICATION.md](APP_LOCK_VERIFICATION.md) for current commands,
results, test synchronization changes, and the remaining pre-existing lint failures.

## Lock window appearance

The lock Activity uses its own light AppCompat theme, not the EPUB reader theme.
Its XML window background, Compose background, and active-lock privacy cover use
the same three amber colors. Initial system-bar colors match the gradient ends.
The opaque cover still blocks input; unresolved startup keeps the launcher color.
Android can resolve the matching appearance before onCreate/Compose renders,
including screen-off/device-unlock
re-entry and Activity recreation. Dark navigation icons are qualified for API 27+;
API 26 retains the platform-compatible fallback. Runtime edge-to-edge behavior
is unchanged. The main launcher splash and EPUB/reader themes are unchanged;
no delay, preview suppression, authentication change, or background work is added.

Android reference: https://developer.android.com/topic/performance/issues/launch-time

Verification for this appearance change: `:app:assembleDebug --offline` passed;
AAPT2 confirmed the packaged default/API-27 lock themes and gradient colors.
The scoped diff/whitespace and manifest routing checks passed. No device was
connected, so the Samsung screen-off/unlock animation has not been visually verified.
Automated tests were not added or run for this appearance-only change.

Follow-up verification on 18 November 2026: day/night lock-window resource checks and
screen-off/wake lifecycle regressions passed on the Pixel_8 API-36 emulator.
Physical Samsung transition frames remain unverified. See
[the current verification report](EPUB_REORDER_AND_LOCK_VERIFICATION.md) for commands,
results, existing lint failures, and the related EPUB reorder fixes.
