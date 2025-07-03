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
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.tpov.common.ExceptionInteractor
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.LockServerResult
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.usecase.SettingConfigObject
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.domain.usecase.SyncInteractor
import com.tpov.common.presentation.utils.LanguageUtils.Companion.toLanguageUtils
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.presentation.services.ProfileInteractor
import com.tpov.setting.data.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val syncInteractor: SyncInteractor,
    private val profileUseCase: ProfileUseCase,
    private val exceptionInteractor: ExceptionInteractor,
    private val profileInteractor: ProfileInteractor,
    private val viewModelFactory: ViewModelProvider.Factory
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val KEY_SYNC_SUCCESS = "KEY_SYNC_SUCCESS"
        const val CHANNEL_ID = "SYNC_NOTIFICATION_CHANNEL"
        const val NOTIFICATION_ID = 2
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            Log.d("SyncWorker", "🚀 SyncWorker started")

            profileUseCase.syncProfile()
            Log.d("SyncWorker", "✅ Profile sync completed")

            syncSettings()
            Log.d("SyncWorker", "✅ Settings sync completed")

            syncQuizData()
            Log.d("SyncWorker", "✅ Quiz data sync completed")

            val outputData = Data.Builder()
                .putBoolean(KEY_SYNC_SUCCESS, true)
                .build()

            Log.d("SyncWorker", "✅ SyncWorker completed successfully")
            Result.success(outputData)
        } catch (e: Exception) {
            Log.e("SyncWorker", "❌ Error in SyncWorker: ${e.message}", e)
            Result.failure()
        }
    }

    private suspend fun updateProfileView() {
        profileInteractor.updateNick()
        profileInteractor.addLoadingTask("sync profile", 50)
    }

    private fun syncSettings() {
        runBlocking {
            val profile = profileUseCase.getProfileFlow()?.first()
            val prefManager = PreferencesManager(context)
            SettingConfigObject.updateSettings(
                prefManager.getSettings().copy(
                    tpovId = profile?.tpovId ?: 0,
                    nickname = profile?.nickname ?: "Nickname in server is not found",
                    languages = listOf((profile?.languages ?: "").toLanguageUtils())
                )
            )
            prefManager.saveSettings(settingsConfig)
            updateProfileView()
        }
    }

    private suspend fun syncQuizData() {
        Log.d("SyncWorker", "🔄 Starting quiz data synchronization")

        for (event in EventQuiz.entries) {
            Log.d("SyncWorker", "📋 Processing event: ${event.name}")
            if (event != EventQuiz.QUIZ_HOME) return
            var lockResult: LockServerResult
            while (true) {
                lockResult = syncInteractor.isLockServer(event)
                when (lockResult) {
                    is LockServerResult.Success -> {
                        Log.d("SyncWorker", "🔒 Successfully locked ${event.name}")
                        break
                    }

                    is LockServerResult.AlreadyLocked -> {
                        Log.d("SyncWorker", "⏳ ${event.name} is locked, waiting...")
                        delay(1000)
                    }

                    is LockServerResult.Error -> {
                        Log.e("SyncWorker", "❌ Failed to lock ${event.name}")
                        return
                    }
                }
            }

            Log.d("SyncWorker", "🔄 Starting syncQuizes for ${event.name}")
            val result = syncInteractor.syncQuizes(event, exceptionInteractor)

            when (result) {
                is SyncStructureResult.Success -> {
                    Log.d("SyncWorker", "✅ Successfully synced ${event.name}")
                    try {
                        showNotification("Sync Complete", "Updated quizzes for ${event.name}.", context)
                        val unlockResult = syncInteractor.isLockServer(event)

                        if (unlockResult is LockServerResult.Error) {
                            Log.e("SyncWorker", "❌ Failed to unlock ${event.name}, rolling back")
                            syncInteractor.rollbackStructureData(event)
                            return
                        } else {
                            Log.d("SyncWorker", "🔓 Successfully unlocked ${event.name}")
                        }
                    } catch (e: Exception) {
                        Log.e("SyncWorker", "❌ Error in post-sync cleanup for ${event.name}: ${e.message}", e)
                        syncInteractor.rollbackStructureData(event)
                        return
                    }
                }

                is SyncStructureResult.Error -> {
                    Log.e("SyncWorker", "❌ Sync failed for ${event.name}: ${result.error}")
                    syncInteractor.rollbackStructureData(event)
                    return
                }
            }
        }

        Log.d("SyncWorker", "🎉 All quiz data synchronization completed successfully")
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
