package com.dailythread.app.data.repository

import androidx.room.withTransaction
import com.dailythread.app.data.local.AppDatabase
import com.dailythread.app.data.local.entity.TaskEntity
import java.time.LocalDate
import java.util.UUID

class TaskRepository(
    private val db: AppDatabase,
    private val tokenStore: TokenStore,
    private val deviceId: String
) {
    private val queue = MutationQueue(db, deviceId)

    fun observe(userId: String, date: LocalDate) = db.taskDao().observeForDate(userId, date.toString())

    suspend fun add(
        title: String,
        priority: String = "MEDIUM",
        date: LocalDate = today(),
        estimateMinutes: Int = 0
    ) {
        val cleaned = title.trim()
        require(cleaned.isNotEmpty()) { "Nama tugas wajib diisi." }
        val userId = requireNotNull(tokenStore.userId()) { "Login required" }
        val now = nowIso()
        val id = UUID.randomUUID().toString()
        val normalizedPriority = priority.uppercase().takeIf { it in setOf("LOW", "MEDIUM", "HIGH") } ?: "MEDIUM"
        val item = TaskEntity(
            id = id,
            userId = userId,
            scheduledDate = date.toString(),
            title = cleaned,
            priority = normalizedPriority,
            estimateMinutes = estimateMinutes.coerceAtLeast(0),
            originDeviceId = deviceId,
            createdAt = now,
            updatedAt = now
        )
        db.withTransaction {
            db.taskDao().upsert(item)
            queue.enqueue("task", id, "CREATE", 0, item.toPayload())
        }
    }

    suspend fun update(id: String, title: String, priority: String, description: String = "") {
        val old = db.taskDao().byId(id) ?: return
        val p = priority.uppercase().takeIf { it in setOf("LOW", "MEDIUM", "HIGH") } ?: old.priority
        val item = old.copy(
            title = title.trim().ifEmpty { old.title },
            description = description.trim(),
            priority = p,
            updatedAt = nowIso(),
            syncState = "PENDING"
        )
        db.withTransaction {
            db.taskDao().upsert(item)
            queue.enqueue("task", id, "UPDATE", old.version, item.toPayload())
        }
    }

    suspend fun toggleDone(id: String) {
        val old = db.taskDao().byId(id) ?: return
        val status = if (old.status == "DONE") "TODO" else "DONE"
        val item = old.copy(status = status, updatedAt = nowIso(), syncState = "PENDING")
        db.withTransaction {
            db.taskDao().upsert(item)
            queue.enqueue("task", id, "UPDATE", old.version, mapOf("status" to status))
        }
    }

    suspend fun moveToTomorrow(id: String) {
        val old = db.taskDao().byId(id) ?: return
        val base = old.scheduledDate?.let(LocalDate::parse) ?: today()
        val next = base.plusDays(1).toString()
        val item = old.copy(scheduledDate = next, status = "TODO", updatedAt = nowIso(), syncState = "PENDING")
        db.withTransaction {
            db.taskDao().upsert(item)
            queue.enqueue("task", id, "UPDATE", old.version, mapOf("scheduled_date" to next, "status" to "TODO"))
        }
    }

    suspend fun delete(id: String) {
        val old = db.taskDao().byId(id) ?: return
        if (old.deletedAt != null) return
        val deletedAt = nowIso()
        db.withTransaction {
            db.taskDao().upsert(old.copy(deletedAt = deletedAt, updatedAt = deletedAt, syncState = "PENDING"))
            queue.enqueue("task", id, "DELETE", old.version)
        }
    }
}

private fun TaskEntity.toPayload() = mapOf(
    "scheduled_date" to scheduledDate,
    "title" to title,
    "description" to description,
    "priority" to priority,
    "due_at" to dueAt,
    "status" to status,
    "linked_focus_id" to linkedFocusId,
    "estimate_minutes" to estimateMinutes
)
