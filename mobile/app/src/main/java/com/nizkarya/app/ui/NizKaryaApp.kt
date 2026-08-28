package com.nizkarya.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nizkarya.app.data.FocusRepo
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.RoutineRepo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.notifications.Reminders
import com.nizkarya.app.ui.screens.AuthScreen
import com.nizkarya.app.ui.screens.InsightsScreen
import com.nizkarya.app.ui.screens.PlanScreen
import com.nizkarya.app.ui.screens.ProfileScreen
import com.nizkarya.app.ui.screens.RoutinesScreen
import com.nizkarya.app.ui.screens.SetupScreen
import com.nizkarya.app.ui.screens.TodayScreen

private data class NizDestination(
    val routeBase: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    NizDestination("today", "Today", Icons.Filled.Home),
    NizDestination("plan", "Plan", Icons.Filled.Checklist),
    NizDestination("routines", "Routines", Icons.Filled.Repeat),
    NizDestination("profile", "Profile", Icons.Filled.Person)
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
        ) {
            CircularProgressIndicator()
        }
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
    val currentRoute = backStackEntry?.destination?.route

    val todos by remember(uid) { TodoRepo.observe(uid) }
        .collectAsState(initial = emptyList())
    val habits by remember(uid) { HabitRepo.observe(uid) }
        .collectAsState(initial = emptyList())
    val routines by remember(uid) { RoutineRepo.observe(uid) }
        .collectAsState(initial = emptyList())
    val activeFocus by remember(uid) { FocusRepo.observeActive(uid) }
        .collectAsState(initial = null)

    // Local, on-device habit reminders — rescheduled whenever habits change.
    LaunchedEffect(habits) {
        Reminders.ensureChannel(context)
        Reminders.scheduleHabitReminders(context, habits)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentRoute?.startsWith(destination.routeBase) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.routeBase) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(destination.icon, contentDescription = destination.label)
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("today") {
                TodayScreen(
                    user = user,
                    todos = todos,
                    habits = habits,
                    onStartFocus = { navController.navigate("plan?tab=focus") },
                    onOpenPlan = { navController.navigate("plan?tab=review") },
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
                    activeFocus = activeFocus,
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
