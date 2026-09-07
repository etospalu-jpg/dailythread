package com.dailythread.app.data.repository

import androidx.room.withTransaction
import com.dailythread.app.data.local.AppDatabase
import com.dailythread.app.data.local.entity.ReviewEntity
import java.time.LocalDate
import java.util.UUID

class ReviewRepository(
    private val db: AppDatabase,
    private val tokenStore: TokenStore,
    private val deviceId: String
) {
    private val queue = MutationQueue(db, deviceId)

    fun observe(userId: String, date: LocalDate) = db.reviewDao().observeForDate(userId, date.toString())

    suspend fun save(
        achievement: String,
        blocker: String,
        tomorrowPriority: String,
        mood: String,
        dailyScore: Int,
        date: LocalDate = today()
    ) {
        val userId = requireNotNull(tokenStore.userId()) { "Login required" }
        val old = db.reviewDao().forDate(userId, date.toString())
        val now = nowIso()
        val item = if (old == null) {
            ReviewEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                reviewDate = date.toString(),
                achievement = achievement.trim(),
                blocker = blocker.trim(),
                tomorrowPriority = tomorrowPriority.trim(),
                mood = mood.trim(),
                dailyScore = dailyScore.coerceIn(0, 100),
                originDeviceId = deviceId,
                createdAt = now,
                updatedAt = now
            )
        } else {
            old.copy(
                achievement = achievement.trim(),
                blocker = blocker.trim(),
                tomorrowPriority = tomorrowPriority.trim(),
                mood = mood.trim(),
                dailyScore = dailyScore.coerceIn(0, 100),
                updatedAt = now,
                syncState = "PENDING"
            )
        }
        db.withTransaction {
            db.reviewDao().upsert(item)
            queue.enqueue("daily_review", item.id, if (old == null) "CREATE" else "UPDATE", old?.version ?: 0, item.toPayload())
        }
    }

    suspend fun delete(date: LocalDate = today()) {
        val userId = requireNotNull(tokenStore.userId()) { "Login required" }
        val old = db.reviewDao().forDate(userId, date.toString()) ?: return
        val deletedAt = nowIso()
        db.withTransaction {
            db.reviewDao().upsert(old.copy(deletedAt = deletedAt, updatedAt = deletedAt, syncState = "PENDING"))
            queue.enqueue("daily_review", old.id, "DELETE", old.version)
        }
    }
}

private fun ReviewEntity.toPayload() = mapOf(
    "review_date" to reviewDate,
    "achievement" to achievement,
    "blocker" to blocker,
    "tomorrow_priority" to tomorrowPriority,
    "mood" to mood,
    "daily_score" to dailyScore
)
