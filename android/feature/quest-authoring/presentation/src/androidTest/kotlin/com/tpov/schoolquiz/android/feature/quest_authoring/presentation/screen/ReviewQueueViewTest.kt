package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQuestionUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueKindUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ReviewQueueViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun list_card_click_emits_assignment_id() {
        var selectedAssignmentId: String? = null

        composeTestRule.setContent {
            SchoolQuizTheme {
                ReviewQueueView(
                    state =
                        ReviewQueueUiState(
                            isLoading = false,
                            assignments =
                                listOf(
                                    ReviewAssignmentListItemUiState(
                                        id = "assignment-1",
                                        title = "Lesson for review",
                                        kindLabels = listOf("Проверка перевода"),
                                        languageLabel = "проверка en",
                                        questionCount = 1,
                                        testingScore = null,
                                        logicScore = null,
                                        translationScore = null,
                                    ),
                                ),
                        ),
                    onFilterMenuClick = {},
                    onFilterMenuDismiss = {},
                    onFilterSelected = {},
                    onAssignmentSelected = { selectedAssignmentId = it },
                    onBackToListClick = {},
                    onScoreSelected = {},
                    onLanguageSelected = {},
                    onTranslationTextChanged = { _, _, _ -> },
                    onSegmentAcceptedChanged = { _, _, _ -> },
                    onSubmitClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("review-assignment-assignment-1").performClick()

        assertEquals("assignment-1", selectedAssignmentId)
    }

    @Test
    fun translation_review_detail_marks_missing_translation_on_screen() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                ReviewQueueView(
                    state =
                        ReviewQueueUiState(
                            isLoading = false,
                            selectedFilter = ReviewQueueFilter.TRANSLATION_REVIEW,
                            detail =
                                ReviewAssignmentDetailUiState(
                                    assignmentId = "assignment-1",
                                    lessonId = "lesson-1",
                                    title = "Lesson for review",
                                    kind = ReviewQueueKindUi.TRANSLATION_REVIEW,
                                    kindLabel = "Проверка перевода",
                                    selectedLanguage = "en",
                                    availableLanguages = listOf("en"),
                                    selectedScore = null,
                                    questions =
                                        listOf(
                                            ReviewQuestionUiState(
                                                id = "question-1",
                                                title = "Вопрос 1",
                                                language = "uk",
                                                text = "Source text",
                                                segments =
                                                    listOf(
                                                        ReviewSegmentUiState(
                                                            questionId = "question-1",
                                                            key = "text",
                                                            label = "Текст",
                                                            sourceText = "Source text",
                                                            translatedText = "",
                                                            accepted = false,
                                                        ),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    onFilterMenuClick = {},
                    onFilterMenuDismiss = {},
                    onFilterSelected = {},
                    onAssignmentSelected = {},
                    onBackToListClick = {},
                    onScoreSelected = {},
                    onLanguageSelected = {},
                    onTranslationTextChanged = { _, _, _ -> },
                    onSegmentAcceptedChanged = { _, _, _ -> },
                    onSubmitClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Проверка перевода").assertIsDisplayed()
        composeTestRule.onNodeWithText("Нет перевода").assertIsDisplayed()
    }
}
