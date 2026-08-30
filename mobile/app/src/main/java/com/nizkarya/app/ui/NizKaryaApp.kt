package com.nizkarya.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.FirebaseApp
import com.nizkarya.app.data.AuthRepo
import com.nizkarya.app.data.AuthState
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.RoutineRepo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.Insight
import com.nizkarya.app.notifications.ReminderScheduler
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.NavItem
import com.nizkarya.app.ui.components.NavPill
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.screens.AuthScreen
import com.nizkarya.app.ui.screens.HabitsScreen
import com.nizkarya.app.ui.screens.RoutineEditorScreen
import com.nizkarya.app.ui.screens.RoutinesScreen
import com.nizkarya.app.ui.screens.SetupScreen
import com.nizkarya.app.ui.screens.TasksScreen
import com.nizkarya.app.ui.screens.TodayScreen
import com.nizkarya.app.ui.screens.YouScreen
import com.nizkarya.app.ui.theme.Accents
import com.nizkarya.app.ui.theme.LocalAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long the first back press on the dashboard stays armed. */
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

/**
 * Switching between the four tabs.
 *
 * The incoming screen used to wait 70ms before starting, by which time the
 * outgoing one had already finished fading, so there was a beat with nothing
 * on screen. Combined with the work each screen used to do on the way in,
 * that read as a stutter followed by a pop. The two now overlap: the new
 * screen starts immediately and the old one leaves under it.
 */
private object NavFade {
    val enter = fadeIn(tween(200)) +
        scaleIn(initialScale = 0.985f, animationSpec = tween(200))
    val exit = fadeOut(tween(140))
}

// Four destinations, none of them containing tabs of their own. Today is the
// day, Tasks is everything scheduled, Habits is the streaks, You is progress
// and settings. Review, Routines, Insights and Calendar used to be their own
// places; each now sits inside the tab it belongs to.
private val destinations = listOf(
    NavItem("today", "Today", Icons.Outlined.Today, Icons.Rounded.Today, Accents.Task),
    NavItem(
        "tasks", "Tasks", Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle, Accents.Task
    ),
    NavItem("habits", "Habits", Icons.Outlined.Bolt, Icons.Rounded.Bolt, Accents.Habit),
    NavItem("you", "You", Icons.Outlined.BarChart, Icons.Rounded.BarChart, Accents.Streak)
)

@Composable
fun NizKaryaApp() {
    val context = LocalContext.current
    val configured = remember { FirebaseApp.getApps(context).isNotEmpty() }
    if (!configured) {
        SetupScreen()
        return
    }
    val authState by AuthRepo.state.collectAsState(initial = AuthState.Loading)
    when (val state = authState) {
        AuthState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        AuthState.SignedOut -> AuthScreen()
        is AuthState.SignedIn -> MainShell(state)
    }
}

