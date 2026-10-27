package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWireMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWirePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWireRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.reviewDecisionsWirePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionsAccessRefusedException
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт «Решений» (история 4.17, UX-22): чтение страницы вердиктов и отметка ✓/✗.
 *
 * Имена коллблов — ровно регистрации в `functions/index.js` (сторож —
 * [QuestReviewDecisionsCallableNamesTest]). PERMISSION_DENIED сервера — это requireVerifier,
 * а не сбой канала: он переводится в [ReviewDecisionsAccessRefusedException], по которому экран
 * показывает авторскую сторону вкладки (только «Предложения»).
 */
class FirebaseReviewDecisionsRemoteDataSource(
    private val functions: FirebaseFunctions,
) : ReviewDecisionsRemoteDataSource {
    override suspend fun fetch(request: ReviewDecisionsWireRequest): ReviewDecisionsWirePage =
        call {
            val body = buildMap {
                put(FILTER, request.filter.name)
                request.cursor?.let { cursor ->
                    put(
                        CURSOR,
                        buildMap {
                            put(VERDICT_ID, cursor.verdictId)
                            cursor.afterQuestionId?.let { put(AFTER_QUESTION_ID, it) }
                        },
                    )
                }
            }
            val reply =
                functions
                    .getHttpsCallable(ReviewDecisionsCallableNames.FETCH)
                    .withAppTimeout()
                    .call(body)
                    .await()
                    .data
            reviewDecisionsWirePage(reply)
        }

    override suspend fun mark(
        verdictId: String,
        questionId: String,
        mark: ReviewDecisionMarkValue,
        comment: String,
    ): ReviewDecisionsWireMark =
        call {
            val reply =
                functions
                    .getHttpsCallable(ReviewDecisionsCallableNames.MARK)
                    .withAppTimeout()
                    .call(
                        mapOf(
                            VERDICT_ID to verdictId,
                            QUESTION_ID to questionId,
                            MARK to mark.name,
                            COMMENT to comment,
                        ),
                    )
                    .await()
                    .data
            reviewDecisionsMarkReply(reply)
        }

    private inline fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseFunctionsException) {
            if (error.code == FirebaseFunctionsException.Code.PERMISSION_DENIED) {
                throw ReviewDecisionsAccessRefusedException()
            }
            throw error
        }
}

/** Имена callable-поверхности «Решений» — ровно регистрации в `functions/index.js`. */
internal object ReviewDecisionsCallableNames {
    const val FETCH = "fetchReviewDecisions"
    const val MARK = "markReviewDecision"
}

/** Ответ отметки: {verdictId, questionId, mark, comment, updatedAtMs} — строго, как страница. */
private fun reviewDecisionsMarkReply(data: Any?): ReviewDecisionsWireMark {
    val root = data as? Map<*, *> ?: throw IllegalStateException("Decision mark response is not an object")
    val mark = root[MARK] as? String ?: throw IllegalStateException("Decision mark response has no mark")
    val value = ReviewDecisionMarkValue.entries.firstOrNull { it.name == mark }
        ?: throw IllegalStateException("Decision mark response names an unknown mark")
    val updatedAtMs = (root[UPDATED_AT_MS] as? Number ?: throw IllegalStateException("Decision mark response has no time"))
        .toLong()
    return ReviewDecisionsWireMark(
        value = value,
        comment = root[COMMENT] as? String ?: "",
        updatedAtMs = updatedAtMs,
    )
}

private const val FILTER = "filter"
private const val CURSOR = "cursor"
private const val VERDICT_ID = "verdictId"
private const val AFTER_QUESTION_ID = "afterQuestionId"
private const val QUESTION_ID = "questionId"
private const val MARK = "mark"
private const val COMMENT = "comment"
private const val UPDATED_AT_MS = "updatedAtMs"
