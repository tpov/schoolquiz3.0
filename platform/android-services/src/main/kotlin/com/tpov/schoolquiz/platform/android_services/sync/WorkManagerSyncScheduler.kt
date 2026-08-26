package com.tpov.schoolquiz.platform.android_services.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import java.util.concurrent.TimeUnit

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

    override fun applyFrequency(frequency: SyncFrequency) {
        schedule(SyncWorker.WORK_NAME_PERIODIC, frequency) { periodicRequest<SyncWorker>(it) }
    }

    override fun applyProfileFrequency(frequency: SyncFrequency) {
        schedule(ProfileSyncWorker.WORK_NAME_PROFILE_PERIODIC, frequency) {
            periodicRequest<ProfileSyncWorker>(it)
        }
    }

    /** UPDATE keeps an existing schedule running while the interval is swapped; same value is a no-op. */
    private fun schedule(
        workName: String,
        frequency: SyncFrequency,
        request: (Long) -> PeriodicWorkRequest,
    ) {
        when (val intervalMs = frequency.intervalMs) {
            null -> workManager.cancelUniqueWork(workName)
            else ->
                workManager.enqueueUniquePeriodicWork(
                    workName,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request(intervalMs),
                )
        }
    }

    private inline fun <reified W : ListenableWorker> periodicRequest(intervalMs: Long): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<W>(intervalMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
}
