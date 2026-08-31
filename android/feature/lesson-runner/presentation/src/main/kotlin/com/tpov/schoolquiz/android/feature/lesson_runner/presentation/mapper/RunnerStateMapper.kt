package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.SaveError
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import kotlin.random.Random
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Blank as ContentBlank
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError as DomainSaveError

private const val SINGLE_CHOICE_SHUFFLE_SALT = 17L
private const val MULTIPLE_CHOICE_SHUFFLE_SALT = 31L
private const val ORDERING_SHUFFLE_SALT = 47L
private const val FILL_BLANK_SHUFFLE_SALT = 61L

fun RunnerState.Ready.toQuestionUiState(lives: Int? = null): RunnerUiState.Question {
    val question = playOrder[indexInPool]
    val displaySeed =
        seed xor question.order.toLong() xor question.codeAnswerIndex.toLong() xor indexInPool.toLong()
    return RunnerUiState.Question(
        questionUiState =
            question.content.toQuestionUiState(
                draft = currentDraftAnswer,
                shuffleSeed = displaySeed,
            ),
        indexInPool = indexInPool,
        totalInPool = playOrder.size,
        deadlineMs = deadlineMs,
        isPaused = isPaused,
        isHard = mode == Difficulty.HARD,
        revealCorrect = sessionMode.revealsCorrectAnswer(mode),
        showExitConfirmDialog = false,
        currentDraft = currentDraftAnswer,
        lives = lives,
    )
}

fun QuestionContent.toQuestionUiState(
    draft: UserAnswerDraft? = null,
    shuffleSeed: Long? = null,
): QuestionUiState =
    when (this) {
        is QuestionContent.SingleChoice -> {
            val selectedId = (draft as? UserAnswerDraft.SingleChoiceDraft)?.selected?.raw
            QuestionUiState.SingleChoice(
                questionText = text,
                hasImage = imageUrl != null,
                info = info?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
                options =
                    options
                        .map { OptionUi(id = it.id.raw, text = it.text) }
                        .shuffledForDisplay(shuffleSeed, SINGLE_CHOICE_SHUFFLE_SALT),
                selectedOptionId = selectedId,
                correctOptionId = correctOptionId.raw,
            )
        }
        is QuestionContent.Survey -> {
            val selectedIds =
                (draft as? UserAnswerDraft.SurveyDraft)?.selected?.map { it.raw }?.toSet() ?: emptySet()
            QuestionUiState.Survey(
                questionText = text,
                hasImage = imageUrl != null,
                info = info?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
                options =
                    options
                        .map { OptionUi(id = it.id.raw, text = it.text) }
                        .shuffledForDisplay(shuffleSeed, SINGLE_CHOICE_SHUFFLE_SALT),
                selectedIds = selectedIds,
                allowMultiple = allowMultiple,
            )
        }
        is QuestionContent.MultipleChoice -> {
            val selectedIds =
                (draft as? UserAnswerDraft.MultipleChoiceDraft)
                    ?.selected?.map { it.raw }?.toSet() ?: emptySet()
            QuestionUiState.MultipleChoice(
                questionText = text,
                hasImage = imageUrl != null,
                info = info?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
                options =
                    options
                        .map { OptionUi(id = it.id.raw, text = it.text) }
                        .shuffledForDisplay(shuffleSeed, MULTIPLE_CHOICE_SHUFFLE_SALT),
                selectedIds = selectedIds,
                correctIds = correctOptionIds.map { it.raw }.toSet(),
            )
        }
        is QuestionContent.Ordering -> {
            val draftOrder = (draft as? UserAnswerDraft.OrderingDraft)?.order
            val itemById = items.associateBy { it.id }
            val shuffledItems =
                items
                    .map { OptionUi(it.id.raw, it.text) }
                    .shuffledForDisplay(shuffleSeed, ORDERING_SHUFFLE_SALT)
            val orderedItems =
                if (draftOrder != null && draftOrder.size == items.size) {
                    draftOrder.mapNotNull { id -> itemById[id]?.let { OptionUi(it.id.raw, it.text) } }
                        .takeIf { it.size == items.size } ?: shuffledItems
                } else {
                    shuffledItems
                }
            QuestionUiState.Ordering(
                questionText = text,
                hasImage = imageUrl != null,
                info = info?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
                items = orderedItems,
                correctOrderIds = items.map { it.id.raw },
            )
        }
        is QuestionContent.FillBlank -> {
            val blankIdToIndex = blanks.mapIndexed { idx, blank -> blank.id to idx }.toMap()
            val candidateById = candidates.associateBy { it.id }
            val filledValues =
                (draft as? UserAnswerDraft.FillBlankDraft)
                    ?.filled
                    ?.mapNotNull { (blankId, candidateId) ->
                        val idx = blankIdToIndex[blankId] ?: return@mapNotNull null
                        val text = candidateId?.let { candidateById[it]?.text } ?: return@mapNotNull null
                        idx to text
                    }
                    ?.toMap() ?: emptyMap()
            QuestionUiState.FillBlank(
                questionText = text,
                hasImage = imageUrl != null,
                info = info?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
                templateParts = parseTemplateParts(text, blanks),
                filledValues = filledValues,
                candidates =
                    candidates
                        .map { OptionUi(id = it.id.raw, text = it.text) }
                        .shuffledForDisplay(shuffleSeed, FILL_BLANK_SHUFFLE_SALT),
                correctCandidateIdsByBlankIndex =
                    blanks.mapIndexed { idx, blank -> idx to blank.correctCandidateId.raw }.toMap(),
            )
        }
    }

