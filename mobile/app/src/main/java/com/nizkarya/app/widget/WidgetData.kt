package com.nizkarya.app.widget

import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuth
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.HabitLogic
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One line on a widget: a task or a habit, already reduced to what draws. */
data class WidgetRow(
    val id: String,
    val isHabit: Boolean,
    val title: String,
    val time: String?,
    val done: Boolean,
    val late: Boolean
)

/**
 * Everything the widgets draw, resolved once per update.
 *
 * A widget cannot hold a Firestore listener, so it reads on each update
 * instead. Those reads come off the local cache first and only fall back to
 * the server when there is nothing on disk yet, because a home screen has no
 * patience for a round trip and the cache already carries every write made on
 * this phone.
 */
data class WidgetSnapshot(
    val signedIn: Boolean,
    val dateLabel: String,
    val done: Int,
    val total: Int,
    val rows: List<WidgetRow>,
    val habits: List<WidgetRow>
) {
    val allDone: Boolean get() = total > 0 && done == total

    companion object {
        val SignedOut = WidgetSnapshot(false, "", 0, 0, emptyList(), emptyList())
    }
}

object WidgetData {

    /**
     * How long one answer is worth reusing.
     *
     * A refresh redraws every widget on the home screen and each one asks for
     * the data separately, so a phone with all three placed used to do six
     * whole-collection reads for a single tick. They all want the same answer
     * within the same few milliseconds, so the first one pays and the rest
     * read it off this. Anything that changes the data calls [invalidate], so
     * this window can never hold a stale tick in front of you.
     */
    private const val REUSE_WINDOW_MS = 4_000L

    private val dateLabel = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    private val lock = Mutex()
    private var held: WidgetSnapshot? = null
    private var heldAt = 0L

    /** Throw the held answer away, because something just moved the data. */
    fun invalidate() {
        held = null
    }

    /**
     * Signed out is a state a widget has to draw, not a crash. It is also the
     * state right after an install, which is when someone is most likely to be
     * looking at the thing.
     */
    suspend fun load(): WidgetSnapshot = lock.withLock {
        val now = SystemClock.elapsedRealtime()
        val reusable = held
        if (reusable != null && now - heldAt < REUSE_WINDOW_MS) return@withLock reusable
        val fresh = read()
        held = fresh
        heldAt = now
        fresh
    }

    private suspend fun read(): WidgetSnapshot {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return WidgetSnapshot.SignedOut
        return runCatching { build(uid) }.getOrElse { WidgetSnapshot.SignedOut }
    }

    private suspend fun build(uid: String): WidgetSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = LocalTime.now(zone)

        // Off the disk, not off the network. A widget redraw that waits on the
        // radio is a widget redraw that does not happen.
        val todos = TodoRepo.fetchAllLocal(uid).filter { it.archivedAt == null }
        val habits = HabitRepo.fetchAllLocal(uid).filter { it.archivedAt == null }

        val todayTodos = todos.filter { todo ->
            val at = todo.scheduledDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()
            at == today
        }
        val todayHabits = habits.filter { HabitLogic.isScheduledToday(it) }

        val taskRows = todayTodos.map { todo ->
            val at = todo.scheduledDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalTime()
            val complete = todo.status == "completed"
            WidgetRow(
                id = todo.id,
                isHabit = false,
                title = todo.title,
                time = at?.format(clock),
                done = complete,
                late = !complete && at != null && at < now
            )
        }
        val habitRows = todayHabits.map { habit ->
            val at = habit.reminderTime.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            WidgetRow(
                id = habit.id,
                isHabit = true,
                title = habit.title,
                time = at?.format(clock),
                done = HabitLogic.isDoneToday(habit),
                late = false
            )
        }

        // Unfinished first and in time order, because a widget is a short list
        // and the next thing to do has to be in it. Finished work keeps its
        // place in the count but sinks to the bottom.
        val rows = (taskRows + habitRows).sortedWith(
            compareBy<WidgetRow> { it.done }
                .thenBy { it.time ?: "99:99" }
                .thenBy { it.title.lowercase() }
        )

        return WidgetSnapshot(
            signedIn = true,
            dateLabel = today.format(dateLabel),
            done = rows.count { it.done },
            total = rows.size,
            rows = rows,
            habits = habitRows.sortedWith(
                compareBy<WidgetRow> { it.done }.thenBy { it.time ?: "99:99" }
            )
        )
    }
}
