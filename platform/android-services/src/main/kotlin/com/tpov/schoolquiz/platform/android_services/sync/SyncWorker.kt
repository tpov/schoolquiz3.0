package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tpov.schoolquiz.shared.core.sync.Syncable
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncables: List<Syncable>,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = if (performSync(syncables)) Result.success() else Result.retry()

    companion object {
        const val WORK_NAME_PERIODIC = "periodic_sync"
        const val WORK_NAME_MANUAL = "manual_sync"
        const val WORK_NAME_BOOTSTRAP = "bootstrap_sync"
        val PERIODIC_INTERVAL = 1L to TimeUnit.DAYS

        internal suspend fun performSync(syncables: List<Syncable>): Boolean {
            for (syncable in syncables) {
                syncable.sync().onFailure { error ->
                    Log.w(TAG, "Syncable failed: ${syncable.javaClass.name}", error)
                    return false
                }
            }
            return true
        }

        private const val TAG = "SchoolQuizSync"
    }
}
