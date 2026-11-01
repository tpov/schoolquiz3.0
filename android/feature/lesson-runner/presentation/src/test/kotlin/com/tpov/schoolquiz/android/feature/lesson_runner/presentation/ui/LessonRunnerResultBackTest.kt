package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.CopyEditSheetState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.ErrorReportSubmissionState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.EditCopyMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Системный Back на экране результата (джорни 9.9): результат уже сохранён, поэтому Back ведёт
 * назад по иерархии компонента без подтверждения. На остальных состояниях обработчик выключен —
 * их выход остаётся прежним (крест с диалогом на вопросах).
 */
@RunWith(AndroidJUnit4::class)
class LessonRunnerResultBackTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var navigateBackCalls = 0
    private var componentBackCalls = 0

    private fun resultState() =
        RunnerUiState.Result(
            percentScore = PercentScore(88),
            mode = Difficulty.EASY,
            completedAt = 10L,
            hardUnlocked = false,
            bestStarsRawTenths = 28,
            currentAttemptStarsRawTenths = 28,
            lessonAverageRating = null,
            lessonRatingCount = 0,
            top3 = emptyList(),
            userAttemptCount = 1,
            userAveragePercentScore = 88,
            showRatingPrompt = false,
            saveWarning = false,
        )

    private fun resultContent() {
        val component = BackSpyComponent(resultState())
        composeRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(
                    component = component,
                    onNavigateBack = { navigateBackCalls++ },
                )
            }
        }
    }

    @Test
    fun `system back on the result screen navigates back without confirmation`() {
        resultContent()
        composeRule.waitForIdle()

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        assertEquals(1, componentBackCalls, "back must be routed through the component hierarchy")
        assertEquals(1, navigateBackCalls, "the host navigates back after the component event")
    }

    @Test
    fun `system back while not on the result screen stays with the previous handler`() {
        val component = BackSpyComponent(RunnerUiState.Loading)
        composeRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(
                    component = component,
                    onNavigateBack = { navigateBackCalls++ },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        assertEquals(0, componentBackCalls, "the runner state other than result must not intercept back")
        assertEquals(0, navigateBackCalls)
    }

    /** Минимальный компонент раннера: считает onBack и шлёт NavigateBack, как настоящий. */
    private inner class BackSpyComponent(
        initialState: RunnerUiState,
    ) : LessonRunnerRootComponent {
        private val eventFlow = MutableSharedFlow<RunnerEvent>(extraBufferCapacity = 8)

        override val needsTermsConsent = MutableStateFlow(false)
        override fun acceptTerms() = Unit
        override fun onDismissTermsConsent() = Unit
        override val isHardMode: Boolean = false
        override val uiState: StateFlow<RunnerUiState> = MutableStateFlow(initialState)
        override val events: Flow<RunnerEvent> = eventFlow
        override val comments: StateFlow<List<LessonComment>> = MutableStateFlow(emptyList())
        override fun onAnswer(answer: UserAnswerDraft) = Unit
        override fun onDraftChanged(draft: UserAnswerDraft) = Unit
        override fun onTimeout() = Unit
        override fun onContinue() = Unit
        override fun onExit() = Unit
        override fun onCrossButtonTap() = Unit
        override fun onCrossConfirmed() = Unit
        override fun onCrossCancelled() = Unit
        override fun onSubmitRating(rating: Int) = Unit
        override fun onSyncResult() = Unit
        override fun onFinish() = Unit
        override fun onRunAgain() = Unit
        override fun onNextLesson() = Unit
        override fun hintRequested(): Boolean = false
        override val proposalFeeQuote: StateFlow<LessonProposalFeeQuote?> = MutableStateFlow(null)
        override val errorReportState: StateFlow<ErrorReportSubmissionState> =
            MutableStateFlow(ErrorReportSubmissionState.Idle)
        override fun onReportDialogOpened() = Unit
        override fun onReportError(
            text: String,
            ownVariant: String?,
        ) = Unit
        override val copyEditSheetState: StateFlow<CopyEditSheetState> = MutableStateFlow(CopyEditSheetState())
        override fun onCopyEditDialogOpened() = Unit
        override fun onCopyEditMarkChanged(rowId: String, mark: EditCopyMark?) = Unit
        override fun onCopyEditSubmit() = Unit
        override fun onBack() {
            componentBackCalls++
            eventFlow.tryEmit(RunnerEvent.NavigateBack)
        }
        override fun onPostComment(text: String) = Unit
    }
}
