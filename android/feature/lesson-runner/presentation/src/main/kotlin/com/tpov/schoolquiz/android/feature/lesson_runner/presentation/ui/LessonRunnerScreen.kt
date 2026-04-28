package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.random.Random

private const val ANSWER_FEEDBACK_DELAY_MS = 3_000L
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
) {
    val state by component.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                    snackbarHostState.showSnackbar("Не удалось сохранить результат")
                RunnerEvent.SaveRatingFailed ->
                    snackbarHostState.showSnackbar("Не удалось сохранить оценку")
                RunnerEvent.NavigateBack -> onNavigateBack()
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
            MaterialTheme.colorScheme.primary
        } else {
            null
        }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        RunnerLegacyBackground(
            isHard = isHard,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            accentColor = backgroundAccent,
        ) {
            RunnerStateContent(state = state, component = component)
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
        is RunnerUiState.Result ->
            ResultContent(
                state = state,
                onSubmitRating = { component.onSubmitRating(it) },
                onFinish = { component.onFinish() },
                modifier = Modifier.fillMaxSize(),
            )
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
        Text(text = reason.displayMessage(), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onBack) { Text("Назад") }
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

    fun submitFeedbackNow() {
        val answer = feedback?.answer ?: return
        canSkipFeedback = false
        feedback = null
        component.onAnswer(answer)
    }

    LaunchedEffect(feedback) {
        canSkipFeedback = false
        if (feedback == null) {
            return@LaunchedEffect
        }
        delay(ANSWER_FEEDBACK_SKIP_ARM_DELAY_MS)
        canSkipFeedback = true
        delay(ANSWER_FEEDBACK_DELAY_MS - ANSWER_FEEDBACK_SKIP_ARM_DELAY_MS)
        submitFeedbackNow()
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
            onCrossClick = { component.onCrossButtonTap() },
            onTimeout = {
                if (feedback == null) {
                    val timeoutFeedback =
                        buildTimeoutFeedback(
                            qState = state.questionUiState,
                            currentDraft = state.currentDraft,
                            seed = state.indexInPool.toLong() xor state.deadlineMs,
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
                component = component,
                onFeedback = { feedback = it },
            )
            if (feedback != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                enabled = canSkipFeedback,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = ::submitFeedbackNow,
                            ),
                )
            }
        }
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

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "ktlint:standard:function-naming")
@Composable
private fun QuestionTypeContent(
    qState: QuestionUiState,
    currentDraft: UserAnswerDraft?,
    feedback: AnswerFeedback?,
    component: LessonRunnerRootComponent,
    onFeedback: (AnswerFeedback) -> Unit,
) {
    when (qState) {
        is QuestionUiState.SingleChoice ->
            SingleChoiceContent(
                state = qState,
                onOptionSelected = { optionId ->
                    val draft = UserAnswerDraft.SingleChoiceDraft(OptionId(optionId))
                    component.onDraftChanged(draft)
                    onFeedback(
                        AnswerFeedback.SingleChoice(
                            answer = draft,
                            selectedId = optionId,
                            correctId = qState.correctOptionId,
                        ),
                    )
                },
                feedback = feedback as? AnswerFeedback.SingleChoice,
                modifier = Modifier.fillMaxSize(),
            )
        is QuestionUiState.MultipleChoice -> {
            val currentSelected =
                (currentDraft as? UserAnswerDraft.MultipleChoiceDraft)
                    ?.selected?.map { it.raw }?.toSet() ?: qState.selectedIds
            MultipleChoiceContent(
                state = qState.copy(selectedIds = currentSelected),
                onOptionToggled = { optionId ->
                    val newSelected =
                        if (optionId in currentSelected) currentSelected - optionId else currentSelected + optionId
                    component.onDraftChanged(
                        UserAnswerDraft.MultipleChoiceDraft(newSelected.map { OptionId(it) }.toSet()),
                    )
                },
                onSubmit = {
                    val draft = UserAnswerDraft.MultipleChoiceDraft(currentSelected.map { OptionId(it) }.toSet())
                    onFeedback(
                        AnswerFeedback.MultipleChoice(
                            answer = draft,
                            selectedIds = currentSelected,
                            correctIds = qState.correctIds,
                        ),
                    )
                },
                feedback = feedback as? AnswerFeedback.MultipleChoice,
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
                        ),
                    )
                },
                feedback = orderingFeedback,
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
                        ),
                    )
                },
                feedback = feedback as? AnswerFeedback.FillBlank,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun buildTimeoutFeedback(
    qState: QuestionUiState,
    currentDraft: UserAnswerDraft?,
    seed: Long,
): AnswerFeedback? {
    val random = Random(seed)
    return when (qState) {
        is QuestionUiState.SingleChoice -> buildSingleChoiceTimeoutFeedback(qState, currentDraft, random)
        is QuestionUiState.MultipleChoice -> buildMultipleChoiceTimeoutFeedback(qState, currentDraft, random)
        is QuestionUiState.Ordering -> buildOrderingTimeoutFeedback(qState, currentDraft, random)
        is QuestionUiState.FillBlank -> buildFillBlankTimeoutFeedback(qState, currentDraft, random)
    }
}

private fun buildSingleChoiceTimeoutFeedback(
    qState: QuestionUiState.SingleChoice,
    currentDraft: UserAnswerDraft?,
    random: Random,
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
    )
}

private fun buildMultipleChoiceTimeoutFeedback(
    qState: QuestionUiState.MultipleChoice,
    currentDraft: UserAnswerDraft?,
    random: Random,
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
    )
}

private fun buildOrderingTimeoutFeedback(
    qState: QuestionUiState.Ordering,
    currentDraft: UserAnswerDraft?,
    random: Random,
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
    )
}

private fun buildFillBlankTimeoutFeedback(
    qState: QuestionUiState.FillBlank,
    currentDraft: UserAnswerDraft?,
    random: Random,
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
    )
}

private fun RunnerUiState.InitFailureReason.displayMessage(): String =
    when (this) {
        RunnerUiState.InitFailureReason.AuthRequired -> "Требуется авторизация"
        RunnerUiState.InitFailureReason.LessonNotFound -> "Урок не найден"
        RunnerUiState.InitFailureReason.EmptyPool -> "Нет доступных вопросов"
        RunnerUiState.InitFailureReason.NoValidQuestions -> "Вопросы недействительны"
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

    override fun onBack() = Unit
}
