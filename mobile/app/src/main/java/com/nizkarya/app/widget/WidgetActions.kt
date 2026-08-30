package com.nizkarya.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
 * The write goes straight to Firestore from a background context, with no
 * Activity anywhere. Completing a recurring task still spawns its next
 * occurrence, because it goes through the same repository logic the app uses.
 *
 * What it deliberately does not do is wait for the server to acknowledge that
 * write before redrawing. This callback runs on a worker the system reclaims
 * after a few seconds, so waiting on the network meant that on a weak
 * connection the redraw was killed before it ran and the tick appeared to do
 * nothing at all, sometimes for half an hour. Firestore applies the change to
 * its local cache first and replays it from its own queue later, so drawing
 * from that cache immediately is both faster and honest.
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
                    HabitRepo.markDoneOnLocal(uid, id, LocalDate.now().toString())
                } else {
                    TodoRepo.completeByIdLocal(uid, id)
                }
            }
        }
        WidgetRefresh.refreshAll(context)
    }
}

/**
 * A widget receiver that treats the system's own update as a chance to catch
 * up with the server.
 *
 * Everything else the widgets do reads the local cache, which is right: it is
 * instant and it already holds every change made on this phone. What it cannot
 * hold is a change made somewhere else while the app was shut, because there
 * is no listener running to be told. This update arrives on the platform's
 * schedule and nobody is waiting on it, so it is the one that can afford to
 * ask.
 */
abstract class SyncingWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetData.requestServerRead()
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

/** Redraw the widgets, whatever caused the data to move. */
object WidgetRefresh {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Redraw the widgets that are actually on a home screen.
     *
     * updateAll used to be called on all three classes unconditionally, and
     * each one re-runs provideGlance and reads the data again. On a phone with
     * one widget placed that was three quarters wasted work, sitting in the
     * middle of the path a tap has to travel.
     */
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        WidgetData.invalidate()
        redraw(app, TodayWidget())
        redraw(app, NextUpWidget())
        redraw(app, HabitsWidget())
    }

    private suspend fun redraw(context: Context, widget: GlanceAppWidget) {
        runCatching {
            val placed = GlanceAppWidgetManager(context)
                .getGlanceIds(widget.javaClass)
                .isNotEmpty()
            if (placed) widget.updateAll(context)
        }.onFailure {
            // Looking a provider up needs it to be resolvable. If that ever
            // fails, update the class outright rather than silently leaving a
            // stale widget sitting on someone's home screen.
            runCatching { widget.updateAll(context) }
        }
    }

    /**
     * Fire and forget, for the places that notice a change but cannot suspend:
     * leaving the app, and the voice add screen on its way out.
     */
    fun request(context: Context) {
        val app = context.applicationContext
        scope.launch { refreshAll(app) }
    }
}
