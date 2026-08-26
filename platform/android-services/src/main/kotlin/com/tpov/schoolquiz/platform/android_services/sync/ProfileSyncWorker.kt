package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tpov.schoolquiz.shared.core.sync.Syncable

/**
 * Refreshes the account's own state — the profile and what hangs off it.
 *
 * Runs on the profile's cadence from settings, which is allowed to be rarer than the content
 * schedule: quests play offline regardless, so the server copy of the profile may trail behind
 * without anything on the screen lying to the player.
 */
class ProfileSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncables: List<Syncable>,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = if (SyncWorker.performSync(syncables)) Result.success() else Result.retry()

    companion object {
        const val WORK_NAME_PROFILE_PERIODIC = "profile_periodic_sync"
    }
}
