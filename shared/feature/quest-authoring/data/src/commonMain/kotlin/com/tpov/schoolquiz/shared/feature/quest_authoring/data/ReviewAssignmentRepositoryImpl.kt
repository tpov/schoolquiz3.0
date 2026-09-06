package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentWithQuestions
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.SubmitReviewOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewActionSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentSyncState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewChecks
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Задания на ревью читаются из локальной таблицы, а решение по ним уходит общей очередью (AD-5,
 * AD-6). Прямого callable у репозитория больше нет: без сети он давал спиннер и отказ, а с сетью —
 * незащищённый повтор, который начислял репутацию дважды.
 */
class ReviewAssignmentRepositoryImpl(
    private val local: ReviewAssignmentLocalDataSource,
    private val outbox: OutboxStore,
) : ReviewAssignmentRepository {
    override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignment>> =
        local.observeAssignmentDetails(ownerUid).map { assignments ->
            assignments.map { it.toDomain() }
        }

    /**
     * Только запись очереди — локальная половина пуста (AD-25): строка задания не меняется, пока
     * журнал ревью не принесёт её исчезновение. Транзакции с таблицей фичи здесь нет, потому что
     * менять в ней нечего.
     */
    override suspend fun queueReviewAction(submission: ReviewActionSubmission): Result<Unit> =
        try {
            outbox.enqueue(SubmitReviewOutboxWriter.record(submission))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    /**
     * Пометка читается из очереди, а не из строки задания: ядро сравнивает непрозрачную для него
     * ссылку и ни одной таблицы фичи не читает (AD-7, AD-14).
     */
    override fun observeAssignmentSync(
        ownerUid: String,
        assignmentId: String,
    ): Flow<ReviewAssignmentSyncState> =
        outbox.observeEntity(ownerUid, SubmitReviewOutboxWriter.assignmentRef(assignmentId))
            .map { sync ->
                when (sync) {
                    OutboxEntitySync.None -> ReviewAssignmentSyncState.NONE
                    is OutboxEntitySync.Queued ->
                        when (sync.state) {
                            OutboxState.SYNCHRONOUS -> ReviewAssignmentSyncState.NONE
                            OutboxState.QUARANTINED -> ReviewAssignmentSyncState.QUARANTINED
                            OutboxState.WAITING,
                            OutboxState.WAITING_PRECONDITION,
                            OutboxState.CONFLICT,
                            -> ReviewAssignmentSyncState.PENDING
                        }
                }
            }
            .distinctUntilChanged()

    private fun ReviewAssignmentWithQuestions.toDomain(): ReviewAssignment =
        ReviewAssignment(
            id = assignment.id,
            submissionId = assignment.submissionId,
            ownerUid = assignment.ownerUid,
            catalogId = assignment.catalogId,
            draftId = assignment.draftId,
            questId = assignment.questId,
            lessonId = assignment.lessonId,
            title = assignment.title,
            createdAtMs = assignment.createdAtMs,
            taskKinds = assignment.taskKinds.mapNotNull { it.toKindOrNull() }.toSet(),
            sourceLanguages = assignment.sourceLanguages.toSet(),
            newTranslationLanguages = assignment.newTranslationLanguages.toSet(),
            reviewLanguages = assignment.reviewLanguages.toSet(),
            checks = assignment.toChecks(),
            questions =
                questions.sortedWith(compareBy<ReviewAssignmentQuestionEntity> { it.language }.thenBy { it.order })
                    .map { it.toDomain() },
        )

    private fun ReviewAssignmentEntity.toChecks(): ReviewChecks =
        ReviewChecks(
            isTested = isTested,
            testingScore = testingScore,
            isLogicReviewed = isLogicReviewed,
            logicScore = logicScore,
            isTranslationReviewed = isTranslationReviewed,
            translationScore = translationScore,
            translatedLanguages = translatedLanguages.toLanguageLevels(),
        )

    private fun ReviewAssignmentQuestionEntity.toDomain(): ReviewQuestion =
        ReviewQuestion(
            id = questionId,
            draftId = draftId,
            lessonId = lessonId,
            type = type,
            language = language,
            languageLevel = languageLevel,
            difficulty = difficulty,
            order = order,
            text = text,
            imagePath = imagePath,
            payload = payload,
            updatedAtMs = updatedAtMs,
        )

    private fun String.toKindOrNull(): ReviewAssignmentKind? =
        runCatching { ReviewAssignmentKind.valueOf(this) }.getOrNull()

    private fun List<String>.toLanguageLevels(): Map<String, Int> =
        mapNotNull { entry ->
            val separator = entry.lastIndexOf('=')
            if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
            val language = entry.substring(0, separator).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val level = entry.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null
            language to level
        }.toMap()
}
