@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Repeat
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
import com.nizkarya.app.notifications.Reminders
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.screens.AuthScreen
import com.nizkarya.app.ui.screens.InsightsScreen
import com.nizkarya.app.ui.screens.PlanScreen
import com.nizkarya.app.ui.screens.ProfileScreen
import com.nizkarya.app.ui.screens.RoutinesScreen
import com.nizkarya.app.ui.screens.SetupScreen
import com.nizkarya.app.ui.screens.TodayScreen

private data class Destination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination("today", "Today", Icons.Filled.Home, Icons.Outlined.Home),
    Destination("plan", "Plan", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    Destination("routines", "Routines", Icons.Filled.Repeat, Icons.Outlined.Repeat),
    Destination("profile", "You", Icons.Filled.Person, Icons.Outlined.Person)
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

    LaunchedEffect(habits) {
        Reminders.ensureChannel(context)
        Reminders.scheduleHabitReminders(context, habits)
    }

    val isTopLevel = destinations.any { currentRoute.startsWith(it.route) }
    val screenTitle = when {
        currentRoute.startsWith("plan") -> "Plan"
        currentRoute.startsWith("routines") -> "Routines"
        currentRoute.startsWith("profile") -> "You"
        currentRoute.startsWith("insights") -> "Insights"
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
                                        Icons.AutoMirrored.Filled.ArrowBack,
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
                enterTransition = {
                    fadeIn(tween(220)) + slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(220)
                    )
                },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = {
                    fadeOut(tween(160)) + slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(220)
                    )
                }
            ) {
                composable("today") {
                    TodayScreen(
                        user = user,
                        todos = todos,
                        habits = habits,
                        onOpenReview = { navController.navigate("plan?tab=review") },
                        onOpenInsights = { navController.navigate("insights") }
                    )
                }
                composable("insights") { InsightsScreen(todos = todos, habits = habits) }
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
                composable("routines") { RoutinesScreen(uid = uid, routines = routines) }
                composable("profile") {
                    ProfileScreen(user = user, todos = todos, habits = habits)
                }
            }
        }
    }
}
