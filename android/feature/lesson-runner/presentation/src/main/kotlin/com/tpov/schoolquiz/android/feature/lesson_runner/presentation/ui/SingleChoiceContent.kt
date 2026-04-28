package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState

private const val FEEDBACK_STAGGER_MS = 90

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun SingleChoiceContent(
    state: QuestionUiState.SingleChoice,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    feedback: AnswerFeedback.SingleChoice? = null,
) {
    val feedbackSelectedId = feedback?.selectedId
    val selectedIndex = state.options.indexOfFirst { it.id == feedbackSelectedId }.coerceAtLeast(0)
    QuestionFrame(
        questionText = state.questionText,
        imageUrl = state.imageUrl?.takeIf { state.hasImage },
        modifier = modifier,
    ) {
        state.options.forEachIndexed { index, option ->
            val isSelected = option.id == (feedbackSelectedId ?: state.selectedOptionId)
            val tone =
                when {
                    feedback == null -> AnswerFeedbackTone.Neutral
                    option.id == feedback.correctId -> AnswerFeedbackTone.Correct
                    feedback.correctId == null && option.id == feedback.selectedId -> AnswerFeedbackTone.Wrong
                    feedback.correctId != null -> AnswerFeedbackTone.Wrong
                    else -> AnswerFeedbackTone.Neutral
                }
            AnswerOptionSurface(
                text = option.text,
                selected = isSelected,
                enabled = feedback == null && state.selectedOptionId == null,
                leading = {
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                    )
                },
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
