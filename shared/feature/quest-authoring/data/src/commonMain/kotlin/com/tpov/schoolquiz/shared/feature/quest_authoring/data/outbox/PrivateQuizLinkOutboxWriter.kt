package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строка общей очереди для ручного закрытия приватной ссылки (AD-29/UX-16, история 8.3).
 *
 * Callable очередь не зовёт: единственный транспорт — приёмник мутаций (AD-6), и только там
 * повтор по ключу возвращает сохранённый ответ. Локальной половины у записи нет: ничего в
 * таблицах фичи не меняется, `expectedVersion` пуст — ссылка клиентом не версионируется.
 * Тело — ровно адрес, какие принимает серверный обработчик: лишнего в намерении быть не может.
 */
object PrivateQuizLinkOutboxWriter {
    /** Ссылка на квест — то же пространство имён, что у заявки на арену (NFR4). */
    fun questRef(catalogId: String, questId: String): String =
        QuestArenaOutboxWriter.DRAFT_REF_PREFIX + questId + ":link:" + catalogId

    fun record(
        ownerUid: String,
        catalogId: String,
        questId: String,
        requestedAtMs: Long,
    ): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.CLOSE_PRIVATE_QUIZ_LINK, "$catalogId/$questId"),
            ownerUid = ownerUid,
            operation = OutboxOperations.CLOSE_PRIVATE_QUIZ_LINK,
            payload = payload(catalogId, questId).toString(),
            entityRef = questRef(catalogId, questId),
            state = OutboxState.WAITING,
            createdAtMs = requestedAtMs,
        )

    private fun payload(
        catalogId: String,
        questId: String,
    ): JsonObject =
        buildJsonObject {
            put("catalogId", JsonPrimitive(catalogId))
            put("questId", JsonPrimitive(questId))
        }
}
