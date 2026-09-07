package com.dailythread.app

import android.app.Application
import android.os.Build
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.*
import com.dailythread.app.data.local.AppDatabase
import com.dailythread.app.data.repository.TokenStore
import com.dailythread.app.sync.SyncEngine
import com.dailythread.app.sync.SyncWorker
import com.dailythread.app.sync.RealtimeInvalidationClient
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class DailyThreadApp : Application() {
    lateinit var db: AppDatabase
    lateinit var tokenStore: TokenStore
    lateinit var syncEngine: SyncEngine
    lateinit var realtime: RealtimeInvalidationClient

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val deviceId: String by lazy {
        getSharedPreferences("device", MODE_PRIVATE).let { prefs ->
            prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("device_id", it).apply()
            }
        }
    }

    val deviceName: String by lazy {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .ifBlank { "Android device" }
    }

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "daily-thread.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        tokenStore = TokenStore(this)
        syncEngine = SyncEngine(
            db = db,
            tokenStore = tokenStore,
            deviceId = deviceId,
            deviceName = deviceName,
            appVersion = BuildConfig.VERSION_NAME
        )
        realtime = RealtimeInvalidationClient(tokenStore, syncEngine)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-thread-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        applicationScope.launch {
            db.outboxDao().observePushableCount()
                .distinctUntilChanged()
                .collectLatest { count ->
                    if (count <= 0) return@collectLatest
                    delay(750)
                    val immediate = OneTimeWorkRequestBuilder<SyncWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                        .build()
                    WorkManager.getInstance(this@DailyThreadApp).enqueueUniqueWork(
                        "daily-thread-sync-now",
                        ExistingWorkPolicy.KEEP,
                        immediate
                    )
                }
        }
    }

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_daily_reviews_reviewDate")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_reviews_reviewDate ON daily_reviews(reviewDate)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_reviews_userId_reviewDate ON daily_reviews(userId, reviewDate)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_items_userId_focusDate_sortOrder ON focus_items(userId, focusDate, sortOrder)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habit_entries_userId_habitId_entryDate ON habit_entries(userId, habitId, entryDate)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbox_mutations ADD COLUMN serverVersion INTEGER")
                db.execSQL("ALTER TABLE outbox_mutations ADD COLUMN serverPayloadJson TEXT")
            }
        }
    }
}
