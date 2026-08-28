# NizKarya — Android app

Native Android client for NizKarya (Kotlin + Jetpack Compose + Material 3),
sharing the same Firebase project (Auth + Firestore, `users/{uid}/…` schema)
as the web app in the repo root.

## Status

**E2E port complete.** Working end-to-end against the shared Firebase
project:

- Email auth (sign in / sign up / password reset)
- Today home (greeting, progress ring, today's tasks, habit check-off,
  overdue nudge, focus CTA)
- Plan → Todos (natural-language quick add, grouped list, filters,
  full editor with subtasks/recurrence/tags, complete/reopen with
  recurring-todo spawn, delete)
- Plan → Habits (streaks, milestones, all frequencies, editor,
  archive/restore/delete)
- Plan → Focus (select items, timed sprint with live countdown, metrics
  logged to Firestore like the web app)
- Plan → Review (overdue tasks: move to today / skip / archive; missed
  habits last 7 days: mark done / skip)
- Routines (create/edit/delete, one-tap run into today's tasks)
- Profile (stats, sign out, notification permission)
- On-device habit reminders (AlarmManager exact alarms; no server/FCM)
- Ported unit tests: quick-add parser, habit scheduling/streaks/milestones,
  recurrence

Not yet ported: Google sign-in (needs SHA-1 in Firebase console), insights
charts, drag reorder, bulk select, global search.

## Signing & releases

- `keystore/nizkarya-debug.keystore` (committed) signs **every** debug and
  release build, so APKs upgrade in place across machines and CI. Debug
  distribution only — a Play Store release would use a separate private key.
- `.github/workflows/android-release.yml` builds a signed APK on every merge
  to main touching `mobile/` and publishes it as a GitHub Release with an
  auto-incremented version (`0.2.<run>`).

## Requirements

- Android Studio (Koala or newer) or JDK 17 + Android SDK 34
- `mobile/app/google-services.json` (committed) — the Android app config for
  the shared Firebase project (`com.nizkarya.app`). Firebase client config
  is not secret (it ships in every APK); access is enforced by Firestore
  rules. For Google sign-in later, add the shared keystore's SHA-1 to the
  Firebase Android app.

## Build & run

```bash
cd mobile
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
```

Or open the `mobile/` folder in Android Studio and press Run.

## CI

`.github/workflows/android.yml` compiles the app and runs unit tests on every
PR touching `mobile/`, and uploads the debug APK as a build artifact.

## Conventions

- Min SDK 24, target/compile SDK 34
- MVVM: Compose UI → ViewModel (StateFlow) → repository → Firestore
- Firestore document shapes must stay identical to `src/lib/types.ts` in the
  web app — both clients share the same data.
