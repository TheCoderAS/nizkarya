@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nizkarya.app.BuildConfig
import com.nizkarya.app.data.AuthRepo
import com.nizkarya.app.data.AuthState
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.DayStreak
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.CompactRow
import com.nizkarya.app.ui.components.SecondaryButton
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.SegmentedChoice
import com.nizkarya.app.ui.components.StatPill
import com.nizkarya.app.ui.components.streakColor
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.theme.AppSettings
import com.nizkarya.app.ui.theme.supportsDynamicColor

@Composable
fun ProfileScreen(
    user: AuthState.SignedIn,
    todos: List<Todo>,
    habits: List<Habit>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notify(
            scope,
            snackbar,
            if (granted) "Reminders are on." else "Reminders stay off until you allow them."
        )
    }

    val active = todos.filter { it.archivedAt == null }
    val pendingCount = active.count { it.status == "pending" }
    val completedCount = active.count { it.status == "completed" }
    val activeHabits = habits.filter { it.archivedAt == null }
    val bestStreak = activeHabits.maxOfOrNull { HabitLogic.currentStreak(it) } ?: 0
    val dayStreak = DayStreak.current(active)

    val initials = user.displayName.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2).joinToString("")
        .ifBlank { user.email.take(1).uppercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Identity
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.padding(horizontal = 8.dp))
            Column {
                Text(
                    text = user.displayName.ifBlank { "Your account" },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Stats
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatPill("$pendingCount", "To do")
                StatPill("$completedCount", "Done")
                StatPill("${activeHabits.size}", "Habits")
                StatPill("$dayStreak", "Day streak", streakColor())
                StatPill("$bestStreak", "Best habit")
            }
        }

        // Appearance
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                if (supportsDynamicColor) {
                    CompactRow(
                        leading = {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp).size(20.dp)
                            )
                        },
                        trailing = {
                            Switch(
                                checked = AppSettings.dynamicColor,
                                onCheckedChange = {
                                    AppSettings.setDynamicColor(context, it)
                                }
                            )
                        }
                    ) {
                        Text("Match my wallpaper", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Use the colours from your home screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoice(
                        options = listOf(
                            "system" to "Auto",
                            "light" to "Light",
                            "dark" to "Dark"
                        ),
                        selected = AppSettings.themeMode,
                        onSelect = { AppSettings.setThemeMode(context, it) }
                    )
                }
            }
        }

        // Reminders
        Text("Reminders", style = MaterialTheme.typography.titleMedium)
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            CompactRow(
                leading = {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp).size(20.dp)
                    )
                },
                trailing = {
                    SecondaryButton(
                        text = "Allow",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                notify(scope, snackbar, "Reminders are already on.")
                            }
                        }
                    )
                }
            ) {
                Text("Habit reminders", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Habits with a reminder time notify you on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        // Quiet and error-tinted: leaving is neither the page's action nor a
        // thing to make attractive, but it should read as consequential.
        TextButton(
            onClick = { AuthRepo.signOut() },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Sign out", style = MaterialTheme.typography.labelLarge)
        }

        Text(
            text = "NizKarya ${BuildConfig.VERSION_NAME} · Own your day",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Spacer(Modifier.height(28.dp))
    }
}
