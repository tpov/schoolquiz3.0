package com.tpov.schoolquiz.presentation

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.presentation.SyncWorker.Companion.CHANNEL_ID
import com.tpov.schoolquiz.presentation.SyncWorker.Companion.NOTIFICATION_ID
import com.tpov.schoolquiz.presentation.main.MainViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val structureUseCase: StructureUseCase,
    private val profileUseCase: ProfileUseCase,
    private val questionUseCase: QuestionUseCase,
    private val viewModelFactory: ViewModelProvider.Factory
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val KEY_SYNC_SUCCESS = "KEY_SYNC_SUCCESS"
        const val CHANNEL_ID = "SYNC_NOTIFICATION_CHANNEL"
        const val NOTIFICATION_ID = 2
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val viewModel = ViewModelProvider(ViewModelStore(), viewModelFactory)[MainViewModel::class.java]
        try {

            syncQuizData(viewModel)
            syncProfile()

            val outputData = Data.Builder()
                .putBoolean(KEY_SYNC_SUCCESS, true)
                .build()

            Log.d("SyncWorker", "Sync successful")
            Result.success(outputData)
        } catch (e: Exception) {
            Log.e("SyncData", "Error fetching or saving data: ${e.message}")
            Result.failure()
        }
    }

    private suspend fun syncQuizData(viewModel: MainViewModel) {
        val updatedQuizList = structureUseCase.syncStructureDataAndQuestions(1)
            val quizCount = updatedQuizList

            showNotification("Sync Complete", "Updated $quizCount quizzes.", context)
        }
    }

    private suspend fun syncProfile() {

    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, message: String, context: Context) {
        createNotificationChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sync Notifications"
            val descriptionText = "Notifications for data synchronization"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @AssistedFactory
    interface Factory : ChildWorkerFactory {
        override fun create(context: Context, workerParams: WorkerParameters): SyncWorker
    }

class AppWorkerFactory @Inject constructor(
    private val workerFactories: Map<Class<out ListenableWorker>, @JvmSuppressWildcards Provider<ChildWorkerFactory>>
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val factoryProvider = workerFactories[Class.forName(workerClassName)] ?: return null
        return factoryProvider.get().create(appContext, workerParameters)
    }
}

interface ChildWorkerFactory {
    fun create(context: Context, workerParams: WorkerParameters): SyncWorker
}
