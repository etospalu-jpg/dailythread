package com.dailythread.app.data.repository

import androidx.room.withTransaction
import com.dailythread.app.data.local.AppDatabase
import com.dailythread.app.data.local.entity.HabitEntity
import com.dailythread.app.data.local.entity.HabitEntryEntity
import java.time.LocalDate
import java.util.UUID

class HabitRepository(
    private val db: AppDatabase,
    private val tokenStore: TokenStore,
    private val deviceId: String
) {
    private val queue = MutationQueue(db, deviceId)

    fun observeHabits(userId: String) = db.habitDao().observeActive(userId)
    fun observeEntries(userId: String, date: LocalDate) = db.habitEntryDao().observeForDate(userId, date.toString())

    suspend fun add(name: String) {
        val cleaned = name.trim()
        require(cleaned.isNotEmpty()) { "Nama habit wajib diisi." }
        val userId = requireNotNull(tokenStore.userId()) { "Login required" }
        val now = nowIso()
        val id = UUID.randomUUID().toString()
        val item = HabitEntity(
            id = id,
            userId = userId,
            name = cleaned,
            originDeviceId = deviceId,
            createdAt = now,
            updatedAt = now
        )
        db.withTransaction {
            db.habitDao().upsert(item)
            queue.enqueue("habit", id, "CREATE", 0, item.toPayload())
        }
    }

    suspend fun rename(id: String, name: String) {
        val old = db.habitDao().byId(id) ?: return
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        val item = old.copy(name = cleaned, updatedAt = nowIso(), syncState = "PENDING")
        db.withTransaction {
            db.habitDao().upsert(item)
            queue.enqueue("habit", id, "UPDATE", old.version, item.toPayload())
        }
    }

    suspend fun setCompleted(habitId: String, completed: Boolean, date: LocalDate = today()) {
        val habit = db.habitDao().byId(habitId) ?: return
        val userId = requireNotNull(tokenStore.userId()) { "Login required" }
        val existing = db.habitEntryDao().forHabitDate(habitId, date.toString())
        val now = nowIso()
        if (existing == null) {
            val id = UUID.randomUUID().toString()
            val item = HabitEntryEntity(
                id = id,
                userId = userId,
                habitId = habit.id,
                entryDate = date.toString(),
                completed = completed,
                completedAt = if (completed) now else null,
                originDeviceId = deviceId,
                createdAt = now,
                updatedAt = now
            )
            db.withTransaction {
                db.habitEntryDao().upsert(item)
                queue.enqueue("habit_entry", id, "CREATE", 0, item.toPayload())
            }
        } else {
            val item = existing.copy(
                completed = completed,
                completedAt = if (completed) now else null,
                updatedAt = now,
                syncState = "PENDING"
            )
            db.withTransaction {
                db.habitEntryDao().upsert(item)
                queue.enqueue("habit_entry", item.id, "UPDATE", existing.version, item.toPayload())
            }
        }
    }

    suspend fun delete(id: String) {
        val old = db.habitDao().byId(id) ?: return
        if (old.deletedAt != null) return
        val deletedAt = nowIso()
        db.withTransaction {
            db.habitDao().upsert(old.copy(deletedAt = deletedAt, isActive = false, updatedAt = deletedAt, syncState = "PENDING"))
            queue.enqueue("habit", id, "DELETE", old.version)
        }
    }
}

private fun HabitEntity.toPayload() = mapOf(
    "name" to name,
    "is_active" to isActive,
    "frequency" to mapOf("type" to "daily")
)

private fun HabitEntryEntity.toPayload() = mapOf(
    "habit_id" to habitId,
    "entry_date" to entryDate,
    "completed" to completed,
    "completed_at" to completedAt
)
