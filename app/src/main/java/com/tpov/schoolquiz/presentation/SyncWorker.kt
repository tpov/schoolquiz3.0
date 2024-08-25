package com.tpov.schoolquiz.presentation

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.tpov.common.domain.StructureUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val structureUseCase: StructureUseCase
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val KEY_SYNC_SUCCESS = "KEY_SYNC_SUCCESS"
        private const val CHANNEL_ID = "SYNC_NOTIFICATION_CHANNEL"
        private const val NOTIFICATION_ID = 2
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncWorker", "Sync started")

            val updatedQuizList = structureUseCase.syncData()

            if (updatedQuizList.isNotEmpty()) {
                val quizCount = updatedQuizList.size
                Log.d("SyncWorker", "Sync completed with $quizCount updated quizzes")
                showNotification("Sync Complete", "Updated $quizCount quizzes.")
            } else {
                Log.d("SyncWorker", "Sync completed with no new data to synchronize")
                showNotification("Sync Complete", "No new data to synchronize.")
            }

            val outputData = Data.Builder()
                    .putBoolean(KEY_SYNC_SUCCESS, true)
                    .build()

            Log.d("SyncWorker", "Sync successful")
            Result.success(outputData)
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed with exception", e)
            Result.retry()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, message: String) {
        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sync Notifications"
            val descriptionText = "Notifications for data synchronization"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @AssistedInject.Factory
    interface Factory : ChildWorkerFactory
}

class AppWorkerFactory @Inject constructor(
    private val workerFactories: Map<Class<out ListenableWorker>, @JvmSuppressWildcards Provider<ChildWorkerFactory>>
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val entry = workerFactories.entries.find {
            Class.forName(workerClassName).isAssignableFrom(it.key)
        }

        val factoryProvider = entry?.value ?: return null
        return factoryProvider.get().create(appContext, workerParameters)
    }
}

interface ChildWorkerFactory {
    fun create(context: Context, workerParams: WorkerParameters): ListenableWorker
}
