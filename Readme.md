# NizKarya: Own your day

A fast, focused daily-productivity app for tasks, habits, and routines.

**NizKarya is an Android app.** The React web app that used to live here was
retired; this repository now holds the Android source, a static landing page,
and the shared Firebase backend configuration.

- **Download:** [latest release](https://github.com/TheCoderAS/nizkarya/releases/latest)
- **Landing page:** https://next-gen-track.web.app

## Layout

| Path                      | What it is                                                     |
| ------------------------- | -------------------------------------------------------------- |
| `mobile/`                 | The Android app: Kotlin, Jetpack Compose, Material 3            |
| `site/`                   | Static landing page served by Firebase Hosting (no build step)  |
| `firestore.rules`         | Owner-scoped Firestore security rules                           |
| `firestore.indexes.json`  | Composite indexes required by the app's queries                 |
| `database.rules.json`     | Realtime Database rules                                         |
| `storage.rules`           | Cloud Storage rules (deny-all; the API is not enabled)          |

## The Android app

Kotlin + Jetpack Compose on Material 3, with Material You dynamic colour.
Firebase Auth (Google and email) and Cloud Firestore for storage; reminders are
scheduled on-device with `AlarmManager`, so no notification server is involved.

**Features**

- **Tasks**: natural-language quick add (`Gym tomorrow 6pm`), priorities, tags,
  notes, recurrence, and steps you can tick off straight from the list.
- **Habits**: daily through yearly frequencies, on-device reminders, streaks
  with optional forgiveness for missed days, and a seven-day strip on every row.
- **Routines**: reusable bundles of steps you can drop into today in one tap.
- **Review**: replan everything overdue into today's free slots; catch up on
  missed habits.
- **Insights**: completion trends, on-time vs. spillover, habit consistency.

### Build

```bash
cd mobile
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
```

`mobile/app/google-services.json` is committed for the `next-gen-track` Firebase
project. Google sign-in additionally requires the debug signing key's SHA-1 to be
registered on that project.

### Releases

Every merge to `main` that touches `mobile/` builds a signed APK and publishes a
GitHub Release, versioned `0.2.<run number>`. All releases are signed with the
same committed key (`mobile/keystore/`), so new versions install over old ones
in place and keep their data.

## The landing page

`site/` is plain HTML with inline CSS, with no bundler and no dependencies. It reads
the newest release from the GitHub API at page load to point its download button
at the current APK, falling back to `/releases/latest` if that call fails.

`site/sw.js` is a tombstone service worker. The retired PWA registered a caching
worker at that path; deleting the file would have left it installed and serving
the dead app shell from cache indefinitely, so this one unregisters itself and
clears those caches instead.

## CI and deployment

| Workflow                     | Trigger                          | What it does                                                       |
| ---------------------------- | -------------------------------- | ------------------------------------------------------------------ |
| `pull-request.yml`           | every PR                         | Validates the Firebase/rules JSON and the site's assets             |
| `android.yml`                | PRs touching `mobile/**`         | `assembleDebug` + unit tests, uploads the APK                       |
| `android-release.yml`        | merges to `main` touching `mobile/**` | Builds a signed APK and publishes a GitHub Release              |
| `firebase-hosting-merge.yml` | merges to `main` touching the site or rules | Deploys the landing page, then the Firestore/RTDB rules  |

`pull-request.yml` used to build the web app. Its job is still named
**Build Check** because that is a required status check on `main`; rather than
leave a no-op behind, it now verifies what actually ships: that the config
files parse, that `hosting.public` points at a directory containing
`index.html`, that every root-relative asset the page references exists, and
that nothing still refers to the removed web app.

The rules deploy is a separate step from the Hosting deploy because the Hosting
action publishes only the static site.
