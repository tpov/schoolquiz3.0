package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState

private const val FEEDBACK_STAGGER_MS = 90

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun MultipleChoiceContent(
    state: QuestionUiState.MultipleChoice,
    onOptionToggled: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    feedback: AnswerFeedback.MultipleChoice? = null,
) {
    val feedbackSelected = feedback?.selectedIds
    val selectedIds = feedbackSelected ?: state.selectedIds
    val firstSelectedIndex = state.options.indexOfFirst { it.id in selectedIds }.coerceAtLeast(0)
    QuestionFrame(
        questionText = state.questionText,
        imageUrl = state.imageUrl?.takeIf { state.hasImage },
        modifier = modifier,
        bottomAction = {
            RunnerPrimaryAction(
                text = "Ответить",
                enabled = feedback == null && state.selectedIds.isNotEmpty(),
                onClick = onSubmit,
            )
        },
    ) {
        state.options.forEachIndexed { index, option ->
            val isSelected = option.id in selectedIds
            val tone =
                when {
                    feedback == null -> AnswerFeedbackTone.Neutral
                    !feedback.revealCorrect -> AnswerFeedbackTone.Neutral
                    option.id in feedback.correctIds -> AnswerFeedbackTone.Correct
                    feedback.correctIds.isNotEmpty() -> AnswerFeedbackTone.Wrong
                    option.id in feedback.selectedIds -> AnswerFeedbackTone.Wrong
                    else -> AnswerFeedbackTone.Neutral
                }
            AnswerOptionSurface(
                text = option.text,
                selected = isSelected,
                enabled = feedback == null,
                leading = {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                    )
                },
                feedbackTone = tone,
                feedbackDelayMillis = kotlin.math.abs(index - firstSelectedIndex) * FEEDBACK_STAGGER_MS,
                onClick = { onOptionToggled(option.id) },
            )
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun MultipleChoiceContentPreview() {
    SchoolQuizTheme {
        MultipleChoiceContent(
            state =
                QuestionUiState.MultipleChoice(
                    questionText = "Выберите все правильные утверждения",
                    hasImage = false,
                    imageUrl = null,
                    options =
                        listOf(
                            OptionUi("1", "Kotlin — JVM язык"),
                            OptionUi("2", "Java — скриптовый язык"),
                            OptionUi("3", "Kotlin поддерживает null safety"),
                        ),
                    selectedIds = setOf("1"),
                ),
            onOptionToggled = {},
            onSubmit = {},
        )
    }
}
