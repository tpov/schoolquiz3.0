package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirBg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirMode
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.rememberNoirState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

// Design decision §4.4: the verdict waits for a tap instead of advancing on a hidden timer;
// the short arm delay swallows the double-tap that selected the answer in the first place.
private const val ANSWER_FEEDBACK_SKIP_ARM_DELAY_MS = 250L

@Composable
fun rememberFlagSecure(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window =
            (context as? Activity)?.window
                ?: return@DisposableEffect onDispose {}
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun LessonRunnerScreen(
    component: LessonRunnerRootComponent,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLesson: (String) -> Unit = {},
) {
    val state by component.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveResultError = stringResource(R.string.runner_error_save_result)
    val saveRatingError = stringResource(R.string.runner_error_save_rating)

    rememberFlagSecure(enabled = component.isHardMode)

    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    DisposableEffect(Unit) {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(component) {
        component.events.collect { event ->
            when (event) {
                is RunnerEvent.SaveAttemptFailed ->
                    snackbarHostState.showSnackbar(saveResultError)
                RunnerEvent.SaveRatingFailed ->
                    snackbarHostState.showSnackbar(saveRatingError)
                RunnerEvent.NavigateBack -> onNavigateBack()
                is RunnerEvent.OpenNextLesson -> onOpenLesson(event.lessonId)
            }
        }
    }

    val isHard =
        when (val s = state) {
            is RunnerUiState.Question -> s.isHard
            else -> component.isHardMode
        }
    val backgroundAccent =
        if (state is RunnerUiState.Result) {
            LocalNoirAccent.current
        } else {
            null
        }
    // NOIR lives inside the existing theme rather than replacing it, so the rest of the app is
    // untouched while screens move over one at a time. It is provided here rather than at the app
    // root because the root files are mid-edit, and because the runner is the one place that knows
    // which mode the round is being played in.
    val noirState = rememberNoirState()
    LaunchedEffect(isHard) {
        noirState.mode = if (isHard) NoirMode.Hard else NoirMode.Easy
    }
    NoirTheme(state = noirState) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = NoirBg,
        ) { innerPadding ->
            RunnerDesignBackground(
                isHard = isHard,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                accentColor = backgroundAccent,
            ) {
                // No floating glyphs behind a question. The drawing has none, and what they
                // actually produce is a dozen dark shapes scattered across the answers — texture
                // where the screen is asking somebody to read carefully.
                RunnerStateContent(state = state, component = component)
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RunnerStateContent(
    state: RunnerUiState,
    component: LessonRunnerRootComponent,
) {
    when (state) {
        RunnerUiState.Loading ->
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        is RunnerUiState.InitFailed ->
            Box(modifier = Modifier.fillMaxSize()) {
                InitFailedContent(
                    reason = state.reason,
                    onBack = { component.onBack() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        is RunnerUiState.Question ->
            QuestionStateContent(state = state, component = component)
        is RunnerUiState.Result -> {
            val comments by component.comments.collectAsState()
            ResultContent(
                state = state,
                comments = comments,
                onSubmitRating = { component.onSubmitRating(it) },
                onRunAgain = { component.onRunAgain() },
                onNextLesson = { component.onNextLesson() },
                onPostComment = { component.onPostComment(it) },
                onSyncResult = component::onSyncResult,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun InitFailedContent(
    reason: RunnerUiState.InitFailureReason,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(reason.messageRes),
            style = NoirType.rowTitle,
        )
        TextButton(onClick = onBack) { Text(stringResource(R.string.runner_action_back)) }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun QuestionStateContent(
    state: RunnerUiState.Question,
    component: LessonRunnerRootComponent,
) {
    var feedback by remember(state.indexInPool) { mutableStateOf<AnswerFeedback?>(null) }
    var canSkipFeedback by remember(state.indexInPool) { mutableStateOf(false) }
    val infoText = state.questionUiState.info
    var showInfoDialog by remember(state.indexInPool, infoText) { mutableStateOf(false) }

    fun submitFeedbackNow() {
        val answer = feedback?.answer ?: return
        canSkipFeedback = false
        feedback = null
        component.onAnswer(answer)
    }

    // No auto-advance: the verdict stays until the tap (design §4.4).
    LaunchedEffect(feedback) {
        canSkipFeedback = false
        if (feedback == null || showInfoDialog) return@LaunchedEffect
        delay(ANSWER_FEEDBACK_SKIP_ARM_DELAY_MS)
        canSkipFeedback = true
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 24.dp, bottom = 16.dp),
    ) {
        QuestionProgressHeader(
            indexInPool = state.indexInPool,
            totalInPool = state.totalInPool,
            deadlineMs = state.deadlineMs,
            isPaused = state.isPaused,
            isHard = state.isHard,
            lives = state.lives,
            onCrossClick = { component.onCrossButtonTap() },
            onTimeout = {
                if (feedback == null) {
                    val timeoutFeedback =
                        if (state.serverScored) {
                            null
                        } else {
                            buildTimeoutFeedback(
                                qState = state.questionUiState,
                                currentDraft = state.currentDraft,
                                seed = state.indexInPool.toLong() xor state.deadlineMs,
                                revealCorrect = state.revealCorrect,
                            )
                        }
                    if (timeoutFeedback != null) {
                        feedback = timeoutFeedback
                    } else {
                        component.onTimeout()
                    }
                }
            },
            modifier = Modifier.statusBarsPadding(),
        )
        Box(modifier = Modifier.weight(1f).navigationBarsPadding()) {
            QuestionTypeContent(
                qState = state.questionUiState,
                currentDraft = state.currentDraft,
                feedback = feedback,
                revealCorrect = state.revealCorrect && state.questionUiState.hasLocalAnswerKey,
                interaction =
                    QuestionInteraction(
                        readDraft = { (component.uiState.value as? RunnerUiState.Question)?.currentDraft },
                        changeDraft = component::onDraftChanged,
                        selectAnswer = component::onDraftChanged,
                        hint =
                            rememberHintControl(
                                qState = state.questionUiState,
                                isHard = state.isHard,
                                charges = state.lives,
                                feedbackShown = feedback != null,
                                questionKey = state.indexInPool,
                                component = component,
                            ),
                        feedback = { feedback = it },
                    ),
            )
            FeedbackOverlay(
                hasAnswered = feedback != null,
                feedbackDigit = feedback?.revealDigit(),
                canSkip = canSkipFeedback,
                onSkip = ::submitFeedbackNow,
            )
            if (feedback != null && infoText != null) {
                QuestionInfoButton(
                    onClick = { showInfoDialog = true },
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 18.dp, end = 20.dp),
                )
            }
        }
    }
    QuestionStateDialogs(state, component, showInfoDialog) { showInfoDialog = false }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun QuestionStateDialogs(
    state: RunnerUiState.Question,
    component: LessonRunnerRootComponent,
    showInfoDialog: Boolean,
    dismissInfo: () -> Unit,
) {
    val infoText = state.questionUiState.info
    if (showInfoDialog && infoText != null) {
        QuestionInfoDialog(
            info = infoText,
            onDismiss = dismissInfo,
        )
    }
    if (state.isPaused) {
        BlockingResumeDialog(
            onContinue = { component.onContinue() },
            onExit = { component.onExit() },
        )
    }
    if (state.showExitConfirmDialog) {
        ExitConfirmDialog(
            onConfirm = { component.onCrossConfirmed() },
            onDismiss = { component.onCrossCancelled() },
        )
    }
}

/**
 * The hint's state for one question: whether the button is live, and a spend that succeeds at
 * most once.
 *
 * The button is disabled by recomposition, which a second tap in the same frame beats, so the
 * once-only flag is read inside the spend rather than trusted from the enabled state. It resets
 * with [questionKey], i.e. per question.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun rememberHintControl(
    qState: QuestionUiState,
    isHard: Boolean,
    charges: Int?,
    feedbackShown: Boolean,
    questionKey: Int,
    component: LessonRunnerRootComponent,
): HintControl {
    var spent by remember(questionKey) { mutableStateOf(false) }
    return HintControl(
        enabled = !spent && isHintAvailable(qState, isHard, charges, feedbackShown),
        spend = {
            val didSpend = !spent && component.hintRequested()
            if (didSpend) spent = true
            didSpend
        },
    )
}

/**
 * Tap-anywhere layer plus the reveal line — the mockup's verdict slot, which replaces the old
 * invisible three-second overlay. The verdict waits for a tap instead of advancing on a timer.
 *
 * The tap layer is the only route to the next question, so it is drawn for every answered question.
 * The banner is not: a hard question reveals nothing, so [feedbackDigit] is null and the layer
 * carries no verdict — which is the point. Tying the layer's existence to the banner's is what
 * stalled a hard lesson on its first answer.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun BoxScope.FeedbackOverlay(
    hasAnswered: Boolean,
    feedbackDigit: Int?,
    canSkip: Boolean,
    onSkip: () -> Unit,
) {
    if (!hasAnswered) return
    val continueLabel = stringResource(R.string.runner_feedback_continue_action)
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .semantics { contentDescription = continueLabel }
                .clickable(
                    enabled = canSkip,
                    role = Role.Button,
                    onClickLabel = continueLabel,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onSkip,
                ),
    )
    if (feedbackDigit != null) {
        VerdictBanner(
            digit = feedbackDigit,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun VerdictBanner(
    digit: Int,
    modifier: Modifier = Modifier,
) {
    val toneColor =
        when (digit) {
            PERFECT_DIGIT -> NoirSuccess
            WORST_DIGIT -> NoirDanger
            else -> LocalNoirAccent.current
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = verdictLabel(digit).uppercase(),
            style = NoirType.button.copy(fontSize = 12.sp, color = toneColor),
        )
        Text(
            text = stringResource(R.string.runner_verdict_continue).uppercase(),
            style = NoirType.chip.copy(color = NoirTOff),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun verdictLabel(digit: Int): String =
    when (digit) {
        PERFECT_DIGIT -> stringResource(R.string.runner_verdict_correct)
        WORST_DIGIT -> stringResource(R.string.runner_verdict_wrong)
        else -> stringResource(R.string.runner_verdict_partial, digit)
    }

private val previewQuestionOptions =
    listOf(
        OptionUi("1", "Париж"),
        OptionUi("2", "Берлин"),
        OptionUi("3", "Лондон"),
        OptionUi("4", "Мадрид"),
    )

private fun previewQuestionState(isHard: Boolean) =
    RunnerUiState.Question(
        questionUiState =
            QuestionUiState.SingleChoice(
                questionText = "Какая столица Франции?",
                hasImage = false,
                imageUrl = null,
                options = previewQuestionOptions,
                selectedOptionId = null,
            ),
        indexInPool = 1,
        totalInPool = 5,
        deadlineMs = 0L,
        isPaused = false,
        isHard = isHard,
        showExitConfirmDialog = false,
    )

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, name = "EASY mode — green tint + neutral header")
@Composable
private fun LessonRunnerScreenEasyPreview() {
    SchoolQuizTheme {
        LessonRunnerScreen(
            component = PreviewLessonRunnerComponent(previewQuestionState(isHard = false)),
            onNavigateBack = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, name = "HARD mode — red tint + errorContainer header")
@Composable
private fun LessonRunnerScreenHardPreview() {
    SchoolQuizTheme {
        LessonRunnerScreen(
            component = PreviewLessonRunnerComponent(previewQuestionState(isHard = true)),
            onNavigateBack = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonRunnerScreenLoadingPreview() {
    SchoolQuizTheme {
        LessonRunnerScreen(
            component = PreviewLessonRunnerComponent(RunnerUiState.Loading),
            onNavigateBack = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonRunnerScreenInitFailedPreview() {
    SchoolQuizTheme {
        LessonRunnerScreen(
            component =
                PreviewLessonRunnerComponent(
                    RunnerUiState.InitFailed(RunnerUiState.InitFailureReason.AuthRequired),
                ),
            onNavigateBack = {},
        )
    }
}

private class PreviewLessonRunnerComponent(initialState: RunnerUiState) : LessonRunnerRootComponent {
    override val isHardMode: Boolean = false
    override val uiState: StateFlow<RunnerUiState> = MutableStateFlow(initialState)
    override val events: Flow<RunnerEvent> = emptyFlow()
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

    override fun onBack() = Unit

    override fun onPostComment(text: String) = Unit
}
