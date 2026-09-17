package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tpov.schoolquiz.shared.core.sync.SyncRun
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.Syncable
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncables: List<Syncable>,
    private val run: SyncRun,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        if (performSync(SyncRunKind.CONTENT, syncables, run)) Result.success() else Result.retry()

    companion object {
        const val WORK_NAME_PERIODIC = "periodic_sync"
        const val WORK_NAME_MANUAL = "manual_sync"
        const val WORK_NAME_BOOTSTRAP = "bootstrap_sync"
        val PERIODIC_INTERVAL = 1L to TimeUnit.DAYS

        /**
         * Runs every syncable, in order, and reports whether they all succeeded.
         *
         * Deliberately not fail-fast any more. The steps are independent: the catalog is public
         * content that has nothing to do with an account, and it used to be last in the list — so
         * one refused profile call left the app showing no quests at all, which reads as a broken
         * app rather than a profile that did not refresh.
         *
         * Order is still honoured, because some steps are meant to run after others: the profile
         * is refreshed again after results are pushed, to pick up the rewards the server granted.
         *
         * Решение о том, что считать исходом прогона, живёт в [SyncRun], в общем коде: воркер —
         * платформенная обёртка расписания, и знать про фичи, ветви ошибок и время ему незачем
         * (AD-7, AD-9). Здесь остаётся только журнал: он и был единственным следом прогона, и
         * теперь перестаёт им быть.
         *
         * Своё расписание воркер называет сам и обязан: след у каждого свой (AD-14), и общий на
         * двоих означал, что удачный профильный прогон снимает ветвь упавшего контентного.
         */
        internal suspend fun performSync(
            kind: SyncRunKind,
            syncables: List<Syncable>,
            run: SyncRun,
        ): Boolean =
            run.run(kind, syncables) { syncable, error ->
                Log.w(TAG, "Syncable failed: ${syncable.javaClass.name}", error)
            }.isSuccess

        private const val TAG = "SchoolQuizSync"
    }
}
