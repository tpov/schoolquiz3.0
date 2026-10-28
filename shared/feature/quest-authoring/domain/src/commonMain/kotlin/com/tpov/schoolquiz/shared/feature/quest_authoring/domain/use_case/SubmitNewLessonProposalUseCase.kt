package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposalQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitNewLessonProposalCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import kotlinx.coroutines.CancellationException

/**
 * «Предложить автору» свой урок с целевым путём в чужом курсе (CAP-27, история 4.24).
 *
 * Форма повторяет парсер приёмника 4.21: название ≤200, вопросы 1..64, каждый — с типом,
 * языком, сложностью и непустым payload. Идентификатор намерения — в момент нажатия (AD-2).
 */
class SubmitNewLessonProposalUseCase(
    private val repository: LessonProposalRepository,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(
        ownerUid: String,
        command: SubmitNewLessonProposalCommand,
    ): Result<Unit> {
        return try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            val target = command.target
            require(target.catalogId.isNotBlank() && target.questId.isNotBlank() &&
                target.sectionId.isNotBlank() && target.themeId.isNotBlank()) {
                "proposal target path is required"
            }
            require(command.lessonId.isNotBlank()) { "lesson id is required" }
            val title = command.lessonTitle.trim()
            require(title.isNotEmpty() && title.length <= MAX_TITLE_CHARS) {
                "lesson title must be 1..$MAX_TITLE_CHARS chars"
            }
            require(command.questions.isNotEmpty() && command.questions.size <= MAX_QUESTIONS) {
                "lesson needs 1..$MAX_QUESTIONS questions"
            }
            val questions = command.questions.map { question ->
                NewLessonProposalQuestion(
                    id = question.id,
                    type = question.type.trim(),
                    language = question.language.trim(),
                    languageLevel = question.languageLevel,
                    difficulty = question.difficulty.trim(),
                    order = question.order,
                    text = question.text.trim(),
                    imagePath = question.imagePath,
                    payload = question.payload.trim(),
                )
            }
            require(questions.all { it.id.isNotBlank() && it.type.isNotEmpty() && it.language.isNotEmpty() &&
                    it.difficulty.isNotEmpty() && it.text.isNotEmpty() && it.payload.isNotEmpty() }) {
                "every question needs id, type, language, difficulty, text and payload"
            }
            repository.queueNewLessonProposal(
                NewLessonProposal(
                    id = idProvider.nextId("lesson-proposal"),
                    ownerUid = ownerUid,
                    requestedAtMs = timestampProvider.nowMs(),
                    command = SubmitNewLessonProposalCommand(target, command.lessonId, title, questions),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    companion object {
        /** Границы парсера приёмника 4.21 (lesson-proposals.js). */
        const val MAX_TITLE_CHARS = 200
        const val MAX_QUESTIONS = 64
    }
}
