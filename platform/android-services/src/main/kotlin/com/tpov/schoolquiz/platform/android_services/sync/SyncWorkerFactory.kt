package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.tpov.schoolquiz.shared.core.sync.Syncable

class SyncWorkerFactory(
    private val syncables: List<Syncable>,
    private val profileSyncables: List<Syncable>,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, syncables)
            ProfileSyncWorker::class.java.name -> ProfileSyncWorker(appContext, workerParameters, profileSyncables)
            else -> null
        }
}
