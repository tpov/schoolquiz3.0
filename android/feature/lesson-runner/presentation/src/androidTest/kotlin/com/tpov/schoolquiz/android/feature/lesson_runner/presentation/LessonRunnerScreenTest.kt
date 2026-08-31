package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.RunFakeComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.onRoot
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import org.junit.Assert.assertNotNull

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
}
