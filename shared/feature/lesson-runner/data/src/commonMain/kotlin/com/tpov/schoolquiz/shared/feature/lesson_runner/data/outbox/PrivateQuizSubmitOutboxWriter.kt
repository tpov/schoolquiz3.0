package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizSubmission
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строка общей очереди для подачи попытки приватного квиза (AD-29/AD-31, история 8.4).
 *
 * Ключ подачи — сам `startId`: сервер хранит терминал под ним, и повтор записи возвращает
 * сохранённый результат, не тратя лимит второй раз (AD-6). Тело замораживается в момент
 * финиша — ровно named-поля приёмника: адрес, порция и ответы; лишнего в намерении быть
 * не может. `expectedVersion` пуст: попытка клиентом не версионируется. В реестре согласий
 * с платными условиями операции нет — приватный проход бесплатен (8.5).
 */
object PrivateQuizSubmitOutboxWriter {
    const val ATTEMPT_REF_PREFIX: String = "private_quiz:attempt:"

    fun attemptRef(startId: String): String = ATTEMPT_REF_PREFIX + startId

    fun mutationId(startId: String): String =
        OutboxOperations.mutationKey(OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT, startId)

    fun record(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
        submission: PrivateQuizSubmission,
        createdAtMs: Long,
    ): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = mutationId(startId),
            ownerUid = ownerUid,
            operation = OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT,
            payload = payload(ownerUid, catalogId, questId, startId, submission).toString(),
            entityRef = attemptRef(startId),
            state = OutboxState.WAITING,
            createdAtMs = createdAtMs,
        )

    private fun payload(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
        submission: PrivateQuizSubmission,
    ) = buildJsonObject {
        put("ownerUid", ownerUid)
        put("catalogId", catalogId)
        put("questId", questId)
        put("startId", startId)
        put("served", buildJsonArray {
            submission.served.forEach { served ->
                add(
                    buildJsonObject {
                        put("questionId", served.questionId)
                        put("codeAnswerIndex", served.codeAnswerIndex)
                    },
                )
            }
        })
        put("answers", buildJsonArray {
            submission.answers.forEach { answer ->
                add(
                    buildJsonObject {
                        put("questionId", answer.questionId)
                        put("codeAnswerIndex", answer.codeAnswerIndex)
                        // Пусто — это null, а не пропущенное поле: показан-без-ответа значим
                        // и на приёмнике, и в книге попытки (QuestArenaOutboxWriter: та же форма).
                        put("answerPayload", answer.answerPayload?.let(::JsonPrimitive) ?: JsonNull)
                        put("answeredAtMs", answer.answeredAtMs)
                    },
                )
            }
        })
    }
}
