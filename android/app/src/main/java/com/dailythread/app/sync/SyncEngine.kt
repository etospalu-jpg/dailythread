package com.dailythread.app.sync

import androidx.room.withTransaction
import com.dailythread.app.data.local.AppDatabase
import com.dailythread.app.data.local.entity.*
import com.dailythread.app.data.network.*
import com.dailythread.app.data.repository.AuthRepository
import com.dailythread.app.data.repository.TokenStore
import com.google.gson.Gson
import com.google.gson.JsonObject

class SyncEngine(
    private val db: AppDatabase,
    private val tokenStore: TokenStore,
    private val deviceId: String
) {
    private val gson = Gson()
    private val auth = AuthRepository(tokenStore)

    suspend fun runOnce() {
        val token = auth.validAccessToken() ?: return
        push(token)
        pull(token)
    }

    private suspend fun push(token: String) {
        val pending = db.outboxDao().pending(100)
        if (pending.isEmpty()) return
        val body = pending.map {
            SyncMutationDto(
                mutationId = it.mutationId,
                entityType = it.entityType,
                entityId = it.entityId,
                operation = it.operation,
                deviceId = it.deviceId,
                baseVersion = it.baseVersion,
                payload = gson.fromJson(it.payloadJson, JsonObject::class.java)
            )
        }
        val response = ApiFactory.sync.push(
            authorization = "Bearer $token",
            request = PushRequest(mutations = body)
        )
        response.results.forEach { result ->
            when (result.status) {
                "SYNCED" -> db.outboxDao().delete(result.mutationId)
                "CONFLICT" -> db.outboxDao().mark(result.mutationId, "CONFLICT", result.error)
                else -> db.outboxDao().mark(result.mutationId, "FAILED", result.error)
            }
        }
    }

    private suspend fun pull(token: String) {
        var cursor = db.syncMetaDao().get("cursor")?.toLongOrNull() ?: 0L
        var more: Boolean
        do {
            val response = ApiFactory.sync.pull(
                authorization = "Bearer $token",
                request = PullRequest(cursor = cursor, deviceId = deviceId)
            )
            db.withTransaction {
                response.changes.forEach { applyChange(it) }
                cursor = response.nextCursor
                db.syncMetaDao().put(SyncMetaEntity("cursor", cursor.toString()))
            }
            more = response.hasMore
        } while (more)
    }

    private suspend fun applyChange(change: ChangeDto) {
        // Never overwrite a local edit that is still pending/failed/conflicted.
        if (db.outboxDao().openCount(change.entityType, change.entityId) > 0) return
        val p = change.payload ?: return
        fun str(name: String): String? = if (p.has(name) && !p.get(name).isJsonNull) p.get(name).asString else null
        fun int(name: String, fallback: Int = 0): Int = if (p.has(name) && !p.get(name).isJsonNull) p.get(name).asInt else fallback
        fun long(name: String, fallback: Long = 0): Long = if (p.has(name) && !p.get(name).isJsonNull) p.get(name).asLong else fallback
        fun bool(name: String, fallback: Boolean = false): Boolean = if (p.has(name) && !p.get(name).isJsonNull) p.get(name).asBoolean else fallback

        when (change.entityType) {
            "focus_item" -> db.focusDao().upsert(
                FocusEntity(
                    id = str("id") ?: change.entityId,
                    userId = str("user_id") ?: return,
                    focusDate = str("focus_date") ?: return,
                    title = str("title") ?: "",
                    notes = str("notes") ?: "",
                    estimateMinutes = int("estimate_minutes"),
                    sortOrder = int("sort_order", 1),
                    status = str("status") ?: "PLANNED",
                    version = long("version", change.version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: change.changedAt,
                    updatedAt = str("updated_at") ?: change.changedAt,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "task" -> db.taskDao().upsert(
                TaskEntity(
                    id = str("id") ?: change.entityId,
                    userId = str("user_id") ?: return,
                    scheduledDate = str("scheduled_date"),
                    title = str("title") ?: "",
                    description = str("description") ?: "",
                    priority = str("priority") ?: "MEDIUM",
                    dueAt = str("due_at"),
                    status = str("status") ?: "TODO",
                    linkedFocusId = str("linked_focus_id"),
                    estimateMinutes = int("estimate_minutes"),
                    version = long("version", change.version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: change.changedAt,
                    updatedAt = str("updated_at") ?: change.changedAt,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "activity" -> db.activityDao().upsert(
                ActivityEntity(
                    id = str("id") ?: change.entityId,
                    userId = str("user_id") ?: return,
                    activityDate = str("activity_date") ?: return,
                    title = str("title") ?: "",
                    note = str("note") ?: "",
                    source = str("source") ?: "manual",
                    linkedEntityId = str("linked_entity_id"),
                    categoryName = str("category_name") ?: "Lainnya",
                    startedAt = str("started_at") ?: return,
                    endedAt = str("ended_at") ?: return,
                    durationMinutes = int("duration_minutes", 1),
                    version = long("version", change.version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: change.changedAt,
                    updatedAt = str("updated_at") ?: change.changedAt,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "habit" -> db.habitDao().upsert(
                HabitEntity(
                    id = str("id") ?: change.entityId,
                    userId = str("user_id") ?: return,
                    name = str("name") ?: "",
                    isActive = bool("is_active", true),
                    frequencyJson = if (p.has("frequency") && !p.get("frequency").isJsonNull) p.get("frequency").toString() else "{\"type\":\"daily\"}",
                    version = long("version", change.version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: change.changedAt,
                    updatedAt = str("updated_at") ?: change.changedAt,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "habit_entry" -> db.habitEntryDao().upsert(
                HabitEntryEntity(
                    id = str("id") ?: change.entityId,
                    userId = str("user_id") ?: return,
                    habitId = str("habit_id") ?: return,
                    entryDate = str("entry_date") ?: return,
                    completed = bool("completed"),
                    completedAt = str("completed_at"),
                    version = long("version", change.version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: change.changedAt,
                    updatedAt = str("updated_at") ?: change.changedAt,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "daily_review" -> db.reviewDao().upsert(
                ReviewEntity(
                    id = str("id") ?: change.entityId,
                    userId = str("user_id") ?: return,
                    reviewDate = str("review_date") ?: return,
                    achievement = str("achievement") ?: "",
                    blocker = str("blocker") ?: "",
                    tomorrowPriority = str("tomorrow_priority") ?: "",
                    mood = str("mood") ?: "",
                    dailyScore = int("daily_score").coerceIn(0, 100),
                    version = long("version", change.version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: change.changedAt,
                    updatedAt = str("updated_at") ?: change.changedAt,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )
        }
    }
}
