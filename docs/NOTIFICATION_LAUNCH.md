# Notification launch investigation — 2026-10-24

## Report and evidence

Bookmark and chapter-update notifications reportedly opened the app and then
immediately closed it after the task was removed from Recents. The reminder was
not checked on the user's device. ADB reported no attached devices during this
investigation, so the exact device stack trace remains unconfirmed.

The strongest code finding is a main-thread splash-exit failure, common to these
launches. `MainActivity` unconditionally read
`SplashScreenViewProvider.iconView` before building its exit animation. Android's
[platform icon getter may return null](https://developer.android.com/reference/android/window/SplashScreenView#getIconView()).
An external/cold launch can receive a solid-color splash rather than the icon
normally seen from the launcher. Android also documents the optional icon policy
under [splash screen behavior](https://developer.android.com/develop/ui/views/launch/splash-screen#themes).

The project uses `androidx.core:core-splashscreen:1.0.1`. Inspection with `javap`
of the actual cached AAR confirmed that `ViewImpl31.getIconView()` calls the
platform getter followed by `Intrinsics.checkNotNull`. That turns a valid absent
icon into an uncaught `NullPointerException` in the UI-thread exit callback.
Google records this bug and its subsequent library fix in the
[Core release notes](https://developer.android.com/jetpack/androidx/releases/core#1.1.0-rc01).
This is a confirmed failure path in the deployed dependency/code combination,
not a captured stack trace from the user's phone.

## Changes

1. **Optional splash icon:** API 31+ reads the platform's nullable icon directly.
   An icon gets the existing scale/fade animation; an iconless splash fades its
   background. Earlier Android versions retain the compatibility implementation.
   No second launch screen or fixed startup wait is introduced.
2. **Splash lifetime:** Animation ownership is explicit. End, cancellation, Stop,
   Destroy, reduced-motion instant completion, and finishing before the first
   frame remove the splash once. Recoverable animation/removal exceptions are
   logged by class; they do not close the app.
3. **Cold notification resolution:** Inbox lookup runs on IO. Route data comes
   from the persisted inbox, with bounded/validated IDs, rather than trusting
   external novel extras. Missing/deleted inbox entries open Home. A newer tap
   cancels an older unresolved lookup.
4. **Navigation lifetime:** A notification waits for the navigation graph's first
   back-stack entry and the activity's Started state. It is acknowledged after
   navigation. Pending extras survive an interrupted startup; a saved handled ID
   prevents the original intent replaying on recreation. A fresh `onNewIntent`
   can open the same notification again. Acknowledgements match the request,
   so an old acknowledgement cannot clear a newer pending action.
5. **Activity task:** Bookmark/chapter/reminder and media body intents explicitly
   use New Task, Clear Top, and Single Top. An existing single activity receives
   the tap; a missing task starts normally. The Compose Home destination supplies
   the cold-start Back stack. No Clear Task flag discards a live reading session.
6. **Background notification work:** Read persistence uses the app-owned IO scope
   and avoids rewriting an already-read/absent item. Lifecycle activity timestamps
   are coalesced through one StateFlow consumer; scheduler/persistence work is off
   the UI thread and serialized with initial notification reconciliation. Per-event
   exception handling keeps collectors alive, and a scope exception handler
   prevents optional notification jobs from terminating the process.
7. **Novel loading failures:** Detail and chapter jobs now convert unexpected
   storage/source exceptions into their existing Retry states, retaining any
   displayed metadata/catalog. Cancellation still propagates normally. A cold
   notification no longer exposes an unhandled detail-load coroutine failure
   merely because memory metadata is absent.

The task intent follows Android's
[notification activity guidance](https://developer.android.com/develop/ui/views/notifications/navigation)
and [task/back-stack behavior](https://developer.android.com/guide/components/activities/tasks-and-back-stack).
Scheduling and storage exception paths were hardened as additional risks; there
is no evidence they caused this particular incident.

## Scenario handling

These are code-review expectations; physical-device confirmation remains pending.

| Scenario | Handling |
|---|---|
| Task removed, process alive or killed | Start MainActivity, tolerate an iconless splash, then resolve the notification from disk |
| App already foreground/background | Reuse the activity; handle the incoming intent once navigation is Started |
| Bookmark or chapter update | Open the validated stored novel ID; normal source and chapter fetching continue |
| Reading reminder | Open Home with the persisted source tab; do not restore an unrelated saved reader stack |
| Media notification body | Open/reuse the app; leave playback owned by the existing service; never start playback from the body tap |
| Deleted/pruned inbox item or invalid novel ID | Open Home; ignore untrusted novel extras |
| Rapid taps on different notifications | Cancel obsolete lookups; acknowledge only the matching pending request |
| Rotation/process recreation before navigation | Resolve the unconsumed intent again and wait for navigation readiness |
| Rotation/process recreation after navigation | Restore navigation; suppress replay of the already handled original intent |
| Home key, task switch, or destroy during splash | Cancel the exit animation and remove its view |
| Animations disabled / immediate animation completion | Assign animation ownership before starting it; remove once |
| Offline source, storage failure, or thrown parser error | Existing detail/chapter Retry state; retain already displayed content |
| Notification scheduler/persistence exception | Log the exception class; preserve the activity and collector |

Removing a task from Recents differs from Android force-stop. This change does
not bypass force-stop, notification permission, OEM background restrictions, or
guarantee scheduled delivery. The normal launcher Home-first behavior, source
selection, bookmarks, reading checkpoints, and narration ownership are preserved.

## Verification

- Reviewed Activity, inbox/pending intents, Compose navigation, detail loading,
  notification jobs, and the media notification body route.
- Inspected the exact cached splash library bytecode and checked Android's
  platform API contract and documented library issue.
- The final `:app:assembleDebug --offline --max-workers=1` completed successfully
  in 1m45s. The delivered file is `app/build/outputs/apk/debug/app-debug.apk`.
- No automated tests were added/run, no notification was sent externally, and no
  phone was attached. No on-device cold-start/rotation/lock-screen claim is made.

On-device follow-up: tap bookmark and chapter notifications after Recents removal
on Android 12/13/14/15; repeat with process death, background activity, rapid taps,
rotation during launch, disabled animations, a stale inbox item, and offline mode.
Check reminder and media body taps through the same entry point. If a closure
remains, inspect `AndroidRuntime`, `NovelNestLaunch`, and `Notifications` logcat
records; the remaining failure must be matched to its stack trace.
