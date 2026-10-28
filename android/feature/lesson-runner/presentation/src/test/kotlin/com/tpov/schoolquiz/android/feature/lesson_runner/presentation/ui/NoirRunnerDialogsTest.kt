package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NOIR-хвосты раннера (эпик 9.2): диалог выхода и опрос. Локале-независимо — все строки из
 * ресурсов; кнопки диалога рендерятся в верхнем регистре, поэтому сравнение без регистра.
 */
@RunWith(AndroidJUnit4::class)
class NoirRunnerDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var exitClicks = 0
    private var cancelClicks = 0

    private fun exitDialogContent() {
        composeRule.setContent {
            NoirTheme {
                ExitConfirmDialog(
                    onConfirm = { exitClicks++ },
                    onDismiss = { cancelClicks++ },
                )
            }
        }
    }

    @Test
    fun `exit confirm dialog exits exactly through its confirm action`() {
        exitDialogContent()

        composeRule
            .onNodeWithText(context.getString(R.string.runner_exit_confirm_title))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.runner_action_exit), ignoreCase = true)
            .performClick()

        assertEquals(1, exitClicks)
        assertEquals(0, cancelClicks)
    }

    @Test
    fun `exit confirm dialog cancellation dismisses without exiting`() {
        exitDialogContent()

        composeRule
            .onNodeWithText(context.getString(R.string.runner_action_cancel), ignoreCase = true)
            .performClick()

        assertEquals(1, cancelClicks)
        assertEquals(0, exitClicks)
    }

    @Test
    fun `single-choice survey toggles through the row and submits once selected`() {
        var toggled: String? = null
        var submits = 0
        composeRule.setContent {
            NoirTheme {
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
                                ),
                            selectedIds = setOf("a"),
                            allowMultiple = false,
                        ),
                    onOptionToggled = { toggled = it },
                    onSubmit = { submits++ },
                )
            }
        }

        composeRule.onNodeWithText("Не нравится").performClick()
        assertEquals("b", toggled)

        composeRule
            .onNodeWithText(context.getString(R.string.runner_action_answer))
            .performClick()
        assertEquals(1, submits)
    }

    @Test
    fun `multiple-choice survey keeps the checkbox family and toggles rows`() {
        // Чекбокс — только для множественного выбора (NOIR-SPEC §6.4): allowMultiple=true ведёт
        // чекбоксами, одиночный выбор — без них (строка сама цель касания).
        val toggles = mutableListOf<String>()
        composeRule.setContent {
            NoirTheme {
                SurveyContent(
                    state =
                        QuestionUiState.Survey(
                            questionText = "Выберите",
                            hasImage = false,
                            imageUrl = null,
                            options =
                                listOf(
                                    OptionUi(id = "a", text = "Первый"),
                                    OptionUi(id = "b", text = "Второй"),
                                    OptionUi(id = "c", text = "Третий"),
                                ),
                            selectedIds = emptySet(),
                            allowMultiple = true,
                        ),
                    onOptionToggled = { toggles.add(it) },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText("Первый").performClick()
        composeRule.onNodeWithText("Третий").performClick()

        assertEquals(listOf("a", "c"), toggles)
    }
}
