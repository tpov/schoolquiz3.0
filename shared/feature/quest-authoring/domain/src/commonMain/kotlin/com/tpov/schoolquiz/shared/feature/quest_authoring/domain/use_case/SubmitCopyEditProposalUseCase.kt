package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitCopyEditProposalCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import kotlinx.coroutines.CancellationException

/**
 * Подача «Предложить правку» (CAP-27, история 4.23): намерение уходит в общую очередь (AD-3/6),
 * идентификатор назначается в момент нажатия (AD-2).
 *
 * Границы повторяют парсер приёмника 4.21: в теле только изменённые вопросы, каждый —
 * авторской формы; `editedQuestionIds` — подмножество их идентификаторов. Отказ до постановки
 * в очередь — записи в outbox нет вовсе.
 */
class SubmitCopyEditProposalUseCase(
    private val repository: LessonProposalRepository,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(
        ownerUid: String,
        command: SubmitCopyEditProposalCommand,
    ): Result<Unit> {
        return try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            require(command.lessonId.isNotBlank()) { "lessonId must not be blank" }
            val title = command.lessonTitle.trim()
            require(title.isNotEmpty()) { "lessonTitle must not be blank" }
            require(title.length <= MAX_LESSON_TITLE_CHARS) {
                "lessonTitle must stay within the server bound ($MAX_LESSON_TITLE_CHARS)"
            }
            require(command.questions.isNotEmpty()) { "copy edit needs at least one edited question" }
            require(command.questions.size <= MAX_QUESTIONS_PER_PROPOSAL) {
                "copy edit exceeds the server question bound ($MAX_QUESTIONS_PER_PROPOSAL)"
            }
            val ids = command.questions.mapTo(HashSet()) { it.id }
            require(ids.size == command.questions.size) { "edited question ids must be unique" }
            require(command.editedQuestionIds.isNotEmpty()) { "copy edit must name the edited questions" }
            require(command.editedQuestionIds.all { it in ids }) {
                "editedQuestionIds must be a subset of the proposal questions"
            }
            for (question in command.questions) {
                require(question.id.isNotBlank()) { "question id must not be blank" }
                require(question.text.isNotBlank() && question.text.length <= MAX_QUESTION_TEXT_CHARS) {
                    "question ${question.id} text must be within the server bound ($MAX_QUESTION_TEXT_CHARS)"
                }
                require(question.payload.isNotBlank() &&
                    question.payload.encodeToByteArray().size <= MAX_QUESTION_PAYLOAD_BYTES) {
                    "question ${question.id} payload must be within the server bound ($MAX_QUESTION_PAYLOAD_BYTES bytes)"
                }
            }
            repository.queueCopyEditProposal(
                CopyEditProposal(
                    id = idProvider.nextId("lesson-proposal"),
                    ownerUid = ownerUid,
                    requestedAtMs = timestampProvider.nowMs(),
                    command = SubmitCopyEditProposalCommand(
                        lessonId = command.lessonId,
                        lessonTitle = title,
                        questions = command.questions,
                        editedQuestionIds = command.editedQuestionIds,
                    ),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    companion object {
        /** Границы парсера приёмника 4.21 (константы в lesson-proposals.js). */
        const val MAX_LESSON_TITLE_CHARS = 200
        const val MAX_QUESTIONS_PER_PROPOSAL = 64
        const val MAX_QUESTION_TEXT_CHARS = 4000
        const val MAX_QUESTION_PAYLOAD_BYTES = 32 * 1024
    }
}
