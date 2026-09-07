package com.dailythread.app.data.repository

import androidx.room.withTransaction
import com.dailythread.app.data.local.AppDatabase
import com.dailythread.app.data.local.entity.FocusEntity
import java.time.LocalDate
import java.util.UUID

class FocusRepository(
    private val db: AppDatabase,
    private val tokenStore: TokenStore,
    private val deviceId: String
) {
    private val queue = MutationQueue(db, deviceId)

    fun observe(userId: String, date: LocalDate) = db.focusDao().observeForDate(userId, date.toString())

    suspend fun add(title: String, estimateMinutes: Int = 60, date: LocalDate = today()) {
        val cleaned = title.trim()
        require(cleaned.isNotEmpty()) { "Nama fokus wajib diisi." }
        val userId = requireNotNull(tokenStore.userId()) { "Login required" }
        val current = db.focusDao().forDate(userId, date.toString())
        require(current.size < 3) { "Maksimal 3 fokus utama per hari." }
        val slot = (1..3).first { candidate -> current.none { it.sortOrder == candidate } }
        val now = nowIso()
        val id = UUID.randomUUID().toString()
        val item = FocusEntity(
            id = id,
            userId = userId,
            focusDate = date.toString(),
            title = cleaned,
            estimateMinutes = estimateMinutes.coerceAtLeast(0),
            sortOrder = slot,
            originDeviceId = deviceId,
            createdAt = now,
            updatedAt = now
        )
        db.withTransaction {
            db.focusDao().upsert(item)
            queue.enqueue("focus_item", id, "CREATE", 0, item.toPayload())
        }
    }

    suspend fun update(id: String, title: String, estimateMinutes: Int, notes: String = "") {
        val old = db.focusDao().byId(id) ?: return
        val now = nowIso()
        val item = old.copy(
            title = title.trim().ifEmpty { old.title },
            estimateMinutes = estimateMinutes.coerceAtLeast(0),
            notes = notes.trim(),
            updatedAt = now,
            syncState = "PENDING"
        )
        db.withTransaction {
            db.focusDao().upsert(item)
            queue.enqueue("focus_item", id, "UPDATE", old.version, item.toPayload())
        }
    }

    suspend fun setStatus(id: String, status: String) {
        require(status in setOf("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED"))
        val old = db.focusDao().byId(id) ?: return
        val item = old.copy(status = status, updatedAt = nowIso(), syncState = "PENDING")
        db.withTransaction {
            db.focusDao().upsert(item)
            queue.enqueue("focus_item", id, "UPDATE", old.version, mapOf("status" to status))
        }
    }

    suspend fun delete(id: String) {
        val old = db.focusDao().byId(id) ?: return
        if (old.deletedAt != null) return
        val deletedAt = nowIso()
        db.withTransaction {
            db.focusDao().upsert(old.copy(deletedAt = deletedAt, updatedAt = deletedAt, syncState = "PENDING"))
            queue.enqueue("focus_item", id, "DELETE", old.version)
        }
    }
}

private fun FocusEntity.toPayload() = mapOf(
    "focus_date" to focusDate,
    "title" to title,
    "notes" to notes,
    "estimate_minutes" to estimateMinutes,
    "sort_order" to sortOrder,
    "status" to status
)
