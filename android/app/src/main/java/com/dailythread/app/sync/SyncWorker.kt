package com.dailythread.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dailythread.app.DailyThreadApp

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as DailyThreadApp
        app.tokenStore.ensureLocalUserId(app.deviceId)
        app.ensureCloudIdentity()
        val userId = app.tokenStore.userId() ?: return Result.success()

        app.syncEngine.runOnce()
        app.profileRepository.syncDirty()

        val stillPushable = app.db.outboxDao().pushableCount(userId)
        val profileDirty = app.tokenStore.profileDirty()
        if ((stillPushable > 0 || profileDirty) && runAttemptCount < 5) Result.retry() else Result.success()
    } catch (t: Throwable) {
        if (runAttemptCount >= 5) Result.failure() else Result.retry()
    }
}
