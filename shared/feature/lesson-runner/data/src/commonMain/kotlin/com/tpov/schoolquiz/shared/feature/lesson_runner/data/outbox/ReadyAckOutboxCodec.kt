package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Клиентская половина ready-ACK — кодирование намерения в строку очереди
 * (ONLINE-O2 §4 и §6, история 2.6 эпика `epic-ordinary-delivery`).
 *
 * Ready-ACK — мутация, как и остальные (AD-7), поэтому кодировка обязана дать движку ровно то,
 * что он умеет отправлять: ключ из `OutboxOperations.mutationKey`, замороженное тело, ссылку на
 * локальную сущность. Контракт предписывает закрепить здесь две вещи:
 *
 * - **имя операции** — `OutboxOperations.READY_ACK` (`lesson_runner.READY_ACK`), общее с реестром
 *   `MUTATION_HANDLERS` на сервере;
 * - **кодирование тройки** `(attemptId, questionIndex, windowId)` в sourceId — identity попытки
 *   плюс позиция вопроса плюс окно, как требует §4; ключ рождается вместе с намерением и не
 *   меняется при retry (§6), поэтому тройка кодируется один раз, при постановке.
 *
 * Сроков в теле нет: назначение deadline — серверный факт выдачи, клиент его не предлагает и не
 * подтверждает своим временем (§5, замещающих часов нет). Пользователя кодировки — онлайн-адаптер
 * рановера, который появится у потребителя контракта; сам рановер офлайн-путей ready-ACK не
 * ставит (§1: офлайн-пути контрактом не трогаются).
 */
object ReadyAckOutboxCodec {

    /** Префикс ссылки на сущность; пространство имён владеющей фичи, как у строк прохождения. */
    const val ENTITY_REF_PREFIX: String = "lesson_runner:ready_ack:"

    /**
     * sourceId перехода: тройка в одной строке, иные части кодировки запрещены.
     *
     * [attemptId] кладётся как есть — идентификатор попытки уже несёт своё «attempt-» (образец
     * формы контракта: `attempt-7-q3-window-1`, `OnlineO2IdentityRetryContractTest`), и второй
     * префикс сделал бы ключ отличимым от закреплённого образца. Ключ сервера принимает только
     * `[A-Za-z0-9_-]`, а `mutationKey` меняет лишь точку операции, поэтому каждая часть тройки
     * обязана уже лежать в этом charset: контроль честнее перекодировки, которая спрятала бы
     * испорченный идентификатор в валидный с виду ключ (AD-6).
     */
    fun sourceId(
        attemptId: String,
        questionIndex: Int,
        windowId: String,
    ): String {
        require(attemptId.isNotBlank()) { "attemptId must not be blank" }
        require(windowId.isNotBlank()) { "windowId must not be blank" }
        require(questionIndex >= 0) { "questionIndex must be non-negative" }
        listOf(attemptId, windowId).forEach { part ->
            require(part.matches(SAFE_CHARSET)) { "sourceId part must fit [A-Za-z0-9_-]: $part" }
        }
        return "$attemptId-q$questionIndex-window-$windowId"
    }

    /** Ссылка на сущность: квитанция подтверждает вопрос попытки, а не отдельную локальную запись. */
    fun entityRef(attemptId: String): String = ENTITY_REF_PREFIX + attemptId

    /** Ключ мутации перехода — выведен из тройки и потому одинаков при любой попытке отправки. */
    fun mutationKey(
        attemptId: String,
        questionIndex: Int,
        windowId: String,
    ): String = OutboxOperations.mutationKey(OutboxOperations.READY_ACK, sourceId(attemptId, questionIndex, windowId))

    /**
     * Строка очереди для готового к ответу вопроса.
     *
     * Тело замораживается в момент намерения (AD-2): идентичность попытки, позиция и окно — и
     * больше ничего; часы квитанции ставит сервер, и клиентское «сейчас» в очередь не попадает.
     * Запись неверсионируемая: окна создаются один раз и не редактируются.
     */
    fun buildReadyAckRow(
        identity: AttemptIdentity,
        questionIndex: Int,
        windowId: String,
        createdAtMs: Long,
    ): OutboxEntity {
        require(questionIndex >= 0) { "questionIndex must be non-negative" }
        val payload =
            buildJsonObject {
                put("attemptId", identity.id.value)
                put("userId", identity.userId)
                put("questionIndex", questionIndex)
                put("windowId", windowId)
                put("createdAtMs", createdAtMs)
            }
        return OutboxEntity(
            mutationId = mutationKey(identity.id.value, questionIndex, windowId),
            ownerUid = identity.userId,
            operation = OutboxOperations.READY_ACK,
            payload = payload.toString(),
            entityRef = entityRef(identity.id.value),
            expectedVersion = null,
            state = OutboxState.WAITING.name,
            attemptCount = 0,
            nextRetryAtMs = 0L,
            lastError = null,
            createdAtMs = createdAtMs,
        )
    }

    private val SAFE_CHARSET: Regex = Regex("[A-Za-z0-9_-]+")
}
