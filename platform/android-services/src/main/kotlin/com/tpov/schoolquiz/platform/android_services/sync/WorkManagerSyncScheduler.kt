package com.tpov.schoolquiz.platform.android_services.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler

class WorkManagerSyncScheduler(
    private val workManager: WorkManager,
) : SyncScheduler {
    override fun enqueueManualSync() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
        workManager.enqueueUniqueWork(SyncWorker.WORK_NAME_MANUAL, ExistingWorkPolicy.REPLACE, request)
    }
}
