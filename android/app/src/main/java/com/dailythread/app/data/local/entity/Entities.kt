package com.dailythread.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "focus_items", indices = [Index("focusDate"), Index("syncState"), Index(value = ["userId", "focusDate", "sortOrder"], unique = true)])
data class FocusEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val focusDate: String,
    val title: String,
    val notes: String = "",
    val estimateMinutes: Int = 0,
    val sortOrder: Int = 1,
    val status: String = "PLANNED",
    val version: Long = 0,
    val originDeviceId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val syncState: String = "PENDING"
)

@Entity(tableName = "tasks", indices = [Index("scheduledDate"), Index("syncState")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val scheduledDate: String?,
    val title: String,
    val description: String = "",
    val priority: String = "MEDIUM",
    val dueAt: String? = null,
    val status: String = "TODO",
    val linkedFocusId: String? = null,
    val estimateMinutes: Int = 0,
    val version: Long = 0,
    val originDeviceId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val syncState: String = "PENDING"
)

@Entity(tableName = "activities", indices = [Index("activityDate"), Index("syncState")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val activityDate: String,
    val title: String,
    val note: String = "",
    val source: String = "manual",
    val linkedEntityId: String? = null,
    val categoryName: String = "Lainnya",
    val startedAt: String,
    val endedAt: String,
    val durationMinutes: Int,
    val version: Long = 0,
    val originDeviceId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val syncState: String = "PENDING"
)

@Entity(tableName = "habits", indices = [Index("syncState")])
data class HabitEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val isActive: Boolean = true,
    val frequencyJson: String = "{\"type\":\"daily\"}",
    val version: Long = 0,
    val originDeviceId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val syncState: String = "PENDING"
)

@Entity(
    tableName = "habit_entries",
    indices = [Index("habitId"), Index("entryDate"), Index("syncState"), Index(value = ["userId", "habitId", "entryDate"], unique = true)]
)
data class HabitEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val habitId: String,
    val entryDate: String,
    val completed: Boolean = false,
    val completedAt: String? = null,
    val version: Long = 0,
    val originDeviceId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val syncState: String = "PENDING"
)

@Entity(tableName = "daily_reviews", indices = [Index("reviewDate"), Index("syncState"), Index(value = ["userId", "reviewDate"], unique = true)])
data class ReviewEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val reviewDate: String,
    val achievement: String = "",
    val blocker: String = "",
    val tomorrowPriority: String = "",
    val mood: String = "",
    val dailyScore: Int = 0,
    val version: Long = 0,
    val originDeviceId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val syncState: String = "PENDING"
)

@Entity(
    tableName = "outbox_mutations",
    indices = [Index("state"), Index(value = ["entityType", "entityId"]), Index(value = ["userId", "state"])]
)
data class OutboxMutationEntity(
    @PrimaryKey val mutationId: String,
    val userId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val baseVersion: Long,
    val payloadJson: String,
    val deviceId: String?,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val lastError: String? = null,
    val serverVersion: Long? = null,
    val serverPayloadJson: String? = null,
    val createdAt: String
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String
)
