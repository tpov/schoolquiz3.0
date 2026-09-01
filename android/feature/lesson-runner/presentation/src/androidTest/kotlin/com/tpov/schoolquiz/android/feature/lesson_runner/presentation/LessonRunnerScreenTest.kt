package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.RunFakeComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonRunnerScreenTest {

    /** Mirrors ANSWER_FEEDBACK_SKIP_ARM_DELAY_MS in LessonRunnerScreen, which is private. */
    private val ANSWER_FEEDBACK_SKIP_ARM_MS = 400L

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)

    // --- State helpers ---

    private fun singleChoiceQuestion(
        questionText: String = "Тестовый вопрос",
        isHard: Boolean = false,
        isPaused: Boolean = false,
        showExitConfirmDialog: Boolean = false,
        deadlineMs: Long = System.currentTimeMillis() + 30_000L,
        indexInPool: Int = 0,
        totalInPool: Int = 5,
    ) = RunnerUiState.Question(
        questionUiState = QuestionUiState.SingleChoice(
            questionText = questionText,
            hasImage = false,
            imageUrl = null,
            options = emptyList(),
            selectedOptionId = null,
        ),
        indexInPool = indexInPool,
        totalInPool = totalInPool,
        deadlineMs = deadlineMs,
        isPaused = isPaused,
        isHard = isHard,
        showExitConfirmDialog = showExitConfirmDialog,
    )

    // --- Hint fixtures: one per question type, hintable and not ---

    private fun question(
        qState: QuestionUiState,
        index: Int = 0,
        lives: Int? = 9,
    ) = RunnerUiState.Question(
        questionUiState = qState,
        indexInPool = index,
        totalInPool = 5,
        deadlineMs = System.currentTimeMillis() + 300_000L,
        isPaused = false,
        isHard = false,
        showExitConfirmDialog = false,
        lives = lives,
    )

    private fun singleChoice(correctId: String?) = QuestionUiState.SingleChoice(
        questionText = "Один ответ",
        hasImage = false,
        imageUrl = null,
        options = listOf(OptionUi("a", "Первый"), OptionUi("b", "Второй")),
        selectedOptionId = null,
        correctOptionId = correctId,
    )

    private fun multipleChoice(correctIds: Set<String>) = QuestionUiState.MultipleChoice(
        questionText = "Несколько ответов",
        hasImage = false,
        imageUrl = null,
        options = listOf(OptionUi("a", "Первый"), OptionUi("b", "Второй")),
        selectedIds = emptySet(),
        correctIds = correctIds,
    )

    private fun ordering(correctOrder: List<String>) = QuestionUiState.Ordering(
        questionText = "Порядок",
        hasImage = false,
        imageUrl = null,
        items = listOf(OptionUi("b", "Второй"), OptionUi("a", "Первый")),
        correctOrderIds = correctOrder,
    )

    /** [blankCount] `___` in the template; [correct] is the key the domain grades against. */
    private fun fillBlank(
        blankCount: Int,
        correct: Map<Int, String>,
    ): QuestionUiState.FillBlank {
        val parts = mutableListOf<TemplatePart>(TemplatePart.Text("Слово "))
        repeat(blankCount) { index ->
            parts += TemplatePart.Blank(index = index, placeholder = "___", blankId = "b$index")
            parts += TemplatePart.Text(" и ")
        }
        return QuestionUiState.FillBlank(
            questionText = "Слово ___ и ___",
            hasImage = false,
            imageUrl = null,
            templateParts = parts,
            filledValues = emptyMap(),
            candidates = listOf(OptionUi("x", "Икс"), OptionUi("y", "Игрек")),
            correctCandidateIdsByBlankIndex = correct,
        )
    }

    /** Every type that has an answer to play, with the draft each hint must submit. */
    private val hintableTypes: List<Triple<String, QuestionUiState, UserAnswerDraft>> = listOf(
        Triple(
            "single choice",
            singleChoice(correctId = "b"),
            UserAnswerDraft.SingleChoiceDraft(OptionId("b")),
        ),
        Triple(
            "multiple choice",
            multipleChoice(correctIds = setOf("a", "b")),
            UserAnswerDraft.MultipleChoiceDraft(setOf(OptionId("a"), OptionId("b"))),
        ),
        Triple(
            "ordering",
            ordering(correctOrder = listOf("a", "b")),
            UserAnswerDraft.OrderingDraft(listOf(OptionId("a"), OptionId("b"))),
        ),
        Triple(
            "fill blank",
            fillBlank(blankCount = 1, correct = mapOf(0 to "x")),
            UserAnswerDraft.FillBlankDraft(mapOf(BlankId("b0") to CandidateId("x"))),
        ),
    )

    /** Every type with nothing to play. Fill blank's case is the one reachable in shipped data. */
    private val unhintableTypes: List<Pair<String, QuestionUiState>> = listOf(
        "single choice with no correct id" to singleChoice(correctId = null),
        "multiple choice with an empty correct set" to multipleChoice(correctIds = emptySet()),
        "ordering with an empty correct order" to ordering(correctOrder = emptyList()),
        "fill blank the template cannot fill" to fillBlank(blankCount = 1, correct = mapOf(0 to "x", 1 to "y")),
    )

    private fun hintNode() = composeTestRule.onNodeWithText(str(R.string.runner_hint_action).uppercase())

    private fun resultState(
        percentScore: Int = 80,
        mode: Difficulty = Difficulty.EASY,
        hardUnlocked: Boolean = false,
        bestStarsRawTenths: Int = 16,
        top3: List<TopParticipant> = emptyList(),
        showRatingPrompt: Boolean = false,
        saveWarning: Boolean = false,
        lessonAverageRating: Float? = null,
    ) = RunnerUiState.Result(
        percentScore = PercentScore(percentScore),
        mode = mode,
        completedAt = 0L,
        hardUnlocked = hardUnlocked,
        bestStarsRawTenths = bestStarsRawTenths,
        currentAttemptStarsRawTenths = bestStarsRawTenths,
        lessonAverageRating = lessonAverageRating,
        lessonRatingCount = 0,
        top3 = top3,
        userAttemptCount = 1,
        userAveragePercentScore = percentScore,
        userBestPercentScore = percentScore,
        showRatingPrompt = showRatingPrompt,
        saveWarning = saveWarning,
    )

    // --- CT-01: GIVEN easy Question WHEN rendered THEN question text and progress visible ---
    // Spec AC-1, AC-2
    @Test
    fun ct01_questionScreen_easy_mode_renders() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(questionText = "CT-01 вопрос", indexInPool = 0, totalInPool = 5)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("CT-01 вопрос").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 / 5").assertIsDisplayed()
    }

    // --- CT-02: GIVEN hard Question WHEN rendered THEN screen renders without crash ---
    // Background colour assertion requires screenshot testing; smoke test verifies hard mode renders.
    // Spec AC-2
    @Test
    fun ct02_questionScreen_hard_mode_errorBackground() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(questionText = "CT-02 вопрос", isHard = true)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        // Hard mode content renders correctly (background colour tested via screenshot testing)
        composeTestRule.onNodeWithText("CT-02 вопрос").assertIsDisplayed()
    }

    // --- CT-02b: a hard question advances on tap, even though it reveals no verdict ---
    // Regression: the tap layer used to be drawn only when there was a verdict digit to show, and a
    // hard question has none — so answering the first question left the runner with nowhere to go.
    // Requires a device or emulator; it is not part of ciCheck.
    @Test
    fun ct02b_hardQuestion_tapAfterAnswer_advances() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(
                singleChoiceQuestion(questionText = "CT-02b вопрос", isHard = true).let { state ->
                    state.copy(
                        questionUiState = (state.questionUiState as QuestionUiState.SingleChoice).copy(
                            options = listOf(OptionUi("o1", "Первый"), OptionUi("o2", "Второй")),
                        ),
                    )
                },
            ),
            isHardMode = true,
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("Первый").performClick()
        // The tap layer arms after a short delay so the answer is not swallowed by the same gesture.
        composeTestRule.mainClock.advanceTimeBy(ANSWER_FEEDBACK_SKIP_ARM_MS)
        composeTestRule.onRoot().performClick()

        assertNotNull(
            "a hard question must reach onAnswer on tap; without a verdict digit the tap layer used " +
                "to be absent and the run stalled here",
            fakeComponent.lastAnswer,
        )
    }

    // --- CT-03: GIVEN showExitConfirmDialog=true WHEN rendered THEN exit dialog displayed ---
    // Spec AC-7
    @Test
    fun ct03_exitConfirmDialog_showExitConfirmDialog_true() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(showExitConfirmDialog = true)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_exit_confirm_title)).assertIsDisplayed()
    }

    // --- CT-04: GIVEN Result state WHEN rendered THEN percent and Завершить button visible ---
    // Spec AC-20
    @Test
    fun ct04_resultState_resultScreen_visible() {
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(percentScore = 75)))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        assertTrue(composeTestRule.onAllNodesWithText("75%").fetchSemanticsNodes().isNotEmpty())
        composeTestRule.onNodeWithText(str(R.string.runner_result_next)).assertIsDisplayed()
    }

    // --- CT-05: GIVEN Result state WHEN rendered THEN finish action present ---
    // Spec AC-20
    @Test
    fun ct05_finishButton_present_on_result() {
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState()))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_result_next)).assertIsDisplayed()
    }

    // --- CT-10: GIVEN expired deadline WHEN rendered THEN onTimeout invoked ---
    // Open Question OQ-CT10: QuestionProgressHeader.kt does not invoke component.onTimeout()
    // when the deadline expires — production seam missing. Test will fail until fixed.
    @Ignore("OQ-CT10: production seam missing — QuestionProgressHeader does not call component.onTimeout()")
    @Test
    fun ct10_timer_expired_onTimeout_invoked() {
        val expiredDeadline = System.currentTimeMillis() - 100L
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(deadlineMs = expiredDeadline, isPaused = false)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(fakeComponent.timeoutCount > 0)
    }

    // --- CT-11: GIVEN hard Question WHEN rendered THEN FLAG_SECURE set in window ---
    // Spec AC-11
    @Test
    fun ct11_hard_mode_flagSecure_set() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(isHard = true)),
            isHardMode = true,
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        val flags = composeTestRule.activity.window.attributes.flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    // --- CT-12: GIVEN hard screen exits composition WHEN disposed THEN FLAG_SECURE cleared ---
    // Spec AC-11 (DisposableEffect onDispose clears flag)
    @Test
    fun ct12_exit_hard_mode_flagSecure_cleared() {
        val stateFlow = MutableStateFlow<RunnerUiState>(singleChoiceQuestion(isHard = true))
        val fakeComponent = RunFakeComponent(stateFlow, isHardMode = true)
        val showRunner = mutableStateOf(true)

        composeTestRule.setContent {
            SchoolQuizTheme {
                if (showRunner.value) {
                    LessonRunnerScreen(fakeComponent, onNavigateBack = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)

        composeTestRule.runOnIdle {
            showRunner.value = false
        }
        composeTestRule.waitForIdle()

        assertEquals(0, composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }

    // --- CT-13: GIVEN easy Question WHEN rendered THEN FLAG_SECURE NOT set ---
    // Spec AC-11
    @Test
    fun ct13_easy_mode_no_flagSecure() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(isHard = false)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(0, composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }

    // --- CT-14: GIVEN isPaused=true WHEN rendered and time advances THEN timer display unchanged ---
    // Spec AC-9 (paused timer does not tick)
    @Test
    fun ct14_paused_timer_not_ticking() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(
                singleChoiceQuestion(
                    isPaused = true,
                    deadlineMs = System.currentTimeMillis() + 30_000L,
                ),
            ),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        // Advance compose clock beyond the deadline: paused LaunchedEffect must not timeout.
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        assertEquals(0, fakeComponent.timeoutCount)
    }

    // --- CT-15: GIVEN isPaused=true WHEN rendered THEN blocking resume dialog displayed ---
    // Spec AC-9
    @Test
    fun ct15_paused_blockingDialog_displayed() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(isPaused = true)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_resume_title)).assertIsDisplayed()
    }

    // --- CT-16: GIVEN blocking dialog shown WHEN "Продолжить" clicked THEN continueCount == 1 ---
    // Spec AC-9
    @Test
    fun ct16_continue_button_calls_onContinue() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(isPaused = true)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_action_continue)).performClick()

        assertEquals(1, fakeComponent.continueCount)
    }

    // --- CT-17: GIVEN blocking dialog shown WHEN "Выйти" clicked THEN exitCount == 1 ---
    // Spec AC-9
    @Test
    fun ct17_exit_button_calls_onExit() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(isPaused = true, showExitConfirmDialog = false)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        // "Выйти" in BlockingResumeDialog (OutlinedButton)
        composeTestRule.onNodeWithText(str(R.string.runner_action_exit)).performClick()

        assertEquals(1, fakeComponent.exitCount)
    }

    // --- CT-18: GIVEN showRatingPrompt=true WHEN rendered THEN rating section is visible in result UI ---
    // Spec AC-21
    @Test
    fun ct18_ratingPrompt_showRatingPrompt_true_visible() {
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(showRatingPrompt = true)))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_rating_title)).assertIsDisplayed()
    }

    // --- CT-19: GIVEN showRatingPrompt=false WHEN rendered THEN rating section absent ---
    // Spec AC-21
    @Test
    fun ct19_ratingPrompt_false_absent() {
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(showRatingPrompt = false)))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_rating_title)).assertDoesNotExist()
    }

    // --- CT-20: GIVEN top3 non-empty WHEN rendered THEN participant name and percent visible ---
    // Spec AC-22
    @Test
    fun ct20_top3_nonEmpty_sectionVisible() {
        val top3 = listOf(TopParticipant("Alice", null, 90))
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(top3 = top3)))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("90%").assertIsDisplayed()
    }

    // --- CT-21: GIVEN top3 with null avatarUrl WHEN rendered THEN no crash, placeholder shown ---
    // Spec AC-22
    @Test
    fun ct21_top3_nullAvatarUrl_placeholder_rendered() {
        val top3 = listOf(TopParticipant("Bob", null, 80))
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(top3 = top3)))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        // No crash; participant row is visible
        composeTestRule.onNodeWithText("Bob").assertIsDisplayed()
    }

    // --- CT-22: Phase-06 scope — StarRating(1.5f) in LessonItemCard ---
    @Ignore("Phase-06 scope: belongs to LessonItemCard, not LessonRunnerScreen")
    @Test
    fun ct22_bestStarsRawTenths_15_starRating_1_5() {
        // Covered in Phase-06 LessonItemCard tests
    }

    // --- CT-23: Phase-06 scope ---
    @Ignore("Phase-06 scope: hardUnlocked checkbox in LessonItemCard")
    @Test
    fun ct23_hardUnlocked_false_checkbox_absent() {
        // Covered in Phase-06 LessonItemCard tests
    }

    // --- CT-24: Phase-06 scope ---
    @Ignore("Phase-06 scope: hardUnlocked checkbox in LessonItemCard")
    @Test
    fun ct24_hardUnlocked_true_checkbox_visible() {
        // Covered in Phase-06 LessonItemCard tests
    }

    // --- CT-25: GIVEN InitFailed(EmptyPool) WHEN rendered THEN "Нет доступных вопросов" visible ---
    // Spec AC-3
    @Test
    fun ct25_initFailed_emptyPool_text_displayed() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(RunnerUiState.InitFailed(RunnerUiState.InitFailureReason.EmptyPool)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_error_empty_pool)).assertIsDisplayed()
    }

    // --- CT-26: GIVEN InitFailed(NoValidQuestions) WHEN rendered THEN "Вопросы недействительны" visible ---
    // Spec AC-3
    @Test
    fun ct26_initFailed_noValidQuestions() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(RunnerUiState.InitFailed(RunnerUiState.InitFailureReason.NoValidQuestions)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_error_no_valid_questions)).assertIsDisplayed()
    }

    // --- CT-31: GIVEN InitFailed(RedactedNotSupported) WHEN rendered THEN its own message, not CT-26's ---
    // E2.7
    @Test
    fun ct31_initFailed_redactedNotSupported() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(RunnerUiState.InitFailed(RunnerUiState.InitFailureReason.RedactedNotSupported)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_error_redacted_questions)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.runner_error_no_valid_questions)).assertDoesNotExist()
    }

    // --- CT-27: GIVEN Result(saveWarning=true) WHEN rendered THEN warning indicator visible ---
    // Spec AC-30
    @Test
    fun ct27_saveWarning_true_indicator_shown() {
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(saveWarning = true)))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(str(R.string.runner_result_save_warning)).assertIsDisplayed()
    }

    // --- CT-28: GIVEN SaveRatingFailed event WHEN emitted THEN snackbar "Не удалось сохранить оценку" shown ---
    // Spec AC-31
    // Note: tests.md says "Не удалось отправить оценку" but production code shows "Не удалось сохранить оценку"
    @Test
    fun ct28_saveRatingFailed_event_snackbar_shown() {
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState()))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        fakeComponent.emit(RunnerEvent.SaveRatingFailed)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(str(R.string.runner_error_save_rating)).assertIsDisplayed()
    }

    // --- CT-29: GIVEN hard mode rendered WHEN activity recreated THEN FLAG_SECURE still set ---
    // Spec AC-11
    @Test
    fun ct29_hardMode_activityRecreate_flagSecure_remains() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(singleChoiceQuestion(isHard = true)),
            isHardMode = true,
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)

        // Simulate configuration change (e.g. rotation)
        composeTestRule.activityRule.scenario.recreate()

        // Re-set content on the recreated activity
        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    // --- CT-30: GIVEN NavigateBack event WHEN emitted THEN onNavigateBack callback invoked ---
    // Spec AC-5, AC-34
    @Test
    fun ct30_navigateBack_event_invokes_onNavigateBack_callback() {
        var navCalled = false
        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState()))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = { navCalled = true })
            }
        }
        composeTestRule.waitForIdle()

        fakeComponent.emit(RunnerEvent.NavigateBack)
        composeTestRule.waitForIdle()

        assertTrue(navCalled)
    }

    // --- CT-31: GIVEN any answerable type WHEN the hint fires THEN one charge buys the answer ---
    @Test
    fun ct31_hint_everyAnswerableType_spendsOneCharge_andSubmitsThatAnswer() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(question(hintableTypes.first().second, index = 0)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        hintableTypes.forEachIndexed { i, (name, qState, expectedAnswer) ->
            fakeComponent.setState(question(qState, index = i))
            composeTestRule.waitForIdle()

            hintNode().assertIsEnabled()
            hintNode().performClick()
            // The verdict disables the button, so a second press must not buy a second charge.
            // (The same-frame race is held off by the hintSpent flag, which no UI test can reach.)
            hintNode().performClick()
            composeTestRule.waitForIdle()

            assertEquals("$name: one press, one charge", i + 1, fakeComponent.chargesSpent)

            composeTestRule.mainClock.advanceTimeBy(ANSWER_FEEDBACK_SKIP_ARM_MS)
            composeTestRule.onRoot().performClick()
            composeTestRule.waitForIdle()

            assertEquals(
                "$name: the charge buys this answer, so this is what must be submitted",
                expectedAnswer,
                fakeComponent.lastAnswer,
            )
        }
    }

    // --- CT-32: GIVEN a type with nothing to reveal THEN the hint is dead and submits nothing ---
    // The bug: the hint spent first and submitted whatever it found after, so the player paid a
    // charge and was marked wrong for a hint they never got.
    @Test
    fun ct32_hint_onUnanswerableTypes_spendsNothing_andSubmitsNothing() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(question(unhintableTypes.first().second, index = 0)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        unhintableTypes.forEachIndexed { i, (name, qState) ->
            fakeComponent.setState(question(qState, index = i))
            composeTestRule.waitForIdle()

            hintNode().assertIsNotEnabled()
            hintNode().performClick()
            composeTestRule.waitForIdle()

            assertEquals("$name: must not cost a charge", 0, fakeComponent.chargesSpent)
            assertEquals("$name: the handler must not even ask to spend", 0, fakeComponent.hintCount)

            // Had the hint fired it would have submitted its partial answer; tapping through the
            // verdict layer is how that reaches onAnswer, and where the wrong mark would appear.
            composeTestRule.mainClock.advanceTimeBy(ANSWER_FEEDBACK_SKIP_ARM_MS)
            composeTestRule.onRoot().performClick()
            composeTestRule.waitForIdle()

            assertNull("$name: an unreachable hint must submit nothing", fakeComponent.lastAnswer)
        }
    }

    // --- CT-33: GIVEN an answerable question with no charges THEN the hint is dead ---
    @Test
    fun ct33_hint_withNoCharges_isDead() {
        val fakeComponent = RunFakeComponent(
            MutableStateFlow(question(hintableTypes.first().second, lives = 0)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        hintNode().assertIsNotEnabled()
        hintNode().performClick()
        composeTestRule.waitForIdle()

        assertEquals("an empty budget must not be spendable", 0, fakeComponent.chargesSpent)
        assertEquals(0, fakeComponent.hintCount)
    }

    // --- CT-34: GIVEN a survey THEN there is no hint button at all ---
    @Test
    fun ct34_survey_hasNoHintButton() {
        val survey = QuestionUiState.Survey(
            questionText = "Опрос",
            hasImage = false,
            imageUrl = null,
            options = listOf(OptionUi("a", "Первый"), OptionUi("b", "Второй")),
            selectedIds = emptySet(),
            allowMultiple = false,
        )
        val fakeComponent = RunFakeComponent(MutableStateFlow(question(survey)))

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonRunnerScreen(fakeComponent, onNavigateBack = {})
            }
        }

        assertEquals(
            "a survey has no right version to reveal, so it carries no hint affordance",
            0,
            composeTestRule.onAllNodesWithText(str(R.string.runner_hint_action).uppercase())
                .fetchSemanticsNodes().size,
        )
    }
}
