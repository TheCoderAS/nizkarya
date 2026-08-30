#!/usr/bin/env python3
"""
Used-but-not-imported sweep for the Android sources.

There is no Android SDK in the dev container, so the app cannot be compiled
locally and CI is the first thing that ever type-checks a change. This catches
the one failure mode that has actually broken CI here: a symbol used without
its import.

Matches call sites `Sym(`, lambda receivers `Sym {`, and member access `Sym.x`,
the last of which covers static-style calls like LocalDate.now(). An earlier
version omitted it and let exactly that through.
"""
import pathlib, re, subprocess, sys

BASE = "/home/user/todo-tracker"
# Modifier/scope extension functions: used as `.name(...)`, but the import is
# still required. The plain WATCH rule rejects dot-prefixed matches (to skip
# real member calls like launcher.launch), which made these invisible to the
# sweep: a missing `.clip` or `.combinedClickable` import sailed through.
# Scope members are deliberately absent: animateItem (LazyItemScope),
# matchParentSize (BoxScope), weight and align are interface members and
# need no import, so flagging them would be noise. Glance's defaultWeight is
# the same shape, a member of its RowScope and ColumnScope. Listing it here
# once cost a CI cycle, because the "fix" the rule suggests is to add an
# import that does not resolve. If a name is a scope member, it does not
# belong in this list.
EXTENSIONS = [
    # Draw and input
    "clip", "scale", "shadow", "alpha", "rotate", "graphicsLayer", "zIndex",
    "clickable", "combinedClickable", "pointerInput", "background", "border",
    # Layout. `size` was missing and a `Modifier.size(14.dp)` with no import
    # went straight to CI. The regex needs the parenthesis, so a plain
    # `list.size` property read is not matched and cannot false-positive.
    "size", "width", "height", "offset", "aspectRatio",
    "fillMaxWidth", "fillMaxHeight", "fillMaxSize",
    "heightIn", "widthIn", "sizeIn", "padding",
    "wrapContentWidth", "wrapContentHeight", "wrapContentSize",
    # Insets and scrolling
    "imePadding", "navigationBarsPadding", "statusBarsPadding",
    "systemBarsPadding", "safeDrawingPadding", "windowInsetsPadding",
    "verticalScroll", "horizontalScroll",
]

WATCH = [
    "remember", "mutableStateOf", "rememberCoroutineScope", "LaunchedEffect", "BackHandler",
    "clickable", "background", "border", "clip", "CircleShape", "AlertDialog", "ModalBottomSheet",
    "LocalDate", "LocalTime", "YearMonth", "DateTimeFormatter", "ZoneId", "TimeUnit", "Date",
    "CalendarLoad", "DayLoad", "HabitLogic", "HabitRepo", "TodoRepo", "DayStreak", "QuickAddParser",
    "CheckToggle", "CompactRow", "CompactIconButton", "EmptyState", "SectionLabel", "PriorityDot",
    "formatClock", "formatDue", "notify", "timestampLocalDate", "streakColor", "dueMeta",
    "launch", "delay", "withContext", "Dispatchers", "CoroutineScope", "SupervisorJob",
    "withTimeoutOrNull", "FirebaseAuth", "WorkManager", "PeriodicWorkRequestBuilder",
    "OneTimeWorkRequestBuilder", "ExistingPeriodicWorkPolicy", "CoroutineWorker", "WorkerParameters",
    "fadeIn", "fadeOut", "tween", "scaleIn", "navArgument", "AnimatedContentTransitionScope",
    "PendingIntent", "AlarmManager", "NotificationManager", "NotificationChannel", "Notification",
    "Icon", "Build", "Activity", "MainActivity", "Timestamp", "installSplashScreen",
    # Glance is a second render target with its own copies of names the app
    # already uses (Text, Column, Row, Box, Alignment), so an import taken from
    # the wrong package compiles nowhere and reads as correct.
    "GlanceModifier", "GlanceId", "ImageProvider", "LocalSize", "LocalContext",
    "ContentScale", "TextStyle", "FontWeight", "ColorProvider", "SizeMode",
    "provideContent", "actionRunCallback", "actionStartActivity",
    "actionParametersOf", "ActionParameters", "updateAll", "WidgetRefresh",
    "WidgetData", "WidgetLook", "TodayWidget", "NextUpWidget", "HabitsWidget",
    "LaunchIntents", "VoiceAddActivity", "QuickAddParser", "FirebaseAuth",
    "Toast", "RecognizerIntent", "TileService", "PendingIntent", "Intent",
    "lifecycleScope", "ActivityResultContracts", "ActivityResultLauncher",
]

# Modifier.padding has four overloads and they do not mix: horizontal/vertical
# belong to one, start/top/end/bottom to another. Combining them compiles
# nowhere and the error names no argument, only "none of the following
# candidates is applicable". Cost one CI cycle.
MIXED_PADDING = re.compile(
    r"\.padding\(\s*(?:[^()]*\b(?:horizontal|vertical)\b[^()]*\b(?:start|top|end|bottom)\b"
    r"|[^()]*\b(?:start|top|end|bottom)\b[^()]*\b(?:horizontal|vertical)\b)[^()]*\)",
    re.S,
)


