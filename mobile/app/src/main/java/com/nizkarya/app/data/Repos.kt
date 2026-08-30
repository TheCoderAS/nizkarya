package com.nizkarya.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.nizkarya.app.logic.DayPlanner
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.logic.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private fun col(uid: String, name: String): CollectionReference =
    FirebaseFirestore.getInstance()
        .collection("users").document(uid).collection(name)

private fun subtasksToMaps(subtasks: List<Subtask>): List<Map<String, Any>> =
    subtasks.map {
        mapOf("id" to it.id, "title" to it.title, "completed" to it.completed)
    }

object TodoRepo {

    fun observe(uid: String): Flow<List<Todo>> = callbackFlow {
        val registration = col(uid, "todos")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.documents.map { it.toTodo() })
                }
            }
        awaitClose { registration.remove() }
    }

    private fun writeFields(
        title: String,
        scheduledDate: Timestamp?,
        priority: String,
        tags: List<String>,
        contextTags: List<String>,
        description: String,
        recurrence: String?,
        subtasks: List<Subtask>,
        uid: String
    ): HashMap<String, Any?> = hashMapOf(
        "title" to title,
        "scheduledDate" to scheduledDate,
        "priority" to priority,
        "tags" to tags,
        "contextTags" to contextTags,
        "description" to description,
        "recurrence" to recurrence,
        "subtasks" to subtasksToMaps(subtasks),
        "status" to "pending",
        "completedDate" to null,
        "skippedAt" to null,
        "author_uid" to uid
    )

    suspend fun add(
        uid: String,
        title: String,
        scheduledDate: Timestamp?,
        priority: String,
        tags: List<String>,
        contextTags: List<String>,
        description: String,
        recurrence: String?,
        subtasks: List<Subtask>
    ) {
        val data = writeFields(
            title, scheduledDate, priority, tags, contextTags,
            description, recurrence, subtasks, uid
        )
        data["createdAt"] = FieldValue.serverTimestamp()
        col(uid, "todos").add(data).await()
    }

    suspend fun update(
        uid: String,
        todoId: String,
        title: String,
        scheduledDate: Timestamp?,
        priority: String,
        tags: List<String>,
        contextTags: List<String>,
        description: String,
        recurrence: String?,
        subtasks: List<Subtask>
    ) {
        val data = writeFields(
            title, scheduledDate, priority, tags, contextTags,
            description, recurrence, subtasks, uid
        )
        data["updatedAt"] = FieldValue.serverTimestamp()
        col(uid, "todos").document(todoId).update(data as Map<String, Any?>).await()
    }

    /**
     * Complete/reopen. Completing a recurring todo also spawns the next
     * occurrence, matching the web app.
     */
    suspend fun toggleStatus(uid: String, todo: Todo) {
        val ref = col(uid, "todos").document(todo.id)
        if (todo.status == "pending") {
            ref.update(
                mapOf(
                    "status" to "completed",
                    "completedDate" to FieldValue.serverTimestamp(),
                    "skippedAt" to null
                )
            ).await()
            val recurrence = todo.recurrence
            if (recurrence != null && recurrence != "none") {
                val baseInstant = todo.scheduledDate?.toDate()?.toInstant() ?: Instant.now()
                val nextInstant = Recurrence.next(baseInstant, recurrence)
                val data = writeFields(
                    todo.title,
                    Timestamp(Date.from(nextInstant)),
                    todo.priority,
                    todo.tags,
                    todo.contextTags,
                    todo.description,
                    recurrence,
                    todo.subtasks.map { it.copy(completed = false) },
                    uid
                )
                data["createdAt"] = FieldValue.serverTimestamp()
                col(uid, "todos").add(data).await()
            }
        } else {
            ref.update(
                mapOf(
                    "status" to "pending",
                    "completedDate" to null,
                    "skippedAt" to null
                )
            ).await()
        }
    }

    /**
     * Tick a single step without opening the editor. Rewrites the whole
     * subtasks array because Firestore can't patch one element of a list.
     */
    suspend fun setSubtaskCompleted(
        uid: String,
        todo: Todo,
        subtaskId: String,
        completed: Boolean
    ) {
        val updated = todo.subtasks.map {
            if (it.id == subtaskId) it.copy(completed = completed) else it
        }
        col(uid, "todos").document(todo.id).update(
            mapOf(
                "subtasks" to subtasksToMaps(updated),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** One-shot read for background scheduling, where there is no listener. */
    suspend fun fetchAll(uid: String): List<Todo> =
        col(uid, "todos").get().await().documents.map { it.toTodo() }

    /**
     * The same read, but off the disk first.
     *
     * A widget has a fraction of a second before the launcher gives up waiting
     * for it. The default source goes to the server first and only falls back
     * to the cache after its own timeout, so a home screen redraw ended up
     * parked on the radio for data that was already sitting on the disk. That
     * cache is also the authoritative view for anything typed on this phone,
     * because Firestore applies a local write to it before the server has said
     * anything. The server read stays as the fallback for a cold cache, which
     * is really only the first draw after an install.
     */
    suspend fun fetchAllLocal(uid: String): List<Todo> {
        val cached = runCatching { col(uid, "todos").get(Source.CACHE).await() }.getOrNull()
        val documents = cached?.documents.orEmpty()
        if (documents.isNotEmpty()) return documents.map { it.toTodo() }
        return fetchAll(uid)
    }

    /**
     * Find one task when something needs to know whether it is still there, as
     * a reminder does at the moment it is about to fire.
     *
     * The three outcomes are all different and the caller has to be able to
     * tell them apart: success with a task, success with null meaning the task
     * is genuinely gone, and a failure meaning we could not find out. Silencing
     * a real reminder because a lookup failed would be worse than showing a
     * stale one, so the last case must never be read as gone.
     *
     * Cache first, because this runs on an alarm wake. Firestore keeps a
     * tombstone for a document it watched get deleted, so a delete this phone
     * has seen answers off the disk. Only a task the cache has never heard of
     * costs a round trip.
     */
    suspend fun lookup(uid: String, todoId: String): Result<Todo?> {
        val ref = col(uid, "todos").document(todoId)
        runCatching { ref.get(Source.CACHE).await() }
            .onSuccess { return Result.success(if (it.exists()) it.toTodo() else null) }
        return runCatching { ref.get().await() }.map { if (it.exists()) it.toTodo() else null }
    }

    /**
     * Complete a task when all we have is its id, as from a notification
     * action. Reads first so recurring tasks still spawn their next occurrence.
     */
    suspend fun completeById(uid: String, todoId: String) {
        val snapshot = col(uid, "todos").document(todoId).get().await()
        if (!snapshot.exists()) return
        val todo = snapshot.toTodo()
        if (todo.status == "pending") toggleStatus(uid, todo)
    }

    /**
     * Complete a task from somewhere that cannot afford to wait: a widget tap
     * or a notification button.
     *
     * Same effect as [completeById], including spawning the next occurrence of
     * a recurring task, but it never blocks on the network. The lookup comes
     * out of the local cache and the writes are handed to Firestore without
     * waiting for the acknowledgement, because Firestore applies them to that
     * same cache first and replays them from its own queue when the connection
     * comes back.
     *
     * Waiting for the acknowledgement is what used to make a tap on a widget
     * take minutes. The redraw sat behind it, and the context doing the waiting
     * gets reclaimed after about ten seconds, so on a weak connection the write
     * landed later and the redraw never happened at all.
     */
    suspend fun completeByIdLocal(uid: String, todoId: String) {
        val ref = col(uid, "todos").document(todoId)
        val snapshot = runCatching { ref.get(Source.CACHE).await() }.getOrNull()
            ?: runCatching { ref.get().await() }.getOrNull()
            ?: return
        if (!snapshot.exists()) return
        val todo = snapshot.toTodo()
        if (todo.status != "pending") return

        ref.update(
            mapOf(
                "status" to "completed",
                "completedDate" to FieldValue.serverTimestamp(),
                "skippedAt" to null
            )
        )

        val recurrence = todo.recurrence
        if (recurrence != null && recurrence != "none") {
            val baseInstant = todo.scheduledDate?.toDate()?.toInstant() ?: Instant.now()
            val nextInstant = Recurrence.next(baseInstant, recurrence)
            val data = writeFields(
                todo.title,
                Timestamp(Date.from(nextInstant)),
                todo.priority,
                todo.tags,
                todo.contextTags,
                todo.description,
                recurrence,
                todo.subtasks.map { it.copy(completed = false) },
                uid
            )
            data["createdAt"] = FieldValue.serverTimestamp()
            col(uid, "todos").add(data)
        }
    }

    suspend fun delete(uid: String, todoId: String) {
        col(uid, "todos").document(todoId).delete().await()
    }

    suspend fun skip(uid: String, todoId: String) {
        col(uid, "todos").document(todoId).update(
            mapOf("status" to "skipped", "skippedAt" to FieldValue.serverTimestamp())
        ).await()
    }

    suspend fun archive(uid: String, todoId: String) {
        col(uid, "todos").document(todoId).update(
            mapOf("archivedAt" to FieldValue.serverTimestamp())
        ).await()
    }

    /** Undo for a swipe-archive. */
    suspend fun unarchive(uid: String, todoId: String) {
        col(uid, "todos").document(todoId).update(
            mapOf("archivedAt" to null)
        ).await()
    }

    /**
     * "Replan my day": batch-move overdue todos into half-hour slots starting
     * from the next half-hour boundary today.
     */
    suspend fun replanIntoToday(uid: String, overdue: List<Todo>) {
        if (overdue.isEmpty()) return
        val zone = java.time.ZoneId.systemDefault()
        val slots = com.nizkarya.app.logic.DayPlanner.slots(
            overdue.size, java.time.LocalDateTime.now(zone)
        )
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        overdue.forEachIndexed { index, todo ->
            val ref = col(uid, "todos").document(todo.id)
            val instant = slots[index].atZone(zone).toInstant()
            batch.update(
                ref,
                mapOf(
                    "scheduledDate" to Timestamp(Date.from(instant)),
                    "status" to "pending",
                    "skippedAt" to null
                )
            )
        }
        batch.commit().await()
    }

    /** Move an overdue todo to today, keeping its original wall-clock time. */
    suspend fun rescheduleToToday(uid: String, todo: Todo) {
        val zone = java.time.ZoneId.systemDefault()
        val oldTime = todo.scheduledDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalTime()
            ?: java.time.LocalTime.of(9, 0)
        val newInstant = java.time.LocalDate.now(zone).atTime(oldTime).atZone(zone).toInstant()
        col(uid, "todos").document(todo.id).update(
            mapOf(
                "scheduledDate" to Timestamp(Date.from(newInstant)),
                "status" to "pending",
                "skippedAt" to null
            )
        ).await()
    }
}

object HabitRepo {

    fun observe(uid: String): Flow<List<Habit>> = callbackFlow {
        val registration = col(uid, "habits")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.documents.map { it.toHabit() })
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun save(
        uid: String,
        editingId: String?,
        title: String,
        habitType: String,
        frequency: String,
        reminderDays: List<Int>,
        reminderTime: String,
        graceMisses: Int,
        contextTags: List<String>
    ) {
        val data = hashMapOf<String, Any?>(
            "title" to title,
            "habitType" to habitType,
            "frequency" to frequency,
            "reminderDays" to reminderDays,
            "reminderTime" to reminderTime,
            "graceMisses" to graceMisses,
            "contextTags" to contextTags,
            "author_uid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (editingId != null) {
            col(uid, "habits").document(editingId).update(data as Map<String, Any?>).await()
        } else {
            data["completionDates"] = emptyList<String>()
            data["skippedDates"] = emptyList<String>()
            data["timezone"] = java.time.ZoneId.systemDefault().id
            data["triggerAfterHabitId"] = null
            data["archivedAt"] = null
            data["createdAt"] = FieldValue.serverTimestamp()
            col(uid, "habits").add(data).await()
        }
    }

    suspend fun toggleToday(uid: String, habit: Habit) {
        val key = HabitLogic.todayKey(habit)
        val done = key in habit.completionDates
        col(uid, "habits").document(habit.id).update(
            mapOf(
                "completionDates" to
                    if (done) FieldValue.arrayRemove(key) else FieldValue.arrayUnion(key),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** One-shot read for background scheduling, where there is no listener. */
    suspend fun fetchAll(uid: String): List<Habit> =
        col(uid, "habits").get().await().documents.map { it.toHabit() }

    /** Off the disk first. See [TodoRepo.fetchAllLocal] for why. */
    suspend fun fetchAllLocal(uid: String): List<Habit> {
        val cached = runCatching { col(uid, "habits").get(Source.CACHE).await() }.getOrNull()
        val documents = cached?.documents.orEmpty()
        if (documents.isNotEmpty()) return documents.map { it.toHabit() }
        return fetchAll(uid)
    }

    /** Find one habit, with the same three outcomes as [TodoRepo.lookup]. */
    suspend fun lookup(uid: String, habitId: String): Result<Habit?> {
        val ref = col(uid, "habits").document(habitId)
        runCatching { ref.get(Source.CACHE).await() }
            .onSuccess { return Result.success(if (it.exists()) it.toHabit() else null) }
        return runCatching { ref.get().await() }.map { if (it.exists()) it.toHabit() else null }
    }

    /**
     * [markDoneOn] without waiting for the server, for widget taps and
     * notification buttons. Firestore works the union out locally, so the very
     * next read sees the habit as kept. See [TodoRepo.completeByIdLocal].
     */
    fun markDoneOnLocal(uid: String, habitId: String, dateKey: String) {
        col(uid, "habits").document(habitId).update(
            mapOf(
                "completionDates" to FieldValue.arrayUnion(dateKey),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
    }

    suspend fun markDoneOn(uid: String, habitId: String, dateKey: String) {
        col(uid, "habits").document(habitId).update(
            mapOf(
                "completionDates" to FieldValue.arrayUnion(dateKey),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun skipOn(uid: String, habitId: String, dateKey: String) {
        col(uid, "habits").document(habitId).update(
            mapOf(
                "skippedDates" to FieldValue.arrayUnion(dateKey),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun setArchived(uid: String, habitId: String, archived: Boolean) {
        col(uid, "habits").document(habitId).update(
            mapOf(
                "archivedAt" to if (archived) FieldValue.serverTimestamp() else null,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun deletePermanently(uid: String, habitId: String) {
        col(uid, "habits").document(habitId).delete().await()
    }
}

object RoutineRepo {

    fun observe(uid: String): Flow<List<Routine>> = callbackFlow {
        val registration = col(uid, "routines")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.documents.map { it.toRoutine() })
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun save(uid: String, editingId: String?, title: String, items: List<RoutineItem>) {
        val itemMaps = items.map {
            mapOf(
                "title" to it.title,
                "priority" to it.priority,
                "tags" to it.tags,
                "contextTags" to it.contextTags,
                "description" to it.description,
                "time" to it.time
            )
        }
        val data = hashMapOf<String, Any?>(
            "title" to title,
            "items" to itemMaps,
            "author_uid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (editingId != null) {
            col(uid, "routines").document(editingId).update(data as Map<String, Any?>).await()
        } else {
            data["createdAt"] = FieldValue.serverTimestamp()
            col(uid, "routines").add(data).await()
        }
    }

    suspend fun delete(uid: String, routineId: String) {
        col(uid, "routines").document(routineId).delete().await()
    }

    /**
     * Launch a routine into today, returning the ids it created so the run
     * can be undone.
     *
     * Steps that carry a time land on that time today. Steps that do not are
     * laid out from the next half hour, thirty minutes apart, by [DayPlanner].
     * Every step used to be stamped with the same instant, which meant a five
     * step morning routine produced five tasks due at the same minute and
     * five reminders firing at once.
     */
    suspend fun run(uid: String, routine: Routine): List<String> {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        val created = mutableListOf<String>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val slots = DayPlanner.slots(
            count = routine.items.count { it.time.isBlank() },
            now = LocalDateTime.now(zone)
        )
        var nextSlot = 0
        routine.items.forEach { item ->
            val at = item.time.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val moment = if (at != null) {
                today.atTime(at)
            } else {
                slots.getOrNull(nextSlot++) ?: LocalDateTime.now(zone)
            }
            val ref = col(uid, "todos").document()
            created += ref.id
            batch.set(
                ref,
                hashMapOf(
                    "title" to item.title,
                    "scheduledDate" to Timestamp(
                        Date.from(moment.atZone(zone).toInstant())
                    ),
                    "priority" to item.priority,
                    "tags" to item.tags,
                    "contextTags" to item.contextTags,
                    "description" to item.description,
                    "recurrence" to null,
                    "subtasks" to emptyList<Map<String, Any>>(),
                    "status" to "pending",
                    "completedDate" to null,
                    "skippedAt" to null,
                    "author_uid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        return created
    }

    /**
     * Undo a run. Running a routine writes several tasks at once, which is
     * the easiest thing in the app to do by accident, so it has to be
     * reversible in one tap.
     */
    suspend fun undoRun(uid: String, todoIds: List<String>) {
        if (todoIds.isEmpty()) return
        val batch = FirebaseFirestore.getInstance().batch()
        todoIds.forEach { batch.delete(col(uid, "todos").document(it)) }
        batch.commit().await()
    }
}
