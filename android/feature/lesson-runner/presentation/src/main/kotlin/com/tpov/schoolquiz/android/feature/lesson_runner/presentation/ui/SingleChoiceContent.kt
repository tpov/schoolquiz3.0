package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState

private const val FEEDBACK_STAGGER_MS = 90

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun SingleChoiceContent(
    state: QuestionUiState.SingleChoice,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    feedback: AnswerFeedback.SingleChoice? = null,
    hintEnabled: Boolean = false,
    onHint: () -> Unit = {},
) {
    val feedbackSelectedId = feedback?.selectedId
    val selectedIndex = state.options.indexOfFirst { it.id == feedbackSelectedId }.coerceAtLeast(0)
    QuestionFrame(
        questionText = state.questionText,
        imageUrl = state.imageUrl?.takeIf { state.hasImage },
        modifier = modifier,
        hintAction = {
            RunnerSecondaryAction(
                text = stringResource(R.string.runner_hint_action),
                enabled = hintEnabled,
                onClick = onHint,
            )
        },
    ) {
        state.options.forEachIndexed { index, option ->
            val isSelected = option.id == (feedbackSelectedId ?: state.selectedOptionId)
            // Green only for a right answer the user actually chose; a right answer nobody
            // chose is muted (F6), everything else wrong-or-neutral.
            val tone =
                when {
                    feedback == null -> AnswerFeedbackTone.Neutral
                    !feedback.revealCorrect -> AnswerFeedbackTone.Neutral
                    isSelected && option.id == feedback.correctId -> AnswerFeedbackTone.Correct
                    option.id == feedback.correctId -> AnswerFeedbackTone.Muted
                    isSelected -> AnswerFeedbackTone.Wrong
                    else -> AnswerFeedbackTone.Neutral
                }
            AnswerOptionSurface(
                text = option.text,
                selected = isSelected,
                enabled = feedback == null && state.selectedOptionId == null,
                leading = { AnswerKeyLetter(index = index, selected = isSelected) },
                feedbackTone = tone,
                feedbackDelayMillis = kotlin.math.abs(index - selectedIndex) * FEEDBACK_STAGGER_MS,
                onClick = { onOptionSelected(option.id) },
            )
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun SingleChoiceContentPreview() {
    SchoolQuizTheme {
        SingleChoiceContent(
            state =
                QuestionUiState.SingleChoice(
                    questionText = "Какой язык программирования создан в JetBrains?",
                    hasImage = false,
                    imageUrl = null,
                    options =
                        listOf(
                            OptionUi("1", "Kotlin"),
                            OptionUi("2", "Java"),
                            OptionUi("3", "Scala"),
                            OptionUi("4", "Groovy"),
                        ),
                    selectedOptionId = null,
                ),
            onOptionSelected = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun SingleChoiceContentTwoRowsPreview() {
    SchoolQuizTheme {
        SingleChoiceContent(
            state =
                QuestionUiState.SingleChoice(
                    questionText = "Выберите правильный ответ",
                    hasImage = false,
                    imageUrl = null,
                    options =
                        listOf(
                            OptionUi("1", "Ответ 1"),
                            OptionUi("2", "Ответ 2"),
                            OptionUi("3", "Ответ 3"),
                            OptionUi("4", "Ответ 4"),
                            OptionUi("5", "Ответ 5"),
                            OptionUi("6", "Ответ 6"),
                        ),
                    selectedOptionId = null,
                ),
            onOptionSelected = {},
        )
    }
}
