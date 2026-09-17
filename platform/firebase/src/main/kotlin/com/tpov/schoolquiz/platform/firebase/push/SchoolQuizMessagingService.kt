package com.tpov.schoolquiz.platform.firebase.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext

/**
 * Точка входа FCM. Сама ничего не решает: новый токен отдаёт источнику токена, сообщение —
 * обработчику. Исключение отсюда наверх не уходит: сообщение, которое не удалось разобрать, — не
 * повод ронять процесс.
 */
class SchoolQuizMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        runCatching { GlobalContext.get().get<FcmTokenSource>().publish(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        runCatching {
            val handler = GlobalContext.get().get<PushMessageHandler>()
            // Обработчик ждёт текущий uid из потока Auth. В холодном процессе, поднятом FCM, первый
            // колбэк Auth обычно приходит за миллисекунды, но ждать его без предела нельзя: поток
            // сервиса — это бюджет FCM на доставку, и просроченное сообщение лучше пропустить,
            // чем повесить процесс. Пропуск безопасен: push — повод, данные приедут плановым sync.
            runBlocking { withTimeoutOrNull(HANDLE_TIMEOUT_MS) { handler.handle(PushMessage.fromData(message.data)) } }
        }
    }

    private companion object {
        const val HANDLE_TIMEOUT_MS = 10_000L
    }
}
