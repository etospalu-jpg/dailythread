package com.dailythread.app.data.repository

import com.dailythread.app.data.local.AppDatabase
import com.dailythread.app.data.local.entity.OutboxMutationEntity
import com.google.gson.Gson
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal val APP_ZONE: ZoneId = ZoneId.of("Asia/Makassar")
internal fun today(): LocalDate = LocalDate.now(APP_ZONE)
internal fun nowIso(): String = Instant.now().toString()

internal class MutationQueue(
    private val db: AppDatabase,
    private val deviceId: String,
    private val gson: Gson = Gson()
) {
    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: String,
        baseVersion: Long,
        payload: Map<String, Any?> = emptyMap()
    ) {
        val userId = when (entityType) {
            "focus_item" -> db.focusDao().byId(entityId)?.userId
            "task" -> db.taskDao().byId(entityId)?.userId
            "activity" -> db.activityDao().byId(entityId)?.userId
            "habit" -> db.habitDao().byId(entityId)?.userId
            "habit_entry" -> db.habitEntryDao().byId(entityId)?.userId
            "daily_review" -> db.reviewDao().byId(entityId)?.userId
            else -> null
        }
        require(!userId.isNullOrBlank()) { "Mutation owner could not be resolved." }

        db.outboxDao().enqueue(
            OutboxMutationEntity(
                mutationId = UUID.randomUUID().toString(),
                userId = userId,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                baseVersion = baseVersion,
                payloadJson = gson.toJson(payload),
                deviceId = deviceId,
                createdAt = nowIso()
            )
        )
    }
}
