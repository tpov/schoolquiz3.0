package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewActionSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строка общей очереди для решения ревьюера.
 *
 * Callable `submitReviewAction` очередь не зовёт: единственный транспорт — приёмник мутаций (AD-6),
 * и только там ключ идемпотентности защищает репутацию ревьюера от двойного начисления (AD-1).
 *
 * Локальной половины у записи нет (AD-25): строка задания не меняется, и `expectedVersion` пуст —
 * задание не версионируется клиентом, предусловия «ещё моё, ещё открыто» проверяет сервер (AD-27).
 */
object SubmitReviewOutboxWriter {
    /** Ссылка на задание — пространство имён владеющей фичи, как и у операции (NFR4). */
    const val ASSIGNMENT_REF_PREFIX: String = "quest_authoring.ASSIGNMENT:"

    /**
     * Версия формы тела. Живёт внутри тела, а не в колонке: ядро в тело не смотрит (AD-5), а
     * приёмник по этому числу отличит старую запись, пролежавшую офлайн через обновление, от новой.
     */
    const val FORMAT_VERSION: Int = 1

    fun assignmentRef(assignmentId: String): String = ASSIGNMENT_REF_PREFIX + assignmentId

    fun record(submission: ReviewActionSubmission): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_REVIEW, submission.id),
            ownerUid = submission.ownerUid,
            operation = OutboxOperations.SUBMIT_REVIEW,
            payload = submission.toPayload().toString(),
            entityRef = assignmentRef(submission.command.assignmentId),
            state = OutboxState.WAITING,
            createdAtMs = submission.requestedAtMs,
        )

    /**
     * Тело — сериализованная команда.
     *
     * Имена полей — те же, что принимает callable `submitReviewAction`: приёмник разбирает их одним
     * и тем же кодом, а переезд в очередь не повод их менять.
     */
    private fun ReviewActionSubmission.toPayload(): JsonObject =
        buildJsonObject {
            put("formatVersion", FORMAT_VERSION)
            put("actionId", id)
            put("requestedAtMs", requestedAtMs)
            command.writeTo(this)
        }

    private fun SubmitReviewActionCommand.writeTo(builder: JsonObjectBuilder) {
        with(builder) {
            put("assignmentId", assignmentId)
            put("lessonId", lessonId)
            put("kind", kind.name)
            putNullable("score", score)
            putNullable("language", language)
            putNullable("targetReviewId", targetReviewId)
            put("translatedQuestions", buildJsonArray { translatedQuestions.forEach { add(it.toJson()) } })
            put("segmentResults", buildJsonArray { segmentResults.forEach { add(it.toJson()) } })
        }
    }

    private fun ReviewQuestion.toJson(): JsonObject =
        buildJsonObject {
            put("id", id)
            put("draftId", draftId)
            put("lessonId", lessonId)
            put("type", type)
            put("language", language)
            put("languageLevel", languageLevel)
            put("difficulty", difficulty)
            put("order", order)
            put("text", text)
            putNullable("imagePath", imagePath)
            put("payload", payload)
            put("updatedAtMs", updatedAtMs)
        }

    private fun ReviewSegmentDecision.toJson(): JsonObject =
        buildJsonObject {
            put("questionId", questionId)
            put("segmentKey", segmentKey)
            put("accepted", accepted)
        }

    /** Пусто — это `null`, а не пропущенное поле: разница на приёмнике значимая. */
    private fun JsonObjectBuilder.putNullable(
        name: String,
        value: String?,
    ) {
        put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    private fun JsonObjectBuilder.putNullable(
        name: String,
        value: Int?,
    ) {
        put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
    }
}
