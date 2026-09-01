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
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.random.Random

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
                        buildTimeoutFeedback(
                            qState = state.questionUiState,
                            currentDraft = state.currentDraft,
                            seed = state.indexInPool.toLong() xor state.deadlineMs,
                            revealCorrect = state.revealCorrect,
                        )
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
                revealCorrect = state.revealCorrect,
                component = component,
                hint =
                    rememberHintControl(
                        qState = state.questionUiState,
                        charges = state.lives,
                        feedbackShown = feedback != null,
                        questionKey = state.indexInPool,
                        component = component,
                    ),
                onFeedback = { feedback = it },
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
    if (showInfoDialog && infoText != null) {
        QuestionInfoDialog(
            info = infoText,
            onDismiss = { showInfoDialog = false },
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
    charges: Int?,
    feedbackShown: Boolean,
    questionKey: Int,
    component: LessonRunnerRootComponent,
): HintControl {
    var spent by remember(questionKey) { mutableStateOf(false) }
    return HintControl(
        enabled = !spent && isHintAvailable(qState, charges, feedbackShown),
        spend = {
            val didSpend = !spent && component.hintRequested()
            if (didSpend) spent = true
            didSpend
        },
    )
}

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "ktlint:standard:function-naming")
@Composable
private fun QuestionTypeContent(
    qState: QuestionUiState,
    currentDraft: UserAnswerDraft?,
    feedback: AnswerFeedback?,
    revealCorrect: Boolean,
    component: LessonRunnerRootComponent,
    hint: HintControl,
    onFeedback: (AnswerFeedback) -> Unit,
) {
    // Whether a hint exists was decided once, by isHintAvailable, before this ran. Each handler
    // below builds its own typed draft and spends only once that draft is in hand, so a question
    // with nothing playable to reveal can never take a charge — and the typed builders mean a
    // handler wired to the wrong draft type does not compile.
    val hintEnabled = hint.enabled
    when (qState) {
        is QuestionUiState.Survey ->
            SurveyContent(
                state = qState,
                onOptionToggled = { optionId ->
                    val current =
                        (component.uiState.value as? RunnerUiState.Question)
                            ?.currentDraft as? UserAnswerDraft.SurveyDraft
                    val selected = current?.selected.orEmpty()
                    val id = OptionId(optionId)
                    val next =
                        when {
                            !qState.allowMultiple -> setOf(id)
                            id in selected -> selected - id
                            else -> selected + id
                        }
                    component.onDraftChanged(UserAnswerDraft.SurveyDraft(next))
                },
                onSubmit = {
                    val draft =
                        (component.uiState.value as? RunnerUiState.Question)
                            ?.currentDraft as? UserAnswerDraft.SurveyDraft
                            ?: UserAnswerDraft.SurveyDraft(emptySet())
                    onFeedback(
                        AnswerFeedback.Survey(
                            answer = draft,
                            selectedIds = draft.selected.map { it.raw }.toSet(),
                        ),
                    )
                },
                feedback = feedback as? AnswerFeedback.Survey,
            )
        is QuestionUiState.SingleChoice -> {
            val selectAndReveal: (String) -> Unit = { optionId ->
                val draft = UserAnswerDraft.SingleChoiceDraft(OptionId(optionId))
                component.onDraftChanged(draft)
                onFeedback(
                    AnswerFeedback.SingleChoice(
                        answer = draft,
                        selectedId = optionId,
                        correctId = qState.correctOptionId,
                        revealCorrect = revealCorrect,
                    ),
                )
            }
            SingleChoiceContent(
                state = qState,
                onOptionSelected = selectAndReveal,
                feedback = feedback as? AnswerFeedback.SingleChoice,
                hintEnabled = hintEnabled,
                onHint = {
                    val correctId = qState.hintDraft()?.selected?.raw
                    if (correctId != null && hint.spend()) selectAndReveal(correctId)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        is QuestionUiState.MultipleChoice -> {
            val currentSelected =
                (currentDraft as? UserAnswerDraft.MultipleChoiceDraft)
                    ?.selected?.map { it.raw }?.toSet() ?: qState.selectedIds
            val submitWith: (Set<String>) -> Unit = { ids ->
                val draft = UserAnswerDraft.MultipleChoiceDraft(ids.map { OptionId(it) }.toSet())
                onFeedback(
                    AnswerFeedback.MultipleChoice(
                        answer = draft,
                        selectedIds = ids,
                        correctIds = qState.correctIds,
                        revealCorrect = revealCorrect,
                    ),
                )
            }
            MultipleChoiceContent(
                state = qState.copy(selectedIds = currentSelected),
                onOptionToggled = { optionId ->
                    val newSelected =
                        if (optionId in currentSelected) currentSelected - optionId else currentSelected + optionId
                    component.onDraftChanged(
                        UserAnswerDraft.MultipleChoiceDraft(newSelected.map { OptionId(it) }.toSet()),
                    )
                },
                onSubmit = { submitWith(currentSelected) },
                feedback = feedback as? AnswerFeedback.MultipleChoice,
                hintEnabled = hintEnabled,
                onHint = {
                    val draft = qState.hintDraft()
                    if (draft != null && hint.spend()) {
                        // The component owns the draft; without this the paid-for answer would
                        // live only in the transient feedback, as it did before.
                        component.onDraftChanged(draft)
                        submitWith(draft.selected.map { it.raw }.toSet())
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        is QuestionUiState.Ordering -> {
            val orderingFeedback = feedback as? AnswerFeedback.Ordering
            val draftOrder = (currentDraft as? UserAnswerDraft.OrderingDraft)?.order
            val itemById = qState.items.associateBy { it.id }
            val currentItems =
                if (orderingFeedback != null) {
                    orderingFeedback.orderIds.mapNotNull { itemById[it] }.takeIf { it.size == qState.items.size }
                        ?: qState.items
                } else if (draftOrder != null && draftOrder.size == qState.items.size) {
                    draftOrder.mapNotNull { itemById[it.raw] }.takeIf { it.size == qState.items.size }
                        ?: qState.items
                } else {
                    qState.items
                }
            OrderingContent(
                state = qState.copy(items = currentItems),
                onMoveUp = { index ->
                    if (index > 0) {
                        val newItems =
                            currentItems.toMutableList().also {
                                val tmp = it[index]
                                it[index] = it[index - 1]
                                it[index - 1] = tmp
                            }
                        component.onDraftChanged(UserAnswerDraft.OrderingDraft(newItems.map { OptionId(it.id) }))
                    }
                },
                onMoveDown = { index ->
                    if (index < currentItems.lastIndex) {
                        val newItems =
                            currentItems.toMutableList().also {
                                val tmp = it[index]
                                it[index] = it[index + 1]
                                it[index + 1] = tmp
                            }
                        component.onDraftChanged(UserAnswerDraft.OrderingDraft(newItems.map { OptionId(it.id) }))
                    }
                },
                onReorder = { from, to ->
                    val newItems = currentItems.toMutableList()
                    val item = newItems.removeAt(from)
                    newItems.add(to, item)
                    component.onDraftChanged(UserAnswerDraft.OrderingDraft(newItems.map { OptionId(it.id) }))
                },
                onSubmit = {
                    val draft = UserAnswerDraft.OrderingDraft(currentItems.map { OptionId(it.id) })
                    onFeedback(
                        AnswerFeedback.Ordering(
                            answer = draft,
                            orderIds = currentItems.map { it.id },
                            correctOrderIds = qState.correctOrderIds,
                            revealCorrect = revealCorrect,
                        ),
                    )
                },
                feedback = orderingFeedback,
                hintEnabled = hintEnabled,
                onHint = {
                    // correctOrderIds is the content order, i.e. the right arrangement itself.
                    val draft = qState.hintDraft()
                    if (draft != null && hint.spend()) {
                        component.onDraftChanged(draft)
                        onFeedback(
                            AnswerFeedback.Ordering(
                                answer = draft,
                                orderIds = draft.order.map { it.raw },
                                correctOrderIds = qState.correctOrderIds,
                                revealCorrect = revealCorrect,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        is QuestionUiState.FillBlank -> {
            val filledRaw =
                (currentDraft as? UserAnswerDraft.FillBlankDraft)
                    ?.filled
                    ?.mapNotNull { (blankId, candidateId) -> candidateId?.let { blankId.raw to it.raw } }
                    ?.toMap() ?: emptyMap()
            val blankParts = qState.templateParts.filterIsInstance<TemplatePart.Blank>()
            val blanksByIndex = blankParts.associateBy { it.index }
            val candidateById = qState.candidates.associateBy { it.id }
            val displayFilledValues =
                filledRaw.entries.mapNotNull { (blankId, candidateId) ->
                    val blank = blankParts.firstOrNull { it.blankId == blankId } ?: return@mapNotNull null
                    val text = candidateById[candidateId]?.text ?: return@mapNotNull null
                    blank.index to text
                }.toMap()
            FillBlankContent(
                state = qState.copy(filledValues = displayFilledValues),
                candidates = qState.candidates,
                usedCandidateIds = filledRaw.values.toSet(),
                onCandidateSelected = { blankIndex, candidateId ->
                    blanksByIndex[blankIndex]?.let { blank ->
                        val newFilled = filledRaw + (blank.blankId to candidateId)
                        component.onDraftChanged(
                            UserAnswerDraft.FillBlankDraft(
                                newFilled.mapKeys { BlankId(it.key) }.mapValues { CandidateId(it.value) },
                            ),
                        )
                    }
                },
                onBlankCleared = { blankIndex ->
                    blanksByIndex[blankIndex]?.let { blank ->
                        val newFilled = filledRaw - blank.blankId
                        component.onDraftChanged(
                            UserAnswerDraft.FillBlankDraft(
                                newFilled.mapKeys { BlankId(it.key) }.mapValues { CandidateId(it.value) },
                            ),
                        )
                    }
                },
                onSubmit = {
                    val draft =
                        UserAnswerDraft.FillBlankDraft(
                            filledRaw.mapKeys { BlankId(it.key) }.mapValues { CandidateId(it.value) },
                        )
                    onFeedback(
                        AnswerFeedback.FillBlank(
                            answer = draft,
                            filledCandidateIdsByBlankIndex =
                                filledRaw.mapNotNull { (blankId, candidateId) ->
                                    val blank =
                                        blankParts.firstOrNull { it.blankId == blankId }
                                            ?: return@mapNotNull null
                                    blank.index to candidateId
                                }.toMap(),
                            correctCandidateIdsByBlankIndex = qState.correctCandidateIdsByBlankIndex,
                            revealCorrect = revealCorrect,
                        ),
                    )
                },
                feedback = feedback as? AnswerFeedback.FillBlank,
                hintEnabled = hintEnabled,
                onHint = {
                    val draft = qState.hintDraft()
                    if (draft != null && hint.spend()) {
                        component.onDraftChanged(draft)
                        onFeedback(
                            AnswerFeedback.FillBlank(
                                answer = draft,
                                // Read off what is actually submitted, not off the answer key.
                                // Handing revealDigit() the key twice made every fill-blank hint
                                // report a perfect 9 whatever the draft contained.
                                filledCandidateIdsByBlankIndex = draft.filledByBlankIndex(blankParts),
                                correctCandidateIdsByBlankIndex = qState.correctCandidateIdsByBlankIndex,
                                revealCorrect = revealCorrect,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
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
private fun BoxScope.FeedbackOverlay(
    hasAnswered: Boolean,
    feedbackDigit: Int?,
    canSkip: Boolean,
    onSkip: () -> Unit,
) {
    if (!hasAnswered) return
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .clickable(
                    enabled = canSkip,
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

private fun buildTimeoutFeedback(
    qState: QuestionUiState,
    currentDraft: UserAnswerDraft?,
    seed: Long,
    revealCorrect: Boolean,
): AnswerFeedback? {
    val random = Random(seed)
    return when (qState) {
        is QuestionUiState.Survey ->
            AnswerFeedback.Survey(
                answer =
                    currentDraft as? UserAnswerDraft.SurveyDraft
                        ?: UserAnswerDraft.SurveyDraft(emptySet()),
                selectedIds =
                    (currentDraft as? UserAnswerDraft.SurveyDraft)
                        ?.selected?.map { it.raw }?.toSet().orEmpty(),
            )
        is QuestionUiState.SingleChoice -> buildSingleChoiceTimeoutFeedback(qState, currentDraft, random, revealCorrect)
        is QuestionUiState.MultipleChoice ->
            buildMultipleChoiceTimeoutFeedback(
                qState,
                currentDraft,
                random,
                revealCorrect,
            )
        is QuestionUiState.Ordering -> buildOrderingTimeoutFeedback(qState, currentDraft, random, revealCorrect)
        is QuestionUiState.FillBlank -> buildFillBlankTimeoutFeedback(qState, currentDraft, random, revealCorrect)
    }
}

private fun buildSingleChoiceTimeoutFeedback(
    qState: QuestionUiState.SingleChoice,
    currentDraft: UserAnswerDraft?,
    random: Random,
    revealCorrect: Boolean,
): AnswerFeedback.SingleChoice? {
    val selectedId =
        (currentDraft as? UserAnswerDraft.SingleChoiceDraft)?.selected?.raw
            ?: qState.options.shuffled(random).firstOrNull()?.id
            ?: return null
    val draft = UserAnswerDraft.SingleChoiceDraft(OptionId(selectedId))
    return AnswerFeedback.SingleChoice(
        answer = draft,
        selectedId = selectedId,
        correctId = qState.correctOptionId,
        revealCorrect = revealCorrect,
    )
}

private fun buildMultipleChoiceTimeoutFeedback(
    qState: QuestionUiState.MultipleChoice,
    currentDraft: UserAnswerDraft?,
    random: Random,
    revealCorrect: Boolean,
): AnswerFeedback.MultipleChoice? {
    val validIds = qState.options.map { it.id }.toSet()
    if (validIds.isEmpty()) return null
    val selectedFromDraft =
        (currentDraft as? UserAnswerDraft.MultipleChoiceDraft)
            ?.selected
            ?.map { it.raw }
            ?.filter { it in validIds }
            ?.toSet()
            ?: emptySet()
    val targetSize = qState.correctIds.size.coerceAtLeast(1).coerceAtMost(validIds.size)
    val randomRemainder =
        qState.options
            .map { it.id }
            .filter { it !in selectedFromDraft }
            .shuffled(random)
            .take((targetSize - selectedFromDraft.size).coerceAtLeast(0))
    val selectedIds = selectedFromDraft + randomRemainder
    val draft = UserAnswerDraft.MultipleChoiceDraft(selectedIds.map { OptionId(it) }.toSet())
    return AnswerFeedback.MultipleChoice(
        answer = draft,
        selectedIds = selectedIds,
        correctIds = qState.correctIds,
        revealCorrect = revealCorrect,
    )
}

private fun buildOrderingTimeoutFeedback(
    qState: QuestionUiState.Ordering,
    currentDraft: UserAnswerDraft?,
    random: Random,
    revealCorrect: Boolean,
): AnswerFeedback.Ordering? {
    val validIds = qState.items.map { it.id }
    if (validIds.isEmpty()) return null
    val draftOrder =
        (currentDraft as? UserAnswerDraft.OrderingDraft)
            ?.order
            ?.map { it.raw }
            ?.filter { it in validIds }
    val orderIds =
        if (draftOrder.isNullOrEmpty()) {
            validIds.shuffled(random)
        } else {
            draftOrder + validIds.filter { it !in draftOrder }
        }
    val draft = UserAnswerDraft.OrderingDraft(orderIds.map { OptionId(it) })
    return AnswerFeedback.Ordering(
        answer = draft,
        orderIds = orderIds,
        correctOrderIds = qState.correctOrderIds,
        revealCorrect = revealCorrect,
    )
}

private fun buildFillBlankTimeoutFeedback(
    qState: QuestionUiState.FillBlank,
    currentDraft: UserAnswerDraft?,
    random: Random,
    revealCorrect: Boolean,
): AnswerFeedback.FillBlank? {
    val blankParts = qState.templateParts.filterIsInstance<TemplatePart.Blank>()
    val candidateIds = qState.candidates.map { it.id }
    if (blankParts.isEmpty() || candidateIds.isEmpty()) return null
    val validBlankIds = blankParts.map { it.blankId }.toSet()
    val validCandidateIds = candidateIds.toSet()
    val filledByBlankId =
        blankParts.associate { blank ->
            val draftCandidate =
                (currentDraft as? UserAnswerDraft.FillBlankDraft)
                    ?.filled
                    ?.get(BlankId(blank.blankId))
                    ?.raw
                    ?.takeIf { it in validCandidateIds }
            blank.blankId to (draftCandidate ?: candidateIds.shuffled(random).first())
        }.filterKeys { it in validBlankIds }
    val draft =
        UserAnswerDraft.FillBlankDraft(
            filledByBlankId
                .mapKeys { BlankId(it.key) }
                .mapValues { CandidateId(it.value) },
        )
    return AnswerFeedback.FillBlank(
        answer = draft,
        filledCandidateIdsByBlankIndex =
            blankParts.associate { blank ->
                blank.index to filledByBlankId.getValue(blank.blankId)
            },
        correctCandidateIdsByBlankIndex = qState.correctCandidateIdsByBlankIndex,
        revealCorrect = revealCorrect,
    )
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

    override fun onFinish() = Unit

    override fun onRunAgain() = Unit

    override fun onNextLesson() = Unit

    override fun hintRequested(): Boolean = false

    override fun onBack() = Unit

    override fun onPostComment(text: String) = Unit
}
