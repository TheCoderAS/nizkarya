@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.nizkarya.app.notifications.ReminderScheduler
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.screens.AuthScreen
import com.nizkarya.app.ui.screens.CalendarScreen
import com.nizkarya.app.ui.screens.InsightsScreen
import com.nizkarya.app.ui.screens.PlanScreen
import com.nizkarya.app.ui.screens.ProfileScreen
import com.nizkarya.app.ui.screens.RoutinesScreen
import com.nizkarya.app.ui.screens.SetupScreen
import com.nizkarya.app.ui.screens.TodayScreen
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long the first back press on the dashboard stays armed. */
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

/** Shared fade-through for switching between the bottom-bar destinations. */
private object NavFade {
    val enter = fadeIn(tween(190, delayMillis = 70)) +
        scaleIn(initialScale = 0.97f, animationSpec = tween(190, delayMillis = 70))
    val exit = fadeOut(tween(70))
}

private data class Destination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination("today", "Today", Icons.Rounded.Home, Icons.Outlined.Home),
    Destination("plan", "Plan", Icons.Rounded.CheckCircle, Icons.Outlined.CheckCircle),
    Destination("routines", "Routines", Icons.Rounded.Repeat, Icons.Outlined.Repeat),
    Destination("profile", "You", Icons.Rounded.Person, Icons.Outlined.Person)
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

    val isTopLevel = destinations.any { currentRoute.startsWith(it.route) }
    val onDashboard = currentRoute.startsWith("today")

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

    BackHandler(enabled = isTopLevel && !onDashboard) {
        navController.navigate("today") {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(enabled = onDashboard) {
        if (exitArmed) {
            activity?.finish()
        } else {
            exitArmed = true
            notify(scope, snackbarHostState, "Press back again to exit")
        }
    }

    val screenTitle = when {
        currentRoute.startsWith("plan") -> "Plan"
        currentRoute.startsWith("routines") -> "Routines"
        currentRoute.startsWith("profile") -> "You"
        currentRoute.startsWith("insights") -> "Insights"
        currentRoute.startsWith("calendar") -> "Calendar"
        else -> ""
    }

    CompositionLocalProvider(LocalSnackbar provides snackbarHostState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // Today draws its own large greeting header, so no bar there.
                if (screenTitle.isNotEmpty()) {
                    TopAppBar(
                        title = { Text(screenTitle) },
                        navigationIcon = {
                            if (!isTopLevel) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                if (isTopLevel) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        destinations.forEach { destination ->
                            val selected = currentRoute.startsWith(destination.route)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(destination.route) {
                                            popUpTo(
                                                navController.graph.findStartDestination().id
                                            ) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon
                                        else destination.icon,
                                        contentDescription = destination.label
                                    )
                                },
                                label = { Text(destination.label) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "today",
                modifier = Modifier.padding(innerPadding),
                // Fade-through, the Material pattern for sibling destinations:
                // the outgoing screen leaves quickly, the incoming one fades and
                // scales up very slightly behind it. A plain 90ms fade read as
                // no transition at all.
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
                        onOpenReview = { navController.navigate("plan?tab=review") },
                        onOpenTasks = { navController.navigate("plan?tab=todos") },
                        onOpenInsights = { navController.navigate("insights") },
                        onOpenDay = { navController.navigate("calendar?date=$it") }
                    )
                }
                // Insights is a real push, so it keeps the depth cue the tabs lost.
                composable(
                    route = "insights",
                    enterTransition = {
                        fadeIn(tween(120)) + slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(200)
                        )
                    },
                    popExitTransition = {
                        fadeOut(tween(120)) + slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(200)
                        )
                    }
                ) { InsightsScreen(todos = todos, habits = habits) }
                composable(
                    route = "plan?tab={tab}",
                    arguments = listOf(navArgument("tab") { defaultValue = "todos" })
                ) { entry ->
                    PlanScreen(
                        uid = uid,
                        todos = todos,
                        habits = habits,
                        initialTab = entry.arguments?.getString("tab") ?: "todos"
                    )
                }
                composable(
                    route = "calendar?date={date}",
                    arguments = listOf(navArgument("date") { defaultValue = "" }),
                    enterTransition = {
                        fadeIn(tween(120)) + slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(200)
                        )
                    },
                    popExitTransition = {
                        fadeOut(tween(120)) + slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(200)
                        )
                    }
                ) { entry ->
                    val raw = entry.arguments?.getString("date").orEmpty()
                    CalendarScreen(
                        uid = uid,
                        todos = todos,
                        habits = habits,
                        initialDate = runCatching { LocalDate.parse(raw) }
                            .getOrDefault(LocalDate.now())
                    )
                }
                composable("routines") { RoutinesScreen(uid = uid, routines = routines) }
                composable("profile") {
                    ProfileScreen(user = user, todos = todos, habits = habits)
                }
            }
        }
    }
}
