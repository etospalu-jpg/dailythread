package com.dailythread.app.data.repository

import androidx.room.withTransaction
import com.dailythread.app.data.local.AppDatabase
import com.dailythread.app.data.local.entity.ActivityEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ActivityRepository(
    private val db: AppDatabase,
    private val tokenStore: TokenStore,
    private val deviceId: String
) {
    private val queue = MutationQueue(db, deviceId)

    fun observe(userId: String, date: LocalDate) = db.activityDao().observeForDate(userId, date.toString())

    suspend fun add(
        title: String,
        startedAt: Instant,
        endedAt: Instant,
        category: String = "Lainnya",
        note: String = ""
    ) {
        require(endedAt.isAfter(startedAt)) { "Jam selesai harus setelah jam mulai." }
        val cleaned = title.trim()
        require(cleaned.isNotEmpty()) { "Nama aktivitas wajib diisi." }
        val userId = requireNotNull(tokenStore.userId()) { "Login required" }
        val now = nowIso()
        val id = UUID.randomUUID().toString()
        val date = startedAt.atZone(APP_ZONE).toLocalDate().toString()
        val minutes = Duration.between(startedAt, endedAt).toMinutes().coerceAtLeast(1).toInt()
        val item = ActivityEntity(
            id = id,
            userId = userId,
            activityDate = date,
            title = cleaned,
            note = note.trim(),
            categoryName = category.trim().ifEmpty { "Lainnya" },
            startedAt = startedAt.toString(),
            endedAt = endedAt.toString(),
            durationMinutes = minutes,
            originDeviceId = deviceId,
            createdAt = now,
            updatedAt = now
        )
        db.withTransaction {
            db.activityDao().upsert(item)
            queue.enqueue("activity", id, "CREATE", 0, item.toPayload())
        }
    }

    suspend fun quickAdd(title: String, durationMinutes: Int, category: String = "Lainnya") {
        val end = Instant.now()
        val start = end.minusSeconds(durationMinutes.coerceAtLeast(1).toLong() * 60L)
        add(title, start, end, category)
    }

    suspend fun update(id: String, title: String, category: String, note: String = "") {
        val old = db.activityDao().byId(id) ?: return
        val item = old.copy(
            title = title.trim().ifEmpty { old.title },
            categoryName = category.trim().ifEmpty { old.categoryName },
            note = note.trim(),
            updatedAt = nowIso(),
            syncState = "PENDING"
        )
        db.withTransaction {
            db.activityDao().upsert(item)
            queue.enqueue("activity", id, "UPDATE", old.version, item.toPayload())
        }
    }

    suspend fun delete(id: String) {
        val old = db.activityDao().byId(id) ?: return
        if (old.deletedAt != null) return
        val deletedAt = nowIso()
        db.withTransaction {
            db.activityDao().upsert(old.copy(deletedAt = deletedAt, updatedAt = deletedAt, syncState = "PENDING"))
            queue.enqueue("activity", id, "DELETE", old.version)
        }
    }
}

private fun ActivityEntity.toPayload() = mapOf(
    "activity_date" to activityDate,
    "title" to title,
    "note" to note,
    "source" to source,
    "linked_entity_id" to linkedEntityId,
    "category_name" to categoryName,
    "started_at" to startedAt,
    "ended_at" to endedAt,
    "duration_minutes" to durationMinutes
)
