# NizKarya: Android app

Native Android client for NizKarya (Kotlin + Jetpack Compose + Material 3),
backed by Firebase Auth and Cloud Firestore on the `users/{uid}/…` schema.

## What's in it

- Auth: email sign in, sign up and password reset, plus "Continue with Google"
- Today: greeting, progress ring, today's tasks, habit check-off, an overdue
  nudge, and a link into Insights
- Plan → Tasks: grouped list, filters, and a full editor with steps,
  recurrence and tags. Steps can be ticked straight from the list. Completing
  a recurring task spawns the next occurrence.
- Plan → Habits: all frequencies, streaks with optional forgiveness for missed
  days, a seven-day strip on every row, and archive/restore/delete
- Plan → Review: replan every overdue task into today's free slots, or handle
  them one at a time. Missed habits from the last week group by habit.
- Routines: create, edit, delete, and run a whole set into today in one tap
- Insights: 7-day completion chart, on-time vs spillover, day streak, and
  30-day habit consistency
- Voice quick-add: the mic on Today feeds the system speech recognizer through
  the natural-language parser
- Profile: stats, appearance (wallpaper colours, light/dark), notification
  permission, sign out

Reminders are scheduled on-device with `AlarmManager`. There is no server and
no FCM. Both habits and tasks notify: habits at their reminder time, tasks at
their scheduled time. `ReminderScheduler` rebuilds every alarm in one
idempotent pass, run at app start, every six hours by an unconstrained
WorkManager job, and after a reboot or an app update. Each pass arms a rolling
seven-day window, so a reminder for any future date arrives without the app
being opened, and several skipped worker runs still cost nothing.

Unit tests cover the quick-add parser, habit scheduling, streaks, milestones,
recurrence, day planning and day streaks.

Reminders post as ongoing notifications carrying three actions: **Done**
(writes the completion straight to Firestore from the receiver, no need to open
the app), **In an hour** (re-arms the same reminder), and **Dismiss**.

### Known gaps

- Exact alarms need `SCHEDULE_EXACT_ALARM`, which Android 12+ can withhold. The
  scheduler falls back to a ten-minute window, so a reminder can arrive late.
- Force-stopping the app from Settings cancels its alarms and blocks its
  workers and boot receiver until it is launched again. That is an Android
  rule no app can work around.
- Aggressive OEM battery managers (Xiaomi, Oppo, OnePlus and similar) defer or
  kill background work. The seven-day window absorbs a lot of that, but on
  those devices the app may need exempting from battery optimisation.
- `setOngoing(true)` stops a reminder being swiped away on Android 13 and
  below. Android 14 changed this: users can dismiss ongoing notifications, and
  the only exemptions (foreground services, CallStyle, device policy owners)
  do not apply to a habit reminder. So on 14+ the notification resists
  dismissal but cannot prevent it.
- Not ported from the retired web app: drag reorder, bulk select, global
  search.

Google sign-in activates once the shared keystore's SHA-1 (below) is added to
the Firebase Android app. Until then the button explains what is missing.

## Signing and releases

- `keystore/nizkarya-debug.keystore` (committed) signs every debug and release
  build, so APKs upgrade in place across machines and CI. This is for debug
  distribution only; a Play Store release would use a separate private key.
- `.github/workflows/android-release.yml` builds a signed APK on every merge to
  main touching `mobile/` and publishes it as a GitHub Release, versioned
  `0.2.<run number>`.

SHA-1 of the shared key:

```
E1:ED:DF:7E:DC:D1:B4:73:F1:48:31:53:53:61:58:D3:1D:32:AD:87
```

## Requirements

- Android Studio (Koala or newer), or JDK 17 with Android SDK 34
- `mobile/app/google-services.json` (committed) is the Android config for the
  `next-gen-track` Firebase project (`com.nizkarya.app`). Firebase client
  config is not secret, since it ships inside every APK; access is enforced by
  the Firestore rules in the repo root.

## Build and run

```bash
cd mobile
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
```

Or open the `mobile/` folder in Android Studio and press Run.

## CI

`.github/workflows/android.yml` compiles the app and runs the unit tests on
every PR touching `mobile/`, and uploads the debug APK as a build artifact.

## Conventions

- minSdk 26, target and compile SDK 34
- Compose UI reads repositories exposing Firestore snapshot listeners as Flows
- User-facing copy uses no em-dashes. Plain sentences, no filler.
