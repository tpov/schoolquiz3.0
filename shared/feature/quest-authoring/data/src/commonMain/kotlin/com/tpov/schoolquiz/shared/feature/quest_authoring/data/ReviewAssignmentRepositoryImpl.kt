package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentWithQuestions
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewSegmentResultDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.SubmitReviewActionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewChecks
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReviewAssignmentRepositoryImpl(
    private val local: ReviewAssignmentLocalDataSource,
    private val remote: ReviewAssignmentRemoteDataSource,
) : ReviewAssignmentRepository {
    override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignment>> =
        local.observeAssignmentDetails(ownerUid).map { assignments ->
            assignments.map { it.toDomain() }
        }

    override suspend fun submitReviewAction(command: SubmitReviewActionCommand): Result<Unit> =
        try {
            remote.submitReviewAction(command.toDto())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

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
            questions = questions.sortedWith(compareBy<ReviewAssignmentQuestionEntity> { it.language }.thenBy { it.order })
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

    private fun SubmitReviewActionCommand.toDto(): SubmitReviewActionDto =
        SubmitReviewActionDto(
            assignmentId = assignmentId,
            lessonId = lessonId,
            kind = kind.name,
            score = score,
            language = language,
            targetReviewId = targetReviewId,
            translatedQuestions = translatedQuestions.map { it.toDto() },
            segmentResults = segmentResults.map { it.toDto() },
        )

    private fun ReviewQuestion.toDto(): ArenaQuestionDto =
        ArenaQuestionDto(
            id = id,
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

    private fun ReviewSegmentDecision.toDto(): ReviewSegmentResultDto =
        ReviewSegmentResultDto(
            questionId = questionId,
            segmentKey = segmentKey,
            accepted = accepted,
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
