package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.material3.Checkbox
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
fun MultipleChoiceContent(
    state: QuestionUiState.MultipleChoice,
    onOptionToggled: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    feedback: AnswerFeedback.MultipleChoice? = null,
    hintEnabled: Boolean = false,
    onHint: () -> Unit = {},
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
                text = stringResource(R.string.runner_action_answer),
                enabled = feedback == null && state.selectedIds.isNotEmpty(),
                onClick = onSubmit,
            )
        },
        hintAction = {
            RunnerSecondaryAction(
                text = stringResource(R.string.runner_hint_action),
                enabled = hintEnabled,
                onClick = onHint,
            )
        },
    ) {
        state.options.forEachIndexed { index, option ->
            val isSelected = option.id in selectedIds
            // Green only for picked-and-correct; a correct option nobody picked dims (F6).
            val tone =
                when {
                    feedback == null -> AnswerFeedbackTone.Neutral
                    !feedback.revealCorrect -> AnswerFeedbackTone.Neutral
                    isSelected && option.id in feedback.correctIds -> AnswerFeedbackTone.Correct
                    option.id in feedback.correctIds -> AnswerFeedbackTone.Muted
                    isSelected -> AnswerFeedbackTone.Wrong
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
