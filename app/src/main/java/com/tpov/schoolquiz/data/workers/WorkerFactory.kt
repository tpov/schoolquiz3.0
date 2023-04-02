/*
package com.tpov.geoquiz.activity.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.tpov.geoquiz.database.QuestionViewModel
import kotlinx.coroutines.InternalCoroutinesApi
import java.security.Provider
import javax.inject.Inject

@InternalCoroutinesApi
class WorkerFactory @Inject constructor(
    private val workerProviders: @JvmSuppressWildcards QuestionViewModel
) : WorkerFactory() {


    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            RefreshDataWorker::class.qualifiedName -> {
                val childWorkerFactory = workerProviders[RefreshDataWorker::class.java]?.get()
                return childWorkerFactory?.create(appContext, workerParameters)
            }
            else -> null
        }
    }
}
*/
