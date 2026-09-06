package com.tpov.schoolquiz.shared.feature.quest.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfChange
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строка общей очереди для переноса квеста на полку.
 *
 * Имена — в пространстве имён фичи quest (NFR4): операцию и ссылку на сущность объявляет та же
 * фича, чей Koin-модуль регистрирует обработчик карантина. Имя операции — часть контракта с
 * приёмником (`MUTATION_HANDLERS` в `functions/index.js`): незнакомую он отвергает окончательно.
 *
 * Локальной половины у этой записи нет (AD-25): в очередь кладётся только намерение, строка квеста
 * не трогается. Поэтому строка собирается прямо в [OutboxRecord], а не в сущность Room для чужой
 * транзакции — транзакции с чем-то ещё здесь не бывает.
 */
object QuestShelfOutbox {
    /** `quest.SET_SHELF` — так перенос называет и клиент, и приёмник; имя объявлено в общем реестре. */
    const val OPERATION: String = OutboxOperations.SET_SHELF

    /** Ссылка на квест для пометки его карточки (AD-14): `quest.QUEST:<questId>`. */
    const val ENTITY_REF_PREFIX: String = "quest.QUEST:"

    fun entityRef(questId: QuestId): String = ENTITY_REF_PREFIX + questId.value

    /** Квест из ссылки, если ссылка наша; чужую ссылку обработчик карантина не разбирает. */
    fun questIdOf(entityRef: String?): QuestId? =
        entityRef
            ?.takeIf { it.startsWith(ENTITY_REF_PREFIX) }
            ?.removePrefix(ENTITY_REF_PREFIX)
            ?.takeIf { it.isNotBlank() }
            ?.let(::QuestId)

    /**
     * Запись очереди из намерения.
     *
     * Ключ выводится из ключа намерения, а не из `questId`: один квест переносят много раз, и
     * каждый перенос обязан уехать под своим ключом (AD-2). Тело — ровно то, что читает
     * `applySetPublicQuestShelf` на сервере: `questId` и `targetShelf`.
     */
    fun record(
        change: QuestShelfChange,
        createdAtMs: Long,
    ): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(OPERATION, change.id),
            ownerUid = change.ownerUid,
            operation = OPERATION,
            payload =
                buildJsonObject {
                    put("questId", change.questId.value)
                    put("targetShelf", change.targetShelf)
                }.toString(),
            entityRef = entityRef(change.questId),
            // Перенос не версионируется: сервер применяет его к текущему состоянию квеста, и
            // расхождения версий, которое требовало бы решения игрока, здесь нет (AD-24).
            expectedVersion = null,
            state = OutboxState.WAITING,
            createdAtMs = createdAtMs,
        )
}
