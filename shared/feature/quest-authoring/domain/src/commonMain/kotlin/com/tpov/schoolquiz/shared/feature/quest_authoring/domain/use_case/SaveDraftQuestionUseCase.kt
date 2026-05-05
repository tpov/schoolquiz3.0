package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command.SaveDraftQuestionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.toDraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException

class SaveDraftQuestionUseCase(
    private val repository: QuestAuthoringRepository,
    private val parser: QuestionContentParser,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(command: SaveDraftQuestionCommand): Result<DraftQuestionId> {
        return try {
            val content = parser.parse(command.payload).getOrThrow()
            val actualType = content.toDraftQuestionType()
            require(actualType == command.type) {
                "Question type mismatch: command=${command.type}, payload=$actualType"
            }
            require(content.difficulty == command.difficulty) {
                "Question difficulty mismatch: command=${command.difficulty}, payload=${content.difficulty}"
            }

            val questionId = command.questionId ?: DraftQuestionId(idProvider.nextId("draft-question"))
            val question = DraftQuestion(
                id = questionId,
                draftId = command.draftId,
                lessonId = command.lessonId,
                type = command.type,
                language = command.language,
                difficulty = command.difficulty,
                order = command.order,
                text = content.text,
                imagePath = command.imagePath,
                payload = command.payload,
                validationState = DraftQuestionValidationState.SAVED,
                updatedAtMs = timestampProvider.nowMs(),
                languageLevel = command.languageLevel,
            )
            repository.upsertQuestion(question).getOrThrow()
            Result.success(questionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