# A KDoc that mentions `PendingIntent.getBroadcast` is describing a call, not
# making one. Scanning prose reported it as a missing import and sent me off to
# add one the file does not need, which is exactly the kind of noise that gets
# a checker ignored.
COMMENT_LINE = re.compile(r"^\s*(//|\*|/\*)")


def without_comments(text: str) -> str:
    """
    Prose out of the way before the symbol sweep.

    Only whole comment lines, and a trailing `//` on a line carrying no string
    at all, are dropped. That is every comment this codebase actually writes,
    and it leaves no room for a half-built tokenizer to mangle a raw string or
    a URL. Lines are blanked rather than removed so that the line numbers the
    other rules report stay true.
    """
    kept = []
    for line in text.split("\n"):
        if COMMENT_LINE.match(line):
            kept.append("")
            continue
        if '"' not in line and "'" not in line:
            line = line.split("//", 1)[0]
        kept.append(line)
    return "\n".join(kept)


def mixed_padding(path: str, text: str) -> list:
    return [
        f"{path}:{text[:m.start()].count(chr(10)) + 1}: mixed padding overloads "
        f"{m.group(0).strip()}"
        for m in MIXED_PADDING.finditer(text)
    ]


# Compose state read above its own declaration. The compiler calls this an
# unresolved reference, which reads like a missing import and is not one: the
# name exists, ten lines further down. Cost a CI cycle when a LaunchedEffect
# was inserted next to the wrong `var`.
DECL = re.compile(r"^\s*var (\w+) by remember", re.M)


def used_before_declared(path: str, text: str) -> list:
    lines = text.split("\n")
    found = []
    declared = {}
    for i, line in enumerate(lines):
        m = re.match(r"\s*var (\w+) by remember", line)
        if m and m.group(1) not in declared:
            declared[m.group(1)] = i
    for name, at in declared.items():
        for i in range(max(0, at - 60), at):
            if re.search(r"(?<![.\w])" + name + r"\s*=(?!=)", lines[i]):
                found.append(f"{path}:{i + 1}: '{name}' is written above its own "
                             f"declaration on line {at + 1}")
    return found


def kotlin_files():
    """
    Everything this branch touches, working tree included.

    Deliberately `diff origin/main` and not `diff origin/main...HEAD`: the
    three-dot form compares commits, so on a branch with nothing committed yet
    it silently returns nothing and the whole sweep passes vacuously. That is
    how a missing import reached CI once already.
    """
    changed = subprocess.run(["git", "diff", "--name-only", "origin/main"],
                             capture_output=True, text=True, cwd=BASE).stdout.split()
    untracked = subprocess.run(["git", "ls-files", "--others", "--exclude-standard"],
                               capture_output=True, text=True, cwd=BASE).stdout.split()
    return sorted({f for f in changed + untracked if f.endswith(".kt")})

def package_declarations() -> dict:
    """Top-level names per package. Same-package symbols need no import."""
    by_package: dict = {}
    for path in pathlib.Path(BASE, "mobile/app/src").rglob("*.kt"):
        src = path.read_text()
        match = re.search(r"^package ([\w.]+)", src, re.M)
        if not match:
            continue
        names = set(re.findall(
            r"^\s*(?:private\s+|internal\s+)?(?:data\s+|sealed\s+)?"
            r"(?:class|object|fun|enum class|val|const val)\s+(\w+)", src, re.M))
        by_package.setdefault(match.group(1), set()).update(names)
    return by_package


def main() -> int:
    problems = 0
    by_package = package_declarations()
    for rel in kotlin_files():
        path = pathlib.Path(BASE) / rel
        if not path.exists():
            continue
        src = path.read_text()
        package = (re.search(r"^package ([\w.]+)", src, re.M) or [None, ""])[1]
        same_package = by_package.get(package, set())
        body = "\n".join(l for l in src.splitlines() if not l.startswith("import "))
        body = without_comments(body)
        imported = set(re.findall(r"^import [\w.]*?\.(\w+)$", src, re.M))
        imported |= set(re.findall(r"^import [\w.]*?\.(\w+)\.Companion\.\w+$", src, re.M))
        declared = set(re.findall(
            r"^\s*(?:private\s+|internal\s+)?(?:data\s+|sealed\s+)?"
            r"(?:class|object|fun|enum class|val|const val)\s+(\w+)", src, re.M))
        for sym in dict.fromkeys(WATCH):
            # (?<![.\w]) rejects `foo.launch(` and `mylaunch(`.
            if re.search(r"(?<![.\w])" + sym + r"\s*[({.]", body):
                if sym not in imported and sym not in declared and sym not in same_package:
                    print(f"  MISSING import: {sym}  in {rel}")
                    problems += 1
        for sym in EXTENSIONS:
            if re.search(r"\." + sym + r"\s*\(", body):
                if sym not in imported and sym not in declared and sym not in same_package:
                    print(f"  MISSING extension import: {sym}  in {rel}")
                    problems += 1
        for line in mixed_padding(rel, body):
            print(f"  {line}")
            problems += 1
        for line in used_before_declared(rel, body):
            print(f"  {line}")
            problems += 1
    print("  clean" if not problems else f"  {problems} problem(s)")
    return 1 if problems else 0

if __name__ == "__main__":
    sys.exit(main())
