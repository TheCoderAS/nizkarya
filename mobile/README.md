# NizKarya: Android app

Native Android client for NizKarya (Kotlin + Jetpack Compose + Material 3),
backed by Firebase Auth and Cloud Firestore on the `users/{uid}/…` schema.

## What's in it

Four tabs, none of them containing tabs of their own.

- **Today**: the day as a timeline. An hour rail down the left, a now line at
  the current time, and tasks and habits placed at their times with a coloured
  leading edge saying which is which. Anything without a time sits under
  "Anytime". A catch-up card appears at the top only on days you are behind,
  and opens the replan sheet.
- **Tasks**: everything scheduled. A week strip that opens to a month, a strip
  of routines you can run into today with one tap, filters, and the full
  editor with steps, recurrence and tags. Steps tick straight from the list.
  Completing a recurring task spawns the next occurrence.
- **Habits**: all frequencies, streaks with optional forgiveness for missed
  days, a seven-day strip on every row, thirty-day consistency at the top, and
  archive, restore or delete.
- **You**: day streak, tasks done, habit consistency, a seven-day chart, and
  the settings (wallpaper colours, light or dark, notification permission,
  sign out).

Auth is email sign in, sign up and password reset, plus "Continue with
Google". Voice quick-add is the mic on Today, which feeds the system speech
recognizer through the natural-language parser.

Review, Routines, Insights and Calendar used to be destinations of their own,
and Habits was a tab inside a tab. Each now sits inside the tab it belongs to.

## Design system

The app has one visual language, defined once and inherited everywhere:

- **One accent per section**, in `ui/theme/Accents.kt` and provided through
  `LocalAccent`, so every button, check ring and chip inside a tab picks up
  its colour without being told. Tasks violet `#7C6CFF`, habits mint
  `#2ED3B7`, streaks amber `#FFB020`, overdue coral `#FF5F6D`. Each accent
  carries a darkened variant for the light theme, since one hue cannot be
  legible on both grounds.
- **A near-black canvas** (`#0A0A0F`) with surfaces stepping up through
  `#14141C` and `#1C1C26`. Light is a real mirror of it, not an inversion.
  Wallpaper colours (Material You) stay an opt-in from You, and the gradients
  derive from the active scheme when it is on.
- **Inter** (bundled, SIL OFL; licence at `mobile/InterFontLicense-OFL.txt`)
  on a scale that opens at 40 and steps widely: display 40 and 34, headline
  26 and 22, title 19 and 15.5, body 15 and 13.5, label 13 and 11.5. The old
  scale ran 30 down to 12 in small steps, so everything read at one weight and
  a dense screen felt cluttered. Hierarchy does that job now, not padding.
  Roboto lacks a SemiBold cut, which is why the weights never rendered as
  written before Inter.
- **Gradients only where they earn it**: the day's progress figure, the
  progress meter, the add button, the selected calendar day. Not on cards.
- Button hierarchy in `ui/components/Buttons.kt`: PrimaryCta (gradient, one
  per screen), SecondaryButton (tonal), GhostButton (every Cancel),
  DangerButton (error-filled, never for Cancel), AccentFab (the add button).
- **Every form control is the app's own**, in `ui/components/Fields.kt`. No
  `OutlinedTextField` survives anywhere in the app. Its boxed outline with a
  label that floats up and notches the border is a great deal of chrome for a
  line of text, and outlines fight the filled surfaces everything else is
  built from. Instead: `AppTextField` and `LabelledField` (filled, label above
  the field, placeholder inside, focus as a ring in the section accent),
  `TappableField` for anything that opens a picker so a date sits on the same
  material as the title above it, `IconAction` for a row's own remove or add,
  and `SegmentedChoice` as a filled track with an accent pill instead of
  Material's outlined capsule.
- **Floating pill navigation** (`ui/components/NavPill.kt`) instead of
  Material's full-bleed slab. The selected item wears its tab's accent, so the
  bar itself says where you are.
- Gestures: swipe a row start-to-end to complete, end-to-start to archive with
  Undo; long-press any row for its action sheet. Editors are bottom sheets
  that refuse to close over unsaved edits.

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

## Home screen

Four widgets, built with Glance, plus a Quick Settings tile.

- **Today**, resizable 4x2 to 4x4. Date, the day's count, a plus, then the
  day as a list that fills the height and scrolls past it. A tap on a circle
  ticks the thing off where it stands; the header opens the app.
- **Next up**, 4x1. The one thing you have not done yet.
- **Habits**, 4x2. Today's habits, tickable.
- **Quick add**, 4x1. The plus opens the editor. The mic goes to
  `VoiceAddActivity`, a transparent activity that runs the system recogniser,
  feeds the transcript through the same `QuickAddParser` the in-app mic uses
  and writes the task without the app appearing at all. The Quick Settings
  tile does the same thing from the notification shade.

Ticking from a widget goes through the repositories, so a recurring task still
spawns its next occurrence, exactly as the notification Done button already
did. The write happens with no Activity anywhere.

A widget cannot hold a Firestore listener, so it reads on each update and
those reads come from the local cache when offline. Widgets redraw when the
app is left, when a reminder action fires, and on the half-hour the platform
allows.

Widgets render through RemoteViews, which means no bundled Inter, no gradient
brushes and no Canvas. So they use flat accents and shape drawables, and they
commit to the dark surface in both themes: a widget sits on the wallpaper, not
on the app's canvas, and a translucent light card over a dark wallpaper is
unreadable. They are cousins of the app's look rather than a pixel match.

The launcher icon is adaptive with a monochrome layer, so it themes with the
wallpaper on Android 13 and up instead of staying full colour while every icon
around it changes.

## Signing and releases

- `keystore/nizkarya-debug.keystore` (committed) signs every debug and release
  build, so APKs upgrade in place across machines and CI. This is for debug
  distribution only; a Play Store release would use a separate private key.
- `.github/workflows/android-release.yml` builds a signed APK on every merge to
  main touching `mobile/` and publishes it as a GitHub Release.

### Versioning

`major` and `minor` live in `mobile/version.properties` and are bumped by
hand, so moving them is a deliberate, reviewable change. Bump `minor` for a
release that adds features and `major` for a milestone or a breaking change.

The third component is the release workflow's run number, not a patch level.
It is there because the release tag is derived from the version name, so two
releases can never produce the same tag, and because Android requires
`versionCode` to keep increasing for an in-place upgrade. `versionCode` is
`major * 1000000 + minor * 10000 + run`, which encodes the version and still
rises monotonically as long as major and minor only go up.

If per-change semantics ever matter more than this, the alternative is
deriving the bump from conventional commit prefixes, which would mean adopting
that commit convention across the repo.

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
