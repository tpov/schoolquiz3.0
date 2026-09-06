package com.tpov.schoolquiz.platform.firebase.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tpov.schoolquiz.platform.firebase.R

/**
 * Уведомление в шторке по событию из реестра (Story 10.4).
 *
 * Заголовок и текст берутся из строковых ресурсов приложения по ключу, выведенному из
 * `eventType`; ключа нет — уведомления нет. Без разрешения `POST_NOTIFICATIONS` не показывает
 * ничего и не падает. Тап открывает стартовую Activity приложения: у неё `singleTop`, второй
 * копии не появится.
 */
class AndroidPushNotifier(
    private val context: Context,
) : PushNotifier {
    override fun notify(event: PushEvent) {
        val title = text(event.titleKey)
        val body = text(event.textKey)
        if (!canPost() || title == null || body == null) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(channel())
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_push_small)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        // Идентификатор — хэш имени события: второе такое же событие заменяет первое, а не
        // складывается в стопку.
        runCatching { manager.notify(event.eventType.hashCode(), notification) }
    }

    private fun canPost(): Boolean {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun channel(): NotificationChannel =
        NotificationChannel(
            CHANNEL_ID,
            text(CHANNEL_NAME_KEY) ?: CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT,
        )

    private fun openApp(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Строка по имени, а не через R: тексты живут в ресурсах приложения, и этому модулю их R
     * недоступен. Тем же способом приложение читает сгенерированный `default_web_client_id`.
     */
    private fun text(key: String): String? {
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    companion object {
        const val CHANNEL_ID = "push_events"
        const val CHANNEL_NAME_KEY = "push_channel_name"
    }
}
