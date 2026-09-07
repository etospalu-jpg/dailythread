package com.dailythread.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dailythread.app.DailyThreadApp

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as DailyThreadApp
        app.syncEngine.runOnce()
        Result.success()
    } catch (t: Throwable) {
        if (runAttemptCount >= 5) Result.failure() else Result.retry()
    }
}
