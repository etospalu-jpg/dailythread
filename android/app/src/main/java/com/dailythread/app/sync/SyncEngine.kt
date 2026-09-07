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
    private val deviceId: String,
    private val deviceName: String,
    private val appVersion: String
) {
    private val gson = Gson()
    private val auth = AuthRepository(tokenStore)

    suspend fun runOnce() {
        val token = auth.validAccessToken() ?: return
        push(token)
        pull(token)
    }

    suspend fun resolveKeepServer(mutationId: String) {
        val conflict = db.outboxDao().byId(mutationId) ?: return
        val payloadJson = conflict.serverPayloadJson ?: return
        val payload = gson.fromJson(payloadJson, JsonObject::class.java)
        db.withTransaction {
            applyServerEntity(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                version = conflict.serverVersion ?: 0L,
                changedAt = payload.get("updated_at")?.takeIf { !it.isJsonNull }?.asString ?: "",
                payload = payload
            )
            db.outboxDao().delete(mutationId)
        }
    }

    suspend fun resolveKeepMine(mutationId: String) {
        val conflict = db.outboxDao().byId(mutationId) ?: return
        if (conflict.lastError == "not_found") {
            if (conflict.operation == "DELETE") {
                db.outboxDao().delete(mutationId)
                return
            }
            db.outboxDao().retryAsCreate(mutationId)
        } else {
            db.outboxDao().retryAgainstVersion(mutationId, conflict.serverVersion ?: 0L)
        }
        runOnce()
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
            request = PushRequest(
                deviceId = deviceId,
                deviceName = deviceName,
                appVersion = appVersion,
                mutations = body
            )
        )
        response.results.forEach { result ->
            when (result.status) {
                "SYNCED" -> db.outboxDao().delete(result.mutationId)
                "CONFLICT" -> db.outboxDao().markConflict(
                    id = result.mutationId,
                    error = result.error,
                    serverVersion = result.serverVersion,
                    serverPayloadJson = result.serverEntity?.toString()
                )
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
                request = PullRequest(
                    cursor = cursor,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    appVersion = appVersion
                )
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
        if (db.outboxDao().openCount(change.entityType, change.entityId) > 0) return
        val payload = change.payload ?: return
        applyServerEntity(change.entityType, change.entityId, change.version, change.changedAt, payload)
    }

    private suspend fun applyServerEntity(
        entityType: String,
        entityId: String,
        version: Long,
        changedAt: String,
        payload: JsonObject
    ) {
        fun str(name: String): String? = if (payload.has(name) && !payload.get(name).isJsonNull) payload.get(name).asString else null
        fun int(name: String, fallback: Int = 0): Int = if (payload.has(name) && !payload.get(name).isJsonNull) payload.get(name).asInt else fallback
        fun long(name: String, fallback: Long = 0): Long = if (payload.has(name) && !payload.get(name).isJsonNull) payload.get(name).asLong else fallback
        fun bool(name: String, fallback: Boolean = false): Boolean = if (payload.has(name) && !payload.get(name).isJsonNull) payload.get(name).asBoolean else fallback
        val timestamp = changedAt.ifBlank { str("updated_at") ?: str("created_at") ?: "1970-01-01T00:00:00Z" }

        when (entityType) {
            "focus_item" -> db.focusDao().upsert(
                FocusEntity(
                    id = str("id") ?: entityId,
                    userId = str("user_id") ?: return,
                    focusDate = str("focus_date") ?: return,
                    title = str("title") ?: "",
                    notes = str("notes") ?: "",
                    estimateMinutes = int("estimate_minutes"),
                    sortOrder = int("sort_order", 1),
                    status = str("status") ?: "PLANNED",
                    version = long("version", version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: timestamp,
                    updatedAt = str("updated_at") ?: timestamp,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "task" -> db.taskDao().upsert(
                TaskEntity(
                    id = str("id") ?: entityId,
                    userId = str("user_id") ?: return,
                    scheduledDate = str("scheduled_date"),
                    title = str("title") ?: "",
                    description = str("description") ?: "",
                    priority = str("priority") ?: "MEDIUM",
                    dueAt = str("due_at"),
                    status = str("status") ?: "TODO",
                    linkedFocusId = str("linked_focus_id"),
                    estimateMinutes = int("estimate_minutes"),
                    version = long("version", version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: timestamp,
                    updatedAt = str("updated_at") ?: timestamp,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "activity" -> db.activityDao().upsert(
                ActivityEntity(
                    id = str("id") ?: entityId,
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
                    version = long("version", version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: timestamp,
                    updatedAt = str("updated_at") ?: timestamp,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "habit" -> db.habitDao().upsert(
                HabitEntity(
                    id = str("id") ?: entityId,
                    userId = str("user_id") ?: return,
                    name = str("name") ?: "",
                    isActive = bool("is_active", true),
                    frequencyJson = if (payload.has("frequency") && !payload.get("frequency").isJsonNull) payload.get("frequency").toString() else "{\"type\":\"daily\"}",
                    version = long("version", version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: timestamp,
                    updatedAt = str("updated_at") ?: timestamp,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "habit_entry" -> db.habitEntryDao().upsert(
                HabitEntryEntity(
                    id = str("id") ?: entityId,
                    userId = str("user_id") ?: return,
                    habitId = str("habit_id") ?: return,
                    entryDate = str("entry_date") ?: return,
                    completed = bool("completed"),
                    completedAt = str("completed_at"),
                    version = long("version", version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: timestamp,
                    updatedAt = str("updated_at") ?: timestamp,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )

            "daily_review" -> db.reviewDao().upsert(
                ReviewEntity(
                    id = str("id") ?: entityId,
                    userId = str("user_id") ?: return,
                    reviewDate = str("review_date") ?: return,
                    achievement = str("achievement") ?: "",
                    blocker = str("blocker") ?: "",
                    tomorrowPriority = str("tomorrow_priority") ?: "",
                    mood = str("mood") ?: "",
                    dailyScore = int("daily_score").coerceIn(0, 100),
                    version = long("version", version),
                    originDeviceId = str("origin_device_id"),
                    createdAt = str("created_at") ?: timestamp,
                    updatedAt = str("updated_at") ?: timestamp,
                    deletedAt = str("deleted_at"),
                    syncState = "SYNCED"
                )
            )
        }
    }
}
