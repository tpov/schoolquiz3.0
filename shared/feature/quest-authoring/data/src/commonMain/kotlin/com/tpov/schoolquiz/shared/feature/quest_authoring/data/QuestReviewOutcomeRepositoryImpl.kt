package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcomeRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestionReviewFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestReviewOutcomeSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestReviewOutcomeRepository

/**
 * Проекция исходов модельной проверки (история 4.16): data-исходы вью в доменную форму.
 *
 * Локальной половины нет: статус заявки и список заваленных вопросов — серверная правда, кэш
 * только врал бы между свипами воркера. Отказ транспорта — [SyncFailure] поверх ошибки.
 */
class QuestReviewOutcomeRepositoryImpl(
    private val remote: QuestArenaSubmissionRemoteDataSource,
) : QuestReviewOutcomeRepository {
    override suspend fun fetch(
        submissionIds: Set<String>,
        legacyDraftIds: Set<String>,
    ): Result<List<QuestReviewOutcomeSnapshot>> =
        try {
            val outcomes = remote.fetchOutcomes(
                QuestSubmissionOutcomeRequest(
                    submissionIds = submissionIds,
                    legacyDraftIds = legacyDraftIds,
                ),
            )
            Result.success(outcomes.map { it.toSnapshot() })
        } catch (error: SyncFailure) {
            Result.failure(error)
        }

    override suspend fun advance(submissionId: String): Result<Unit> =
        try {
            remote.advanceOutcome(submissionId)
            Result.success(Unit)
        } catch (error: SyncFailure) {
            Result.failure(error)
        }
}

private fun com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcome.toSnapshot():
    QuestReviewOutcomeSnapshot =
    QuestReviewOutcomeSnapshot(
        submissionId = submissionId,
        draftId = draftId,
        status = status,
        rejectionReason = rejectionReason,
        failedQuestions = failedQuestions.map { failure ->
            QuestionReviewFailure(
                lessonId = failure.lessonId,
                questionId = failure.questionId,
                stage = failure.stage,
                reason = failure.reason,
            )
        },
        detectedLanguage = detectedLanguage,
        outcomeReady = outcomeReady,
    )
