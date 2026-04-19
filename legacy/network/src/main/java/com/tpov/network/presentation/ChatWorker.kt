package com.tpov.network.presentation

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tpov.network.domain.ChatUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val chatUseCase: ChatUseCase
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "CHAT_NOTIFICATION_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val hasNewMessages = chatUseCase.checkForNewMessages()
            if (hasNewMessages) {
                showNotification("New Message", "You have new messages in chat")
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, message: String) {
        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        val name = "Chat Notifications"
        val descriptionText = "Notifications for new chat messages"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
