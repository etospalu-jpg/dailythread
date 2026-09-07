package com.dailythread.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailythread.app.data.local.entity.FocusEntity
import com.dailythread.app.data.local.entity.OutboxMutationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseInstrumentedTest {
    private lateinit var db: AppDatabase
    private val now = "2026-09-07T08:00:00Z"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun focusDao_excludesSoftDeletedRows() = runBlocking {
        val dao = db.focusDao()
        dao.upsert(
            FocusEntity(
                id = "focus-active",
                userId = "user-1",
                focusDate = "2026-09-07",
                title = "Aktif",
                sortOrder = 1,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.upsert(
            FocusEntity(
                id = "focus-deleted",
                userId = "user-1",
                focusDate = "2026-09-07",
                title = "Terhapus",
                sortOrder = 2,
                createdAt = now,
                updatedAt = now,
                deletedAt = now
            )
        )

        val visible = dao.forDate("user-1", "2026-09-07")
        assertEquals(listOf("focus-active"), visible.map { it.id })
    }

    @Test
    fun focusDao_keepsUsersIsolated() = runBlocking {
        val dao = db.focusDao()
        dao.upsert(FocusEntity("focus-a", "user-a", "2026-09-07", "A", sortOrder = 1, createdAt = now, updatedAt = now))
        dao.upsert(FocusEntity("focus-b", "user-b", "2026-09-07", "B", sortOrder = 1, createdAt = now, updatedAt = now))

        assertEquals(listOf("focus-a"), dao.forDate("user-a", "2026-09-07").map { it.id })
        assertEquals(listOf("focus-b"), dao.forDate("user-b", "2026-09-07").map { it.id })
    }

    @Test
    fun outboxPendingCount_countsPendingFailedAndConflictOnly() = runBlocking {
        val dao = db.outboxDao()
        fun item(id: String, state: String) = OutboxMutationEntity(
            mutationId = id,
            entityType = "task",
            entityId = "entity-$id",
            operation = "UPDATE",
            baseVersion = 1,
            payloadJson = "{}",
            deviceId = "device-1",
            state = state,
            createdAt = now
        )

        dao.enqueue(item("p", "PENDING"))
        dao.enqueue(item("f", "FAILED"))
        dao.enqueue(item("c", "CONFLICT"))
        dao.enqueue(item("s", "SYNCED"))

        assertEquals(3, dao.observePendingCount().first())
    }

    @Test
    fun retryAgainstVersion_resetsConflictForNextPush() = runBlocking {
        val dao = db.outboxDao()
        val mutation = OutboxMutationEntity(
            mutationId = "m1",
            entityType = "task",
            entityId = "task-1",
            operation = "UPDATE",
            baseVersion = 1,
            payloadJson = "{\"title\":\"Local\"}",
            deviceId = "device-1",
            state = "CONFLICT",
            lastError = "version_mismatch",
            serverVersion = 8,
            serverPayloadJson = "{\"version\":8}",
            createdAt = now
        )
        dao.enqueue(mutation)

        dao.retryAgainstVersion("m1", 8)
        val retried = dao.byId("m1")!!

        assertEquals("PENDING", retried.state)
        assertEquals(8, retried.baseVersion)
        assertEquals(null, retried.lastError)
        assertEquals(null, retried.serverVersion)
        assertEquals(null, retried.serverPayloadJson)
    }
}
