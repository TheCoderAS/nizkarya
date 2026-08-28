package com.nizkarya.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

// Firestore document shapes, kept identical to the web app's src/lib/types.ts
// so both clients read and write the same data.

data class Subtask(
    val id: String,
    val title: String,
    val completed: Boolean
)

data class Todo(
    val id: String,
    val title: String,
    val status: String, // pending | completed | skipped
    val scheduledDate: Timestamp?,
    val completedDate: Timestamp?,
    val archivedAt: Timestamp?,
    val createdAt: Timestamp?,
    val priority: String, // low | medium | high
    val tags: List<String>,
    val contextTags: List<String>,
    val description: String,
    val recurrence: String?, // daily | weekly | monthly | null
    val subtasks: List<Subtask>
)

data class Habit(
    val id: String,
    val title: String,
    val habitType: String, // positive | avoid
    val reminderTime: String, // "HH:mm" or ""
    val reminderDays: List<Int>,
    val completionDates: List<String>, // "yyyy-MM-dd" keys
    val skippedDates: List<String>,
    val timezone: String?,
    val frequency: String, // daily | weekly | monthly | quarterly | half-yearly | yearly
    val graceMisses: Int,
    val contextTags: List<String>,
    val triggerAfterHabitId: String?,
    val createdAt: Timestamp?,
    val archivedAt: Timestamp?
)

data class RoutineItem(
    val title: String,
    val priority: String,
    val tags: List<String>,
    val contextTags: List<String>,
    val description: String
)

data class Routine(
    val id: String,
    val title: String,
    val items: List<RoutineItem>,
    val createdAt: Timestamp?
)

private fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

private fun DocumentSnapshot.intList(field: String): List<Int> =
    (get(field) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

fun DocumentSnapshot.toTodo(): Todo = Todo(
    id = id,
    title = getString("title") ?: "",
    status = getString("status") ?: "pending",
    scheduledDate = getTimestamp("scheduledDate"),
    completedDate = getTimestamp("completedDate"),
    archivedAt = getTimestamp("archivedAt"),
    createdAt = getTimestamp("createdAt"),
    priority = getString("priority") ?: "medium",
    tags = stringList("tags"),
    contextTags = stringList("contextTags"),
    description = getString("description") ?: "",
    recurrence = getString("recurrence"),
    subtasks = (get("subtasks") as? List<*>)?.mapNotNull { raw ->
        (raw as? Map<*, *>)?.let { m ->
            Subtask(
                id = m["id"] as? String ?: "",
                title = m["title"] as? String ?: "",
                completed = m["completed"] as? Boolean ?: false
            )
        }
    } ?: emptyList()
)

fun DocumentSnapshot.toHabit(): Habit = Habit(
    id = id,
    title = getString("title") ?: "",
    habitType = getString("habitType") ?: "positive",
    reminderTime = getString("reminderTime") ?: "",
    reminderDays = intList("reminderDays"),
    completionDates = stringList("completionDates"),
    skippedDates = stringList("skippedDates"),
    timezone = getString("timezone"),
    frequency = getString("frequency") ?: "daily",
    graceMisses = (getLong("graceMisses") ?: 0L).toInt(),
    contextTags = stringList("contextTags"),
    triggerAfterHabitId = getString("triggerAfterHabitId"),
    createdAt = getTimestamp("createdAt"),
    archivedAt = getTimestamp("archivedAt")
)

fun DocumentSnapshot.toRoutine(): Routine = Routine(
    id = id,
    title = getString("title") ?: "",
    items = (get("items") as? List<*>)?.mapNotNull { raw ->
        (raw as? Map<*, *>)?.let { m ->
            RoutineItem(
                title = m["title"] as? String ?: "",
                priority = m["priority"] as? String ?: "medium",
                tags = (m["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                contextTags = (m["contextTags"] as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList(),
                description = m["description"] as? String ?: ""
            )
        }
    } ?: emptyList(),
    createdAt = getTimestamp("createdAt")
)
