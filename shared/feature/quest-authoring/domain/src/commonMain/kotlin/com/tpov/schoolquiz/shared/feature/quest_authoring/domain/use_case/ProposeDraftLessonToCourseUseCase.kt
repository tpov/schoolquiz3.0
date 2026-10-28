package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposalQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposalTarget
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitNewLessonProposalCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException

/**
 * «Предложить автору» урок из своего черновика с целевым путём в чужом курсе (CAP-27, 4.24).
 *
 * Урок едет телом предложения ровно тем, чем собран редактором: вопросы — авторской формы,
 * с ключом (его сверяет «Факты», а одобрение автора применяет). Вопрос без payload — отказ
 * до постановки в очередь: недописанный вопрос в чужой курс не уезжает.
 */
class ProposeDraftLessonToCourseUseCase(
    private val authoringRepository: QuestAuthoringRepository,
    private val submitProposal: SubmitNewLessonProposalUseCase,
) {
    suspend operator fun invoke(
        ownerUid: String,
        draftId: QuestDraftId,
        lessonId: DraftLessonId,
        target: NewLessonProposalTarget,
    ): Result<Unit> {
        return try {
            val bundle = authoringRepository.getDraft(draftId)
                ?: throw IllegalArgumentException("draft $draftId was not found")
            val lesson = bundle.lessons.firstOrNull { it.id == lessonId }
                ?: throw IllegalArgumentException("lesson $lessonId is outside draft $draftId")
            val questions = bundle.questions
                .filter { it.lessonId == lessonId }
                .sortedBy { it.order }
            require(questions.isNotEmpty()) { "lesson ${lesson.id.value} needs at least one question" }
            submitProposal(
                ownerUid,
                SubmitNewLessonProposalCommand(
                    target = target,
                    lessonId = lesson.id.value,
                    lessonTitle = lesson.title,
                    questions = questions.map { it.toProposalQuestion() },
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    /** Вопрос предложения — авторская форма черновика; словарь типов — серверный CONTENT_TYPE. */
    private fun DraftQuestion.toProposalQuestion(): NewLessonProposalQuestion =
        NewLessonProposalQuestion(
            id = id.value,
            type = WIRE_TYPE.getValue(type),
            language = language,
            languageLevel = languageLevel,
            difficulty = difficulty.name,
            order = order,
            text = text,
            imagePath = imagePath,
            payload = requireNotNull(payload) { "question ${id.value} has no payload" },
        )

    private companion object {
        /** Вид предложения на проводе — тот же словарь, что у приёмника 4.21 и redaction. */
        val WIRE_TYPE =
            mapOf(
                DraftQuestionType.SINGLE_CHOICE to "SingleChoice",
                DraftQuestionType.MULTIPLE_CHOICE to "MultipleChoice",
                DraftQuestionType.ORDERING to "Ordering",
                DraftQuestionType.FILL_BLANK to "FillBlank",
                DraftQuestionType.SURVEY to "Survey",
            )
    }
}
