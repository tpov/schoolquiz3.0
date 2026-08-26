package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewLanguagesUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQuestionUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueKindUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentLabelKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ReviewQueueViewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

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
                                        kinds = listOf(ReviewQueueKindUi.TRANSLATION_REVIEW),
                                        languages = ReviewLanguagesUi(reviewTargets = listOf("en")),
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
                                    selectedLanguage = "en",
                                    availableLanguages = listOf("en"),
                                    selectedScore = null,
                                    questions =
                                        listOf(
                                            ReviewQuestionUiState(
                                                id = "question-1",
                                                order = 0,
                                                language = "uk",
                                                text = "Source text",
                                                segments =
                                                    listOf(
                                                        ReviewSegmentUiState(
                                                            questionId = "question-1",
                                                            key = "text",
                                                            labelKind = ReviewSegmentLabelKind.TEXT,
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

        composeTestRule
            .onNodeWithText(context.getString(R.string.qa_filter_translation_review))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.qa_review_no_translation))
            .assertIsDisplayed()
    }
}