private fun List<OptionUi>.shuffledForDisplay(
    seed: Long?,
    salt: Long,
): List<OptionUi> =
    if (seed == null || size < 2) {
        this
    } else {
        shuffled(Random(seed xor salt))
    }

fun UserAnswerDraft.toUserAnswer(): UserAnswer =
    when (this) {
        is UserAnswerDraft.SingleChoiceDraft -> UserAnswer.SingleChoiceAnswer(selected)
        is UserAnswerDraft.MultipleChoiceDraft -> UserAnswer.MultipleChoiceAnswer(selected)
        is UserAnswerDraft.OrderingDraft -> UserAnswer.OrderingAnswer(order)
        is UserAnswerDraft.FillBlankDraft -> UserAnswer.FillBlankAnswer(filled)
        is UserAnswerDraft.SurveyDraft -> UserAnswer.SurveyAnswer(selected)
    }

fun InitFailureReason.toUiReason(): RunnerUiState.InitFailureReason =
    when (this) {
        InitFailureReason.AuthRequired -> RunnerUiState.InitFailureReason.AuthRequired
        InitFailureReason.LessonNotFound -> RunnerUiState.InitFailureReason.LessonNotFound
        InitFailureReason.EmptyPool -> RunnerUiState.InitFailureReason.EmptyPool
        InitFailureReason.NoValidQuestions -> RunnerUiState.InitFailureReason.NoValidQuestions
    }

fun DomainSaveError.toEventSaveError(): SaveError =
    when (this) {
        is DomainSaveError.IoFailure -> SaveError.IoError
        is DomainSaveError.UnknownError -> SaveError.Unknown
    }

private fun parseTemplateParts(
    text: String,
    blanks: List<ContentBlank>,
): List<TemplatePart> {
    val segments = text.split("___")
    val result = mutableListOf<TemplatePart>()
    segments.forEachIndexed { idx, segment ->
        if (segment.isNotEmpty()) result.add(TemplatePart.Text(segment))
        if (idx < segments.size - 1 && idx < blanks.size) {
            result.add(TemplatePart.Blank(index = idx, placeholder = "___", blankId = blanks[idx].id.raw))
        }
    }
    if (result.isEmpty()) result.add(TemplatePart.Text(text))
    return result
}