@Composable
private fun MainShell(user: AuthState.SignedIn) {
    val uid = user.uid
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()
    val snackbarHostState = remember { SnackbarHostState() }

    val todos by remember(uid) { TodoRepo.observe(uid) }.collectAsState(initial = emptyList())
    val habits by remember(uid) { HabitRepo.observe(uid) }.collectAsState(initial = emptyList())
    val routines by remember(uid) { RoutineRepo.observe(uid) }
        .collectAsState(initial = emptyList())

    // Everything expensive, worked out once per change to the data rather
    // than once per visit to a tab. Screens used to do this for themselves
    // inside a remember, which a tab switch throws away.
    val insight = remember(todos, habits) { Insight.of(todos, habits) }

    // Only the fields that decide when something fires. Keying the pass on the
    // whole lists re-ran it for edits that cannot change a single alarm, such
    // as renaming a task or ticking a step.
    val reminderKey = remember(habits, todos) {
        buildString {
            habits.forEach {
                append(it.id).append(it.reminderTime).append(it.frequency)
                    .append(it.reminderDays).append(it.archivedAt?.seconds)
                    .append(it.completionDates.size).append(it.skippedDates.size).append('|')
            }
            todos.forEach {
                append(it.id).append(it.scheduledDate?.seconds).append(it.status)
                    .append(it.archivedAt?.seconds).append('|')
            }
        }
    }

    // Immediate pass while the app is open, plus a periodic worker so the
    // rolling window stays topped up when it is not. Both hop off the main
    // thread inside ReminderScheduler.
    LaunchedEffect(reminderKey) {
        ReminderScheduler.sync(context, habits, todos)
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { ReminderScheduler.enqueuePeriodicSync(context) }
    }

    val onDashboard = currentRoute == "today"
    // Routines is pushed, not a tab, so back there pops rather than jumping
    // to the dashboard.
    val onPushedScreen = destinations.none { it.route == currentRoute } &&
        currentRoute.isNotEmpty()

    // Back should never drop you out of the app by accident. Off the dashboard
    // it takes you to the dashboard; on the dashboard it takes two presses.
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var exitArmed by remember { mutableStateOf(false) }

    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(EXIT_CONFIRM_WINDOW_MS)
            exitArmed = false
        }
    }

    fun go(route: String) {
        if (route == currentRoute) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(enabled = onPushedScreen) { navController.popBackStack() }

    BackHandler(enabled = !onDashboard && !onPushedScreen) { go("today") }

    BackHandler(enabled = onDashboard) {
        if (exitArmed) {
            activity?.finish()
        } else {
            exitArmed = true
            notify(scope, snackbarHostState, "Press back again to exit")
        }
    }

    // Each tab owns an accent, provided once here so every button, check ring
    // and chip inside it inherits the right colour without being told.
    val accent = destinations.firstOrNull { it.route == currentRoute }?.accent ?: Accents.Task

    CompositionLocalProvider(
        LocalSnackbar provides snackbarHostState,
        LocalAccent provides accent
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!onPushedScreen) {
                    NavPill(
                        items = destinations,
                        currentRoute = currentRoute,
                        onSelect = { go(it.route) }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "today",
                // Only the top inset: the pill floats over the list, and every
                // list already reserves room for it in its bottom padding.
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                // Fade-through, the Material pattern for sibling destinations:
                // the outgoing screen leaves quickly, the incoming one fades
                // and scales up very slightly behind it.
                enterTransition = { NavFade.enter },
                exitTransition = { NavFade.exit },
                popEnterTransition = { NavFade.enter },
                popExitTransition = { NavFade.exit }
            ) {
                composable("today") {
                    TodayScreen(
                        user = user,
                        todos = todos,
                        habits = habits,
                        insight = insight,
                        onOpenHabits = { go("habits") }
                    )
                }
                composable("tasks") {
                    TasksScreen(
                        uid = uid,
                        todos = todos,
                        habits = habits,
                        routines = routines,
                        onOpenRoutines = { navController.navigate("routines") }
                    )
                }
                // A push, not a tab: it slides in and back pops it, which is
                // the cue that says "you went into something".
                composable(
                    route = "routines",
                    enterTransition = {
                        fadeIn(tween(120)) + slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(220)
                        )
                    },
                    popExitTransition = {
                        fadeOut(tween(120)) + slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(220)
                        )
                    }
                ) {
                    RoutinesScreen(
                        uid = uid,
                        routines = routines,
                        onBack = { navController.popBackStack() },
                        onEditRoutine = { id ->
                            navController.navigate("routine?id=" + (id ?: ""))
                        }
                    )
                }
                // Editing is its own screen rather than a sheet: a sheet
                // claims vertical drags to dismiss itself, which is exactly
                // the gesture the step handles need.
                composable(
                    route = "routine?id={id}",
                    arguments = listOf(navArgument("id") { defaultValue = "" }),
                    enterTransition = {
                        fadeIn(tween(120)) + slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(220)
                        )
                    },
                    popExitTransition = {
                        fadeOut(tween(120)) + slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(220)
                        )
                    }
                ) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    RoutineEditorScreen(
                        uid = uid,
                        existing = routines.firstOrNull { it.id == id },
                        onDone = { navController.popBackStack() }
                    )
                }
                composable("habits") {
                    HabitsScreen(uid = uid, habits = habits, insight = insight)
                }
                composable("you") {
                    YouScreen(user = user, insight = insight)
                }
            }
        }
    }
}
