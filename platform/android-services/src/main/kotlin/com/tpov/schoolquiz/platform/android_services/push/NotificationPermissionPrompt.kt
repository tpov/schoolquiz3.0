package com.tpov.schoolquiz.platform.android_services.push

import android.content.Context
import android.os.Build

/**
 * Спрашивать ли разрешение на уведомления (Story 10.2).
 *
 * Один раз — при первом запуске после обновления. Отказ игрока запоминается тем же флагом, что и
 * согласие: повторять вопрос на каждом запуске нельзя, а передумать можно в настройках системы.
 * До Android 13 разрешения нет и спрашивать нечего.
 */
class NotificationPermissionPrompt(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldAsk(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !prefs.getBoolean(KEY_ASKED, false)

    fun markAsked() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "schoolquiz_push"
        const val KEY_ASKED = "post_notifications_asked"
    }
}
