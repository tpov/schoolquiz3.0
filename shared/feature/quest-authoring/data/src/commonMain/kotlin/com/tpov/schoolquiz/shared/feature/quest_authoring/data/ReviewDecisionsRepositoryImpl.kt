package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWireEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWireMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWirePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWireQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWireRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionVerdict
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionsAccessRefusedException
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewDecisionsRepository
import kotlinx.coroutines.CancellationException

/**
 * Репозиторий «Решений» (история 4.17, UX-22): тонкий перевод строгой серверной формы в домен.
 *
 * Отказ по роли проходит насквозь ([ReviewDecisionsAccessRefusedException]) — это ответ, а не
 * сбой; остальные отказы остаются в Result.failure и экран показывает ошибку перечитки.
 */
class ReviewDecisionsRepositoryImpl(
    private val remote: ReviewDecisionsRemoteDataSource,
) : ReviewDecisionsRepository {
    override suspend fun fetch(
        filter: ReviewDecisionFilter,
        cursor: ReviewDecisionCursor?,
    ): Result<ReviewDecisionPage> = call { remote.fetch(ReviewDecisionsWireRequest(filter, cursor)).toDomain() }

    override suspend fun mark(
        verdictId: String,
        questionId: String,
        mark: ReviewDecisionMarkValue,
        comment: String,
    ): Result<ReviewDecisionMark> = call { remote.mark(verdictId, questionId, mark, comment).toDomain() }

    private inline fun <T> call(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: ReviewDecisionsAccessRefusedException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
}

private fun ReviewDecisionsWirePage.toDomain(): ReviewDecisionPage =
    ReviewDecisionPage(
        entries = entries.map { it.toDomain() },
        hasMore = hasMore,
        nextCursor = nextCursor,
    )

private fun ReviewDecisionsWireEntry.toDomain(): ReviewDecision =
    ReviewDecision(
        unreadable = unreadable,
        verdictId = verdictId,
        submissionId = submissionId,
        questTitle = questTitle,
        lessonId = lessonId,
        lessonTitle = lessonTitle,
        stage = stage,
        attempt = attempt,
        revision = revision,
        model = model,
        promptVersion = promptVersion,
        sources = sources,
        rationale = rationale,
        questionId = questionId,
        verdict =
            verdict?.let { name ->
                ReviewDecisionVerdict.entries.firstOrNull { it.name == name }
            },
        reason = reason,
        question = question?.toDomain(),
        mark =
            if (markValue === null) {
                null
            } else {
                ReviewDecisionMark(
                    value = markValue,
                    comment = markComment,
                    adminUid = markAdminUid,
                    updatedAtMs = markUpdatedAtMs,
                )
            },
    )

private fun ReviewDecisionsWireQuestion.toDomain(): ReviewDecisionQuestion =
    ReviewDecisionQuestion(type = type, language = language, text = text, payload = payload)

private fun ReviewDecisionsWireMark.toDomain(): ReviewDecisionMark =
    ReviewDecisionMark(value = value, comment = comment, adminUid = "", updatedAtMs = updatedAtMs)
