package com.tpov.schoolquiz.platform.firebase.push

import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository

/**
 * Что делать с пришедшим сообщением (Story 10.4, 10.5).
 *
 * Push несёт повод, а не данные (AD-20): единственное действие по любому сообщению для своего
 * аккаунта — поднять тот же вход синхронизации, которым пользуется ручная синхронизация.
 * Собственного пути применения изменений здесь нет и DAO сюда не попадает.
 *
 * Уведомление показывается только по событию из реестра. Сообщение с чужим `targetUid` не
 * показывается и синхронизацию не поднимает: это токен, который прежний владелец не успел снять,
 * и его снимает сервер по просьбе этого же устройства.
 */
class PushMessageHandler(
    private val scheduler: SyncScheduler,
    private val notifier: PushNotifier,
    private val authRepository: AuthRepository,
    private val foreignTokenDetacher: ForeignTokenDetacher,
) {
    suspend fun handle(message: PushMessage) {
        val targetUid = message.targetUid.orEmpty()
        if (targetUid.isNotBlank() && targetUid != authRepository.currentUid()) {
            foreignTokenDetacher.detach(targetUid)
            return
        }
        scheduler.enqueueManualSync()
        // Без адресата нельзя знать, чьё это событие, — сходить за данными безопасно, а показать
        // в шторке нет.
        val event = if (targetUid.isBlank()) null else PushEvents.byType(message.eventType)
        if (event != null) notifier.notify(event)
    }
}
