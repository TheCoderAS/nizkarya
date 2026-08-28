# NizKarya — Android app

Native Android client for NizKarya (Kotlin + Jetpack Compose + Material 3),
sharing the same Firebase project (Auth + Firestore, `users/{uid}/…` schema)
as the web app in the repo root.

## Status

**Phase 1 — scaffold.** App shell, brand theme (dark/light), bottom
navigation (Today / Plan / Routines / Profile), Firebase SDK wired at the
dependency level. Feature screens land in later phases:

1. ~~Scaffold + theme + navigation~~ (this)
2. Auth (email + Google)
3. Firestore data layer (models, repositories, offline)
4. Todos (list, editor, swipe actions, filters, quick-add parser port)
5. Habits + routines (streaks/milestones port)
6. Today home + focus timer + insights
7. On-device reminders (AlarmManager + WorkManager — no server/FCM needed)
8. Polish + release

## Requirements

- Android Studio (Koala or newer) or JDK 17 + Android SDK 34
- `mobile/app/google-services.json` — from the Firebase console, register an
  **Android app** with package name `com.nizkarya.app` in the existing
  NizKarya Firebase project, then download the config file. It is
  git-ignored; the project still compiles without it (the Google Services
  plugin is applied conditionally), but Auth/Firestore need it at runtime.
  For Google sign-in (phase 2) also add your debug keystore's SHA-1 to the
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
