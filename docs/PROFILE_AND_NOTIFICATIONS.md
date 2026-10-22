# Profile and real notifications

## Profile

Home's profile icon opens Profile; Settings remains available from the Profile header and the existing footer.

Nickname and selected photo URI are local SharedPreferences data. The system document picker grants access to the selected image only; its read grant is persisted across restarts. Removing/replacing a photo releases the old grant. Coil renders the selected image at the avatar's display size.

Profile shows current/best reading streak, recorded reading days, distinct chapter reads, novels started/read, bookmarks and notification preferences. A successful chapter read records the device's local calendar day. Reading the same day repeatedly does not inflate the streak. A streak remains current through yesterday until the reader has a chance to read today; a full missed day breaks it. Existing last-read dates are imported in one batch. Older daily activity which the app never stored cannot be reconstructed.

## Notification preferences and permission

Notifications are opt-in. Profile and Notifications both provide the same controls:

- Enable notifications: requests POST_NOTIFICATIONS on Android 13+, or opens Android notification settings when needed.
- Bookmark confirmations: immediate system/inbox events when a novel is newly added.
- Bookmarked novel updates: periodic new-chapter checks.
- Reading nudges: a doodle and rotating joke after at least 24 hours away.
- Disable notifications and open Android channel settings.

There are separate Android channels for bookmark confirmations, chapter updates and reminders. Permission denial does not prevent reading or using the profile. Repeated denial leads to settings recovery instead of a nonworking repeated permission request.

## Real events and delivery

New bookmark actions call the notification coordinator after persistence and state publication. The callback is shared by Home, Search, Discover, details and reader bookmark actions through BookmarkManager. It does not fire during startup, an existing bookmark metadata refresh, or removal. Removing and bookmarking again is a new real action. This path does no network requests and is independent of the chapter-update polling preference. The master preference, bookmark preference, runtime permission and Android channel settings still apply. A disabled channel or rejected permission is logged; optional notification failures cannot undo a bookmark.

The inbox is persisted, bounded to 100 events and shared with system notifications. Read state and Mark all read survive restarts. No placeholder chapter releases, cache-completion messages or fake timestamps are inserted.

BookmarkUpdateChecker uses live detail metadata through the existing source repository. It checks at most ten bookmarks per pass using a persisted round-robin cursor. Baselines are initialized from known totals; an unknown first baseline is established silently so old chapters are not announced as new. Only forward chapter counts (or forward chapter numbers when totals are absent) notify. Renames, unchanged totals, rollbacks, failed/blocked fetches and removed bookmarks do not create alerts. Repeated checks are deduplicated. Sources which do not provide live metadata cannot produce reliable new-chapter alerts; no updates are fabricated for them.

JobScheduler runs connected bookmark checks on a two-hour interval with a flexible execution window. Large libraries take multiple passes. The reminders are one-off jobs scheduled after 24 hours from the last foreground/background activity checkpoint. A reminder is suppressed while the app is foreground and delivered at most once per inactive period; returning to the app starts a new period. The reminder uses a small locally drawn book doodle and a joke, requiring no image download.

Persisted jobs survive normal process death and reboot. Android can defer jobs for idle mode, battery restrictions and scheduling quotas; this is periodic polling, not instant server push or an exact alarm. Force-stopping an Android app prevents its jobs until it is opened again.

System-notification taps mark the stored event read and open the corresponding novel. Reminder taps open Home. Existing running activities receive the new intent; cold notification launches do not also resume an unrelated reader session. Only IDs belonging to actual stored inbox events are accepted.

## Validation

ProfileNotificationTest covers nickname/photo persistence, daily streaks and gaps, progress callbacks, bounded/readable inbox persistence, unknown first baselines, real updates, duplicate suppression, network failures, rollback, removal during a request, disabled checks, bounded round-robin source checks and durable inactivity cooldown.

The JVM suite does not test Android permission dialogs, document-provider grants, notification rendering, actual JobScheduler timing or device/OEM battery behavior. Those require a phone or emulator.

## Android references

- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler)
- [User-selected document access and persisted URI grants](https://developer.android.com/training/data-storage/shared/documents-files)


## Inactivity artwork and messages

There is one locally drawn 480 x 240 doodle: a smiling amber open book with three sparkle marks on a dark background. All six message variants share this artwork and the title “A little plot twist for your day.” The current variants are:

1. Your bookmark called. It says the suspense is getting personal.
2. Plot twist: your next chapter has been waiting for YOU.
3. I tried to read your next chapter. The book said, 'Bring my human.'
4. Your fictional friends asked where you went. I said: side quest.
5. A chapter a day keeps the cliffhanger... slightly further away.
6. The villain took a tea break. Perfect time to catch up.

A reminder is eligible after 24 hours away, at most once per inactive period; Android can defer job execution. Its cooldown is now committed only after the system accepts the notification, rather than when delivery is disabled.
