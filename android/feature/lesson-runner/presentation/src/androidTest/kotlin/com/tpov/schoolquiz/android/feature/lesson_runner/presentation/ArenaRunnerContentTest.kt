@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunAction
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunRatingState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunToken
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunnerStatus
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunnerView
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.RunFakeComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.AnswerFeedback
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaFrozenRatingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptReward
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArenaRunnerContentTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun feedback_continue_is_guarded_for_250ms() {
        val answer = UserAnswerDraft.SingleChoiceDraft(OptionId("a"))
        val clock = composeTestRule.mainClock
        val previousAutoAdvance = clock.autoAdvance
        clock.autoAdvance = false
        try {
            val fake = render(questionFrame(ArenaRunnerPhase.ANSWERING))
            fake.setFrame(questionFrame(ArenaRunnerPhase.FEEDBACK, feedback = feedback(answer)))
            advanceFeedbackFrame()
            val feedbackStartedAt = clock.currentTime
            continueNode().assertIsDisplayed().assertIsNotEnabled()

            clock.advanceTimeBy(249L, ignoreFrameDuration = true)
            composeTestRule.waitForIdle()
            continueNode().assertIsNotEnabled()
            assertEquals(feedbackStartedAt + 249L, clock.currentTime)

            clock.advanceTimeBy(1L, ignoreFrameDuration = true)
            advanceFeedbackFrame()
            continueNode().assertIsEnabled()
            continueNode().performClick()
            assertEquals(1, fake.actions.size)
            assertEquals(ArenaRunAction.Continue, fake.actions.single().second)
        } finally {
            clock.autoAdvance = previousAutoAdvance
        }
    }

    @Test
    fun feedback_info_opens_without_submitting_an_answer() {
        val answer = UserAnswerDraft.SingleChoiceDraft(OptionId("a"))
        val fake = render(questionFrame(ArenaRunnerPhase.FEEDBACK, feedback = feedback(answer)))

        composeTestRule.onNodeWithContentDescription(string(R.string.runner_cd_info))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Сохранённое объяснение").assertIsDisplayed()
        assertEquals(0, fake.actions.size)
    }

    @Test
    fun paused_frame_does_not_expose_question_text() {
        render(questionFrame(ArenaRunnerPhase.PAUSED))

        composeTestRule.onNodeWithText("Секретный вопрос PAUSED").assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.runner_resume_title)).assertIsDisplayed()
    }

    @Test
    fun busy_answering_frame_hides_previously_interactive_input() {
        val fake = render(questionFrame(ArenaRunnerPhase.ANSWERING))
        val interactiveClick =
            composeTestRule
                .onNodeWithText("Первый")
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .action
        composeTestRule.runOnIdle {
            assertTrue(interactiveClick?.invoke() == true)
            assertEquals(1, fake.actions.size)
            fake.actions.clear()
        }

        val busyFrame = questionFrame(ArenaRunnerPhase.ANSWERING, busy = true)
        composeTestRule.runOnIdle { fake.setFrame(busyFrame) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Первый").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(fake.actions.isEmpty()) }
    }

    @Test
    fun answer_callback_uses_the_rendered_frame_token() {
        val frame = questionFrame(ArenaRunnerPhase.ANSWERING)
        val fake = render(frame)

        composeTestRule.onNodeWithText("Первый").performClick()

        val (token, action) = fake.actions.single()
        assertEquals(frame.token, token)
        assertEquals(
            ArenaRunAction.Answer(UserAnswerDraft.SingleChoiceDraft(OptionId("a"))),
            action,
        )
    }

    @Test
    fun hard_arena_question_sets_flag_secure_on_the_activity_window() {
        render(questionFrame(ArenaRunnerPhase.ANSWERING, isHard = true))

        composeTestRule.waitForIdle()
        assertTrue(
            composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )
    }

    @Test
    fun pending_and_invalid_receipts_do_not_expose_score_or_reward() {
        val fake = render(resultFrame(ServerAttemptSettlement(ServerAttemptSettlementState.PENDING)))
        assertNoVerifiedAccountingText()

        val invalid =
            resultFrame(
                ServerAttemptSettlement(
                    state = ServerAttemptSettlementState.INVALID_RECEIPT,
                    percentScore = PercentScore(0),
                    reward = ServerAttemptReward(7L, 2L),
                ),
            )
        composeTestRule.runOnIdle { fake.setFrame(invalid) }
        composeTestRule.waitForIdle()
        assertNoVerifiedAccountingText()
    }

    @Test
    fun confirmed_receipt_exposes_verified_score_and_reward() {
        val fake = render(resultFrame(confirmedSettlement()))

        composeTestRule.onNodeWithText(string(R.string.arena_run_result_percent, 0)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.arena_run_result_code, "1")).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.arena_run_result_reward_skill, 7L)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.arena_run_result_reward_nolics, 2L)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.arena_run_result_life_cost, 50L)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.arena_run_result_plasma_cost, 100L)).assertIsDisplayed()
        assertTrue(fake.actions.isEmpty())
    }

    @Test
    fun confirmed_pending_receipt_submits_the_rendered_rating() {
        val frame = resultFrame(confirmedSettlement(), ArenaRunRatingState(ArenaFrozenRatingState.Pending))
        val fake = render(frame)

        composeTestRule.onNodeWithText(string(R.string.arena_run_rating_option_2)).performClick()

        assertEquals(listOf(frame to 2), fake.ratingSubmissions)
    }

    @Test
    fun queued_rating_exposes_only_explicit_rating_sync() {
        val frame = resultFrame(confirmedSettlement(), ArenaRunRatingState(ArenaFrozenRatingState.Queued(3)))
        val fake = render(frame)

        composeTestRule.onNodeWithText(string(R.string.arena_run_rating_queued, 3)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.arena_run_rating_sync)).performClick()

        assertEquals(listOf(frame), fake.ratingSyncs)
    }

    @Test
    fun rating_submission_disables_rating_and_back_controls() {
        render(
            resultFrame(confirmedSettlement(), ArenaRunRatingState(ArenaFrozenRatingState.Pending, submitting = true)),
        )

        composeTestRule.onNodeWithText(string(R.string.arena_run_rating_option_1)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(string(R.string.arena_run_back_to_tickets)).assertIsNotEnabled()
    }

    @Test
    fun hard_arena_question_to_attention_clears_flag_secure() {
        val fake = render(questionFrame(ArenaRunnerPhase.ANSWERING, isHard = true))
        composeTestRule.waitForIdle()
        assertTrue(
            composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )

        composeTestRule.runOnIdle {
            fake.setFrame(ArenaRunnerView.Attention(ArenaRunnerStatus.PENDING_COMPLETION))
        }
        composeTestRule.waitForIdle()
        assertEquals(
            0,
            composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    private fun advanceFeedbackFrame() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    private fun continueNode(): SemanticsNodeInteraction =
        composeTestRule.onNodeWithContentDescription(string(R.string.runner_feedback_continue_action))

    private fun assertNoVerifiedAccountingText() {
        composeTestRule.onNodeWithText(string(R.string.arena_run_result_percent, 0)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.arena_run_result_reward_skill, 7L)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.arena_run_result_reward_nolics, 2L)).assertDoesNotExist()
    }

    private fun render(frame: ArenaRunnerView): RecordingArenaComponent {
        val fake = RecordingArenaComponent(frame)
        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fake, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()
        return fake
    }

    private fun questionFrame(
        phase: ArenaRunnerPhase,
        feedback: AnswerFeedback? = null,
        busy: Boolean = false,
        foreground: Boolean = true,
        isHard: Boolean = false,
    ): ArenaRunnerView.Question {
        val questionText =
            if (phase == ArenaRunnerPhase.PAUSED) {
                "Секретный вопрос PAUSED"
            } else {
                "Секретный вопрос"
            }
        val stamp = ArenaSessionStamp(ownerUid = "arena-test-user", generation = 7L)
        return ArenaRunnerView.Question(
            token =
                ArenaRunToken(
                    stamp = stamp,
                    ticketId = "ticket-1",
                    attemptId = "attempt-1",
                    revision = 42L,
                    index = 0,
                    phase = phase,
                ),
            question =
                RunnerUiState.Question(
                    questionUiState =
                        QuestionUiState.SingleChoice(
                            questionText = questionText,
                            hasImage = false,
                            imageUrl = null,
                            options = listOf(OptionUi("a", "Первый"), OptionUi("b", "Второй")),
                            selectedOptionId = null,
                            info = "Сохранённое объяснение",
                        ),
                    indexInPool = 0,
                    totalInPool = 3,
                    deadlineMs = System.currentTimeMillis() + 30_000L,
                    isPaused = phase == ArenaRunnerPhase.PAUSED,
                    isHard = isHard,
                    revealCorrect = !isHard,
                    showExitConfirmDialog = false,
                ),
            feedback = feedback,
            busy = busy,
            foreground = foreground,
        )
    }

    private fun feedback(answer: UserAnswerDraft.SingleChoiceDraft) =
        AnswerFeedback.SingleChoice(
            answer = answer,
            selectedId = "a",
            correctId = "a",
            revealCorrect = true,
        )

    private fun resultFrame(
        settlement: ServerAttemptSettlement,
        rating: ArenaRunRatingState = ArenaRunRatingState(),
    ) = ArenaRunnerView.Result(
        stamp = ArenaSessionStamp("arena-test-user", 7L),
        header =
            ArenaTicketHeader(
                schemaVersion = 1,
                ticketId = "ticket-1",
                attemptId = "attempt-1",
                ownerUid = "arena-test-user",
                requestId = "request-1",
                mode = ArenaEntryMode.SELECTED,
                difficulty = Difficulty.HARD,
                lessonId = "lesson-1",
                lessonVersion = 1L,
                status = ArenaTicketStatus.SETTLED,
                priceNolics = 100,
                lifeCost = 50L,
                issuedAtMs = 0L,
                dayId = "2026-09-08",
            ),
        attempt =
            AttemptIdentity(
                AttemptId("attempt-1"),
                "arena-test-user",
                LessonId("lesson-1"),
                1L,
                Difficulty.HARD,
            ),
        settlement = settlement,
        canSync = false,
        rating = rating,
    )

    private fun confirmedSettlement() =
        ServerAttemptSettlement(
            state = ServerAttemptSettlementState.SETTLED,
            receiptId = "receipt-1",
            codeAnswer = CodeAnswer("1"),
            percentScore = PercentScore(0),
            completed = true,
            settledAtMs = 20L,
            lifePointsSpent = 50L,
            plasmaPointsSpent = 100L,
            reward = ServerAttemptReward(7L, 2L),
            ownerVerified = true,
        )

    private fun string(
        resId: Int,
        vararg args: Any,
    ): String = composeTestRule.activity.getString(resId, *args)

    private class RecordingArenaComponent(
        frame: ArenaRunnerView,
    ) : ArenaRunnerRootComponent,
        LessonRunnerRootComponent by RunFakeComponent(
            MutableStateFlow<RunnerUiState>(
                (frame as? ArenaRunnerView.Question)?.question ?: RunnerUiState.Loading,
            ),
        ) {
        private val view = MutableStateFlow<ArenaRunnerView>(frame)
        val actions = mutableListOf<Pair<ArenaRunToken, ArenaRunAction>>()
        val ratingSubmissions = mutableListOf<Pair<ArenaRunnerView.Result, Int>>()
        val ratingSyncs = mutableListOf<ArenaRunnerView.Result>()

        override val arenaView = view

        fun setFrame(frame: ArenaRunnerView) {
            view.value = frame
        }

        override fun onArenaAction(
            token: ArenaRunToken,
            action: ArenaRunAction,
        ) {
            actions += token to action
        }

        override fun onArenaReload() = Unit

        override fun onArenaForeground() = Unit

        override fun onArenaBackground(changingConfigurations: Boolean) = Unit

        override fun onArenaRefreshWallet() = Unit

        override fun onArenaSubmitRating(
            frame: ArenaRunnerView.Result,
            rating: Int,
        ) {
            ratingSubmissions += frame to rating
        }

        override fun onArenaRequestRatingSync(frame: ArenaRunnerView.Result) {
            ratingSyncs += frame
        }
    }
}
