package com.mahalaxmi.expiry.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mahalaxmi.expiry.data.ExpiryDatabase

class ExpirySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val dao = ExpiryDatabase.getInstance(applicationContext).expiryDao()
            SupabaseSyncRepository(applicationContext, dao).sync()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
