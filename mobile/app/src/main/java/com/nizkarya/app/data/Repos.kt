package com.nizkarya.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.logic.Recurrence
import java.time.Instant
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
     * Complete a task when all we have is its id, as from a notification
     * action. Reads first so recurring tasks still spawn their next occurrence.
     */
    suspend fun completeById(uid: String, todoId: String) {
        val snapshot = col(uid, "todos").document(todoId).get().await()
        if (!snapshot.exists()) return
        val todo = snapshot.toTodo()
        if (todo.status == "pending") toggleStatus(uid, todo)
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
                "description" to it.description
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

    /** Launch a routine: create one pending todo per template item, scheduled now. */
    suspend fun run(uid: String, routine: Routine) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        routine.items.forEach { item ->
            val ref = col(uid, "todos").document()
            batch.set(
                ref,
                hashMapOf(
                    "title" to item.title,
                    "scheduledDate" to Timestamp.now(),
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
    }
}
