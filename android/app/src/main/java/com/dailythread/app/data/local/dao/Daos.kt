package com.dailythread.app.data.local.dao

import androidx.room.*
import com.dailythread.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_items WHERE userId=:userId AND focusDate=:date AND deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeForDate(userId: String, date: String): Flow<List<FocusEntity>>

    @Query("SELECT * FROM focus_items WHERE userId=:userId AND focusDate=:date AND deletedAt IS NULL ORDER BY sortOrder, createdAt")
    suspend fun forDate(userId: String, date: String): List<FocusEntity>

    @Query("SELECT * FROM focus_items WHERE id=:id LIMIT 1")
    suspend fun byId(id: String): FocusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FocusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FocusEntity>)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE userId=:userId AND scheduledDate=:date AND deletedAt IS NULL ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, createdAt")
    fun observeForDate(userId: String, date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id=:id LIMIT 1")
    suspend fun byId(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TaskEntity)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE userId=:userId AND activityDate=:date AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeForDate(userId: String, date: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE id=:id LIMIT 1")
    suspend fun byId(id: String): ActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ActivityEntity)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE userId=:userId AND isActive=1 AND deletedAt IS NULL ORDER BY createdAt")
    fun observeActive(userId: String): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id=:id LIMIT 1")
    suspend fun byId(id: String): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HabitEntity)
}

@Dao
interface HabitEntryDao {
    @Query("SELECT * FROM habit_entries WHERE userId=:userId AND entryDate=:date AND deletedAt IS NULL")
    fun observeForDate(userId: String, date: String): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habit_entries WHERE habitId=:habitId AND entryDate=:date AND deletedAt IS NULL LIMIT 1")
    suspend fun forHabitDate(habitId: String, date: String): HabitEntryEntity?

    @Query("SELECT * FROM habit_entries WHERE id=:id LIMIT 1")
    suspend fun byId(id: String): HabitEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HabitEntryEntity)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM daily_reviews WHERE userId=:userId AND reviewDate=:date AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    fun observeForDate(userId: String, date: String): Flow<ReviewEntity?>

    @Query("SELECT * FROM daily_reviews WHERE userId=:userId AND reviewDate=:date AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun forDate(userId: String, date: String): ReviewEntity?

    @Query("SELECT * FROM daily_reviews WHERE id=:id LIMIT 1")
    suspend fun byId(id: String): ReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReviewEntity)
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: OutboxMutationEntity)

    @Query("SELECT * FROM outbox_mutations WHERE state IN ('PENDING','FAILED') ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int = 100): List<OutboxMutationEntity>

    @Query("UPDATE outbox_mutations SET state=:state, lastError=:error, attempts=attempts+1 WHERE mutationId=:id")
    suspend fun mark(id: String, state: String, error: String? = null)

    @Query("DELETE FROM outbox_mutations WHERE mutationId=:id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM outbox_mutations WHERE state IN ('PENDING','FAILED','CONFLICT')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_mutations WHERE entityType=:entityType AND entityId=:entityId AND state IN ('PENDING','FAILED','CONFLICT')")
    suspend fun openCount(entityType: String, entityId: String): Int
}

@Dao
interface SyncMetaDao {
    @Query("SELECT value FROM sync_meta WHERE key=:key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: SyncMetaEntity)
}
