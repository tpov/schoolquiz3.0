package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState

/**
 * Survey question: options with no right answer.
 *
 * Everything stays [AnswerFeedbackTone.Neutral] — there is nothing to mark right or wrong, and
 * colouring an opinion as a mistake would be plainly wrong. The value of a survey is the
 * distribution of the answers, which is aggregated on the server.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun SurveyContent(
    state: QuestionUiState.Survey,
    onOptionToggled: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    feedback: AnswerFeedback.Survey? = null,
) {
    val selectedIds = feedback?.selectedIds ?: state.selectedIds
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
    ) {
        state.options.forEach { option ->
            val isSelected = option.id in selectedIds
            AnswerOptionSurface(
                text = option.text,
                selected = isSelected,
                enabled = feedback == null,
                leading = {
                    if (state.allowMultiple) {
                        Checkbox(checked = isSelected, onCheckedChange = null)
                    } else {
                        RadioButton(selected = isSelected, onClick = null)
                    }
                },
                feedbackTone = AnswerFeedbackTone.Neutral,
                onClick = { onOptionToggled(option.id) },
            )
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview
@Composable
private fun SurveyContentPreview() {
    SchoolQuizTheme {
        SurveyContent(
            state =
                QuestionUiState.Survey(
                    questionText = "Как вам новый экран?",
                    hasImage = false,
                    imageUrl = null,
                    options =
                        listOf(
                            OptionUi(id = "a", text = "Нравится"),
                            OptionUi(id = "b", text = "Не нравится"),
                            OptionUi(id = "c", text = "Затрудняюсь ответить"),
                        ),
                    selectedIds = setOf("a"),
                    allowMultiple = false,
                ),
            onOptionToggled = {},
            onSubmit = {},
        )
    }
}
