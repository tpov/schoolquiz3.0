package com.tpov.schoolquiz.shared.feature.internet.social.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteSubmission
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строки общей очереди для опросов (UX-O7 §2/§4, история 8.2).
 *
 * Callable очередь не зовёт: единственный транспорт — приёмник мутаций (AD-6), где ключ
 * идемпотентности не даёт одному действию стать двумя (AD-1). Локальной половины у записей нет
 * (AD-25): ничего в таблицах фичи не меняется, `expectedVersion` пуст — опрос клиентом
 * не версионируется.
 *
 * Ключ голоса выводится из тройки идентичности серия/revision/вопрос (§2) и потому не меняется
 * ни при ретрае, ни при повторной постановке того же намерения (§6.1). Ключ публикации выводится
 * из серии и ожидаемой следующей revision: то же намерение — тот же ключ, новое редакционное
 * намерение — новый.
 */
object PollOutboxWriters {
    /** Ссылка на серию — пространство имён владеющей фичи, как и у операций (NFR4). */
    const val SERIES_REF_PREFIX: String = "polls.SERIES:"

    fun seriesRef(seriesId: String): String = SERIES_REF_PREFIX + seriesId

    /** §2: `sourceId = "series-<seriesId>-r<revisionId>-q<questionIndex>"`. */
    fun voteSourceId(
        seriesId: String,
        revisionId: Long,
        questionIndex: Int,
    ): String = "series-$seriesId-r$revisionId-q$questionIndex"

    /** Ожидаемая следующая revision в ключе публикации: то же намерение — тот же ключ. */
    fun publishSourceId(
        seriesId: String,
        expectedRevisionId: Long,
    ): String = "series-$seriesId-r${expectedRevisionId + 1}"

    fun voteRecord(submission: PollVoteSubmission): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId =
                OutboxOperations.mutationKey(
                    OutboxOperations.SUBMIT_POLL_VOTE,
                    voteSourceId(
                        submission.command.seriesId,
                        submission.command.revisionId,
                        submission.command.questionIndex,
                    ),
                ),
            ownerUid = submission.ownerUid,
            operation = OutboxOperations.SUBMIT_POLL_VOTE,
            payload = votePayload(submission).toString(),
            entityRef = seriesRef(submission.command.seriesId),
            state = OutboxState.WAITING,
            createdAtMs = submission.requestedAtMs,
        )

    fun publishRecord(submission: PollPublishSubmission): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId =
                OutboxOperations.mutationKey(
                    OutboxOperations.PUBLISH_POLL_SERIES,
                    publishSourceId(submission.command.seriesId, submission.command.expectedRevisionId),
                ),
            ownerUid = submission.ownerUid,
            operation = OutboxOperations.PUBLISH_POLL_SERIES,
            payload = publishPayload(submission).toString(),
            entityRef = seriesRef(submission.command.seriesId),
            state = OutboxState.WAITING,
            createdAtMs = submission.requestedAtMs,
        )

    /**
     * Тело голоса — канонический JSON одного набора (§2): тройка идентичности, набор без дублей,
     * момент фиксации набора. Замораживается до dispatch и не меняется на retry (AD-6).
     */
    private fun votePayload(submission: PollVoteSubmission) =
        buildJsonObject {
            put("seriesId", submission.command.seriesId)
            put("revisionId", submission.command.revisionId)
            put("questionIndex", submission.command.questionIndex)
            put(
                "selectedOptionIds",
                buildJsonArray {
                    submission.command.selectedOptionIds.forEach { add(JsonPrimitive(it)) }
                },
            )
            put("confirmedAtMs", submission.confirmedAtMs)
        }

    /** Тело публикации — содержание серии целиком (единый draft, AD-28). */
    private fun publishPayload(submission: PollPublishSubmission) =
        buildJsonObject {
            put("seriesId", submission.command.seriesId)
            put("title", submission.command.title)
            put("expectedRevisionId", submission.command.expectedRevisionId)
            put(
                "questions",
                buildJsonArray {
                    submission.command.questions.forEach { question ->
                        add(
                            buildJsonObject {
                                put("questionIndex", question.questionIndex)
                                put("text", question.text)
                                put("mode", question.mode.wire)
                                put(
                                    "options",
                                    buildJsonArray {
                                        question.options.forEach { option ->
                                            add(
                                                buildJsonObject {
                                                    put("optionId", option.optionId)
                                                    put("text", option.text)
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
}
