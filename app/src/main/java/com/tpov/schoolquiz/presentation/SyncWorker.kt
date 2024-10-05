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
import com.tpov.common.domain.QuestionUseCase
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.StructureUseCase
import com.tpov.schoolquiz.domain.ProfileUseCase
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
    private val quizUseCase: QuizUseCase,
    private val profileUseCase: ProfileUseCase,
    private val questionUseCase: QuestionUseCase,
    private val viewModelFactory: ViewModelProvider.Factory
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val KEY_SYNC_SUCCESS = "KEY_SYNC_SUCCESS"
        private const val CHANNEL_ID = "SYNC_NOTIFICATION_CHANNEL"
        private const val NOTIFICATION_ID = 2
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val viewModel = ViewModelProvider(ViewModelStore(), viewModelFactory)[MainViewModel::class.java]
        try {
            Log.d("SyncWorker", "Sync started")

            fetchQuizData()
            pushQuizData(viewModel)
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

    private suspend fun syncProfile() {
        profileUseCase.syncProfile()
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

    private suspend fun pushQuizData(viewModel: MainViewModel) {
        val structureCategoryList = structureUseCase.getStructureCategory()
        if (structureCategoryList.isNotEmpty()) {
            structureCategoryList.forEach { structureCategory ->
                val idQuiz = structureCategory.oldIdQuizId
                val quiz = quizUseCase.getQuizById(idQuiz)
                val questionList = questionUseCase.getQuestionByIdQuiz(idQuiz)

                viewModel.pushTheQuiz(structureCategory, quiz!!, questionList)
                structureUseCase.deleteStructureCategoryById(structureCategory.id!!)
            }
            showNotification("Sync Complete", "Pushed ${structureCategoryList.size} quizzes.")
        }
    }

    private suspend fun fetchQuizData() {
        val updatedQuizList = structureUseCase.syncStructureDataANDquizzes()
        if (updatedQuizList.isNotEmpty()) {
            val quizCount = updatedQuizList.size

            Log.d("SyncWorker", "Sync completed with $quizCount updated quizzes")
            updatedQuizList.forEach { quiz ->
                Log.d("SyncWorker", "Updated quiz: ${quiz.first}")
            }

            showNotification("Sync Complete", "Updated $quizCount quizzes.")
        } else {
            showNotification("Sync Complete", "No new data to synchronize.")
        }
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

    @AssistedFactory
    interface Factory : ChildWorkerFactory {
        override fun create(context: Context, workerParams: WorkerParameters): SyncWorker
    }

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
