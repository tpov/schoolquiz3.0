package com.tpov.schoolquiz.platform.firebase.auth

import com.tpov.schoolquiz.platform.firebase.push.DeviceTokenRelease
import com.tpov.schoolquiz.shared.core.outbox.AccountSwitchGuard
import com.tpov.schoolquiz.shared.core.outbox.OwnerDataKind
import com.tpov.schoolquiz.shared.core.outbox.SwitchReadiness
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountDataKind
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountSwitchLoss
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.GoogleLinkOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Смена аккаунта как последовательность шагов AD-8 — без единого типа Firebase, чтобы порядок
 * шагов проверялся на фейках.
 *
 * Порядок закреплён историями 6.3 и 6.4: слить очередь прежнего владельца → если что-то осталось,
 * остановиться и спросить → по согласию снять токен устройства, войти, и только после удавшегося
 * входа снять следы прежнего владельца: записи очереди, затем личные следы. Не удался вход —
 * прежний аккаунт остаётся, токен возвращается, следы не тронуты.
 *
 * Между вопросом и ответом сам вход держится здесь как [Pending.signIn] — замыкание над
 * учётными данными, полученными от Google. Это выбор из двух: иначе системный выбор аккаунта
 * пришлось бы показывать второй раз. Держится ровно до ответа: подтверждение и отказ оба его
 * снимают, а новый вызов [begin] перекрывает.
 *
 * Переключение выполняется в [scope], а не в том, кто его позвал. Экран профиля живёт до первого
 * ухода с него, а между `signInWithCredential` и снятием следов лежит сеть: игрок, ушедший с экрана
 * сразу после подтверждения, получал сменённый аккаунт и оставшиеся строки прежнего владельца —
 * отменённая корутина не отменяет уже начатую задачу Firebase. Отмена ожидания здесь отменяет
 * только ожидание.
 */
class AccountSwitch(
    private val switchGuard: AccountSwitchGuard,
    private val deviceTokenRelease: DeviceTokenRelease,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private class Pending(
        val owners: List<String>,
        val signIn: suspend () -> String,
    )

    private var pending: Pending? = null

    /** Есть ли переключение, остановленное на вопросе. */
    val hasPending: Boolean get() = pending != null

    /**
     * Сливает очередь [previousUid] и либо переключает сразу, либо останавливается на вопросе.
     *
     * [signIn] — сам вход; сюда он приходит замыканием, потому что учётные данные принадлежат
     * платформе, а не этому классу. Возвращает `uid` нового владельца: снимать следы надо у всех,
     * кроме него, а кто он — знает только тот, кто входил.
     *
     * [previousUid] может быть пустым: действующей сессии может не быть вовсе. Кого тогда
     * предстоит забыть, решает не аутентификация, а очередь — [AccountSwitchGuard.flushBefore].
     */
    suspend fun begin(
        previousUid: String,
        signIn: suspend () -> String,
    ): GoogleLinkOutcome {
        pending = null
        val readiness: SwitchReadiness = switchGuard.flushBefore(previousUid)
        if (readiness.needsWarning) {
            pending = Pending(readiness.owners, signIn)
            return GoogleLinkOutcome.SwitchAwaitsConsent(readiness.toLoss())
        }
        return complete(readiness.owners, signIn)
    }

    /**
     * Игрок согласился. Вопрос снимается до входа, а не после: повторное нажатие не должно
     * запустить второй вход по тем же учётным данным.
     */
    suspend fun confirm(): GoogleLinkOutcome {
        val held = checkNotNull(pending) { "Нет переключения, ожидающего согласия" }
        pending = null
        return complete(held.owners, held.signIn)
    }

    /** Игрок отказался: прежний аккаунт остаётся, ничего не удалено. */
    fun decline() {
        pending = null
    }

    private suspend fun complete(
        owners: List<String>,
        signIn: suspend () -> String,
    ): GoogleLinkOutcome =
        // Уже начатое переключение доводится до конца, даже если ждать его стало некому: между
        // входом и снятием следов расхождение «аккаунт сменён, строки прежнего остались» — ровно
        // то, что AD-8 запрещает.
        scope.async {
            // Токен устройства снимается с прежнего аккаунта до смены аутентификации: после неё
            // правило владельца документ уже не отдаст (Story 10.5). Не удалось — вход не
            // блокируется, токен допишется под новым uid, а чужое сообщение отсеет сервис приёма.
            owners.forEach { deviceTokenRelease.release(it) }
            switchGuard.switchAndForget(owners) {
                try {
                    signIn()
                } catch (
                    @Suppress("TooGenericExceptionCaught") error: Throwable,
                ) {
                    // Вход не случился — прежний аккаунт остаётся активным, и снятый документ надо
                    // вернуть, иначе это устройство не получит его push до перезапуска процесса.
                    // Следы прежнего владельца при этом не тронуты: снимать их без входа нечем
                    // оправдать.
                    owners.forEach { deviceTokenRelease.reclaim(it) }
                    throw error
                }
            }
            GoogleLinkOutcome.Switched
        }.await()
}

/** Числа и имена наружу — доменной формой, без типов ядра очереди. */
private fun SwitchReadiness.toLoss() =
    AccountSwitchLoss(
        waiting = remainder.waiting,
        waitingPrecondition = remainder.waitingPrecondition,
        conflicted = remainder.conflicted,
        quarantined = remainder.quarantined,
        synchronous = remainder.synchronous,
        isKnown = isKnown,
        namedData = namedLosses.map { it.toAccountDataKind() },
    )

private fun OwnerDataKind.toAccountDataKind() =
    when (this) {
        OwnerDataKind.ANSWER_HISTORY -> AccountDataKind.ANSWER_HISTORY
        OwnerDataKind.REPETITION_SCHEDULE -> AccountDataKind.REPETITION_SCHEDULE
        OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS -> AccountDataKind.UNPUBLISHED_QUEST_DRAFTS
    }
