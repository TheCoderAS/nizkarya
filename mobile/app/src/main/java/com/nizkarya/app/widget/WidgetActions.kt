package com.nizkarya.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.google.firebase.auth.FirebaseAuth
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.TodoRepo
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Which row was tapped. Parameters are the only state an action gets. */
object WidgetKeys {
    val Id = ActionParameters.Key<String>("rowId")
    val IsHabit = ActionParameters.Key<Boolean>("rowIsHabit")
}

/**
 * Tick something off without opening the app.
 *
 * This is the same trick the notification Done button already uses: the write
 * goes straight to Firestore from a background context, with no Activity
 * anywhere. Completing a recurring task still spawns its next occurrence,
 * because it goes through the same repository call the app does.
 */
class ToggleRowAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val id = parameters[WidgetKeys.Id]
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (id != null && uid != null) {
            val isHabit = parameters[WidgetKeys.IsHabit] ?: false
            runCatching {
                if (isHabit) {
                    HabitRepo.markDoneOn(uid, id, LocalDate.now().toString())
                } else {
                    TodoRepo.completeById(uid, id)
                }
            }
        }
        WidgetRefresh.refreshAll(context)
    }
}

/** Redraw every widget, whatever caused the data to move. */
object WidgetRefresh {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun refreshAll(context: Context) {
        runCatching { TodayWidget().updateAll(context) }
        runCatching { NextUpWidget().updateAll(context) }
        runCatching { HabitsWidget().updateAll(context) }
    }

    /**
     * Fire and forget, for the places that notice a change but cannot suspend:
     * the reminder receiver, the scheduler, leaving the app.
     */
    fun request(context: Context) {
        val app = context.applicationContext
        scope.launch { refreshAll(app) }
    }
}
