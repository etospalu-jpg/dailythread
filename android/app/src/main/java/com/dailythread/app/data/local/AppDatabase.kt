package com.dailythread.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dailythread.app.data.local.dao.*
import com.dailythread.app.data.local.entity.*

@Database(
    entities = [
        FocusEntity::class, TaskEntity::class, ActivityEntity::class,
        HabitEntity::class, HabitEntryEntity::class, ReviewEntity::class,
        OutboxMutationEntity::class, SyncMetaEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao
    abstract fun taskDao(): TaskDao
    abstract fun activityDao(): ActivityDao
    abstract fun habitDao(): HabitDao
    abstract fun habitEntryDao(): HabitEntryDao
    abstract fun reviewDao(): ReviewDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncMetaDao(): SyncMetaDao
}
