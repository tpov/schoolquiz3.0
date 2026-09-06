package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.tpov.schoolquiz.shared.core.sync.SyncRun
import com.tpov.schoolquiz.shared.core.sync.Syncable

class SyncWorkerFactory(
    private val syncables: List<Syncable>,
    private val profileSyncables: List<Syncable>,
    /**
     * Кто записывает исход обоих прогонов.
     *
     * Один писатель на оба, но не один след: расписание каждый прогон называет сам, и строки в
     * таблице у них разные (AD-14). Второй экземпляр здесь означал бы второго владельца решения
     * об исходе — ровно то, из-за чего успех одного прогона стирал ветвь другого.
     */
    private val run: SyncRun,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, syncables, run)
            ProfileSyncWorker::class.java.name ->
                ProfileSyncWorker(appContext, workerParameters, profileSyncables, run)
            else -> null
        }
}
