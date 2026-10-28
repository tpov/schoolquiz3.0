@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunAction
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunnerStatus
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunnerView
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaFrozenRatingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import kotlinx.coroutines.delay

/*
 * NOIR (эпик 9.3): сырое Text/TextButton семейства результата/внимания — на NoirType/NoirButton.
 * Строки не менялись: квитанции арены читает риг (3.x-джорни управляются текстами и семантикой).
 */

@Composable
internal fun ArenaRunnerContent(component: ArenaRunnerRootComponent) {
    val frame by component.arenaView.collectAsState()
    rememberFlagSecure(enabled = (frame as? ArenaRunnerView.Question)?.question?.isHard == true)
    ArenaRunnerLifecycle(component)
    when (val current = frame) {
        is ArenaRunnerView.Question -> ArenaQuestion(current, component)
        is ArenaRunnerView.Result -> ArenaResult(current, component)
        is ArenaRunnerView.Attention -> ArenaAttention(current, component)
    }
}

@Composable
private fun ArenaQuestion(
    frame: ArenaRunnerView.Question,
    component: ArenaRunnerRootComponent,
) {
    var showInfo by remember(frame.token.attemptId, frame.token.index) { mutableStateOf(false) }
    var armed by remember(frame.token) { mutableStateOf(false) }
    val active = frame.foreground && !frame.busy && !frame.question.showExitConfirmDialog
    LaunchedEffect(frame.token, active, showInfo) {
        armed = false
        if (active && !showInfo && frame.feedback != null) {
            delay(FEEDBACK_ARM_MS)
            armed = true
        }
    }
    if (frame.foreground && frame.token.phase == ArenaRunnerPhase.PAUSED) {
        // No question text behind a saving spinner: the next slot is not exposed until reveal CAS succeeds.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (frame.busy) CircularProgressIndicator(color = LocalNoirAccent.current)
        }
        ArenaDialogs(frame, component, false) {}
    } else if (frame.foreground) {
        Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 16.dp)) {
            ArenaHeader(frame, component)
            Box(Modifier.weight(1f)) {
                ArenaQuestionBody(frame, component)
                FeedbackOverlay(
                    hasAnswered = frame.feedback != null,
                    feedbackDigit = frame.feedback?.revealDigit(),
                    canSkip = active && armed && !showInfo,
                    onSkip = { component.onArenaAction(frame.token, ArenaRunAction.Continue) },
                )
                if (frame.feedback != null && frame.question.questionUiState.info != null && active) {
                    QuestionInfoButton(
                        onClick = { showInfo = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
                    )
                }
                if (frame.busy) {
                    CircularProgressIndicator(
                        color = LocalNoirAccent.current,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
        ArenaDialogs(frame, component, showInfo) { showInfo = false }
    }
}

@Composable
private fun ArenaHeader(
    frame: ArenaRunnerView.Question,
    component: ArenaRunnerRootComponent,
) {
    val question = frame.question
    QuestionProgressHeader(
        indexInPool = question.indexInPool,
        totalInPool = question.totalInPool,
        deadlineMs = question.deadlineMs,
        isPaused =
            frame.busy || frame.token.phase != ArenaRunnerPhase.ANSWERING ||
                !frame.foreground || question.showExitConfirmDialog,
        isHard = question.isHard,
        lives = question.lives,
        onCrossClick = component::onCrossButtonTap,
        onTimeout = { component.onArenaAction(frame.token, ArenaRunAction.Timeout) },
    )
    if (!question.isHard && question.revealCorrect) {
        NoirButton(
            text = stringResource(R.string.arena_run_refresh_hint_budget),
            onClick = component::onArenaRefreshWallet,
            enabled = !frame.busy,
            style = NoirButtonStyle.Ghost,
            fillWidth = false,
        )
    }
}

@Composable
private fun ArenaQuestionBody(
    frame: ArenaRunnerView.Question,
    component: ArenaRunnerRootComponent,
) {
    val enabled =
        !frame.busy && frame.foreground && frame.token.phase == ArenaRunnerPhase.ANSWERING &&
            !frame.question.showExitConfirmDialog
    val question = frame.question
    val input =
        QuestionInteraction(
            readDraft = { question.currentDraft },
            changeDraft = { if (enabled) component.onArenaAction(frame.token, ArenaRunAction.Draft(it)) },
            // SingleChoice submits exactly one Answer CAS, not Draft followed by Answer.
            selectAnswer = {},
            hint =
                HintControl(
                    enabled =
                        enabled && question.revealCorrect &&
                            isHintAvailable(
                                question.questionUiState,
                                question.isHard,
                                question.lives,
                                frame.feedback != null,
                            ),
                    spend = { false },
                    deferredSubmit = { if (enabled) component.onArenaAction(frame.token, ArenaRunAction.Hint(it)) },
                ),
            feedback = { if (enabled) component.onArenaAction(frame.token, ArenaRunAction.Answer(it.answer)) },
        )
    // Persisted frame is the only source of feedback. Hide input semantics during writes and saved feedback.
    Box(if (enabled) Modifier else Modifier.clearAndSetSemantics {}) {
        QuestionTypeContent(
            question.questionUiState,
            question.currentDraft,
            frame.feedback,
            question.revealCorrect && question.questionUiState.hasLocalAnswerKey,
            input,
        )
    }
}

@Composable
private fun ArenaDialogs(
    frame: ArenaRunnerView.Question,
    component: ArenaRunnerRootComponent,
    showInfo: Boolean,
    dismissInfo: () -> Unit,
) {
    val info = frame.question.questionUiState.info
    if (showInfo && info != null) QuestionInfoDialog(info, dismissInfo)
    if (!frame.busy && frame.question.showExitConfirmDialog) {
        ExitConfirmDialog(component::onCrossConfirmed, component::onCrossCancelled)
    } else if (!frame.busy && frame.token.phase == ArenaRunnerPhase.PAUSED) {
        BlockingResumeDialog(
            onContinue = { component.onArenaAction(frame.token, ArenaRunAction.Continue) },
            onExit = component::onCrossButtonTap,
        )
    }
}

/** A frozen receipt projection: no current lesson, local score, rating writer, or inferred reward. */
@Composable
private fun ArenaResult(
    frame: ArenaRunnerView.Result,
    component: ArenaRunnerRootComponent,
) {
    val settlement = frame.settlement
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.arena_run_result_title), style = NoirType.groupTitle)
        Text(stringResource(settlementStatusText(settlement)), style = NoirType.rowSub)
        if (settlement.isConfirmedCompletion) {
            val percent = requireNotNull(settlement.percentScore)
            val code = requireNotNull(settlement.codeAnswer)
            val reward = requireNotNull(settlement.reward)
            Text(stringResource(R.string.arena_run_result_percent, percent.raw), style = NoirType.price)
            Text(stringResource(R.string.arena_run_result_code, code.raw), style = NoirType.num)
            Text(
                stringResource(R.string.arena_run_result_reward_skill, reward.skillPoints),
                style = NoirType.rowSub,
                color = NoirT2,
            )
            Text(
                stringResource(R.string.arena_run_result_reward_nolics, reward.nolics),
                style = NoirType.rowSub,
                color = NoirT2,
            )
            Text(
                stringResource(R.string.arena_run_result_life_cost, requireNotNull(settlement.lifePointsSpent)),
                style = NoirType.rowSub,
                color = NoirT2,
            )
            Text(
                stringResource(R.string.arena_run_result_plasma_cost, requireNotNull(settlement.plasmaPointsSpent)),
                style = NoirType.rowSub,
                color = NoirT2,
            )
            ArenaRating(frame, component)
        }
        if (frame.canSync) {
            NoirButton(
                text = stringResource(R.string.arena_run_sync),
                onClick = component::onSyncResult,
                style = NoirButtonStyle.Ghost,
                fillWidth = false,
            )
        }
        NoirButton(
            text = stringResource(R.string.arena_run_back_to_tickets),
            onClick = component::onBack,
            enabled = !frame.rating.submitting,
            style = NoirButtonStyle.Ghost,
            fillWidth = false,
        )
    }
}

@Composable
private fun ArenaRating(
    frame: ArenaRunnerView.Result,
    component: ArenaRunnerRootComponent,
) {
    val rating = frame.rating
    when (val local = rating.local) {
        ArenaFrozenRatingState.Pending -> {
            Text(stringResource(R.string.arena_run_rating_choose), style = NoirType.rowSub)
            for (value in ARENA_RATING_VALUES) {
                NoirButton(
                    text = stringResource(ratingOptionResource(value)),
                    onClick = { component.onArenaSubmitRating(frame, value) },
                    enabled = rating.canSubmit,
                    style = NoirButtonStyle.Ghost,
                    fillWidth = false,
                )
            }
        }
        is ArenaFrozenRatingState.Queued -> {
            Text(stringResource(R.string.arena_run_rating_queued, local.rating), style = NoirType.rowSub)
            NoirButton(
                text = stringResource(R.string.arena_run_rating_sync),
                onClick = { component.onArenaRequestRatingSync(frame) },
                enabled = rating.canSync,
                style = NoirButtonStyle.Ghost,
                fillWidth = false,
            )
        }
        is ArenaFrozenRatingState.Accepted ->
            Text(stringResource(R.string.arena_run_rating_accepted, local.rating), style = NoirType.rowSub)
        ArenaFrozenRatingState.ExistingUnverified ->
            Text(stringResource(R.string.arena_run_rating_existing_unverified), style = NoirType.rowSub)
        ArenaFrozenRatingState.Unavailable ->
            Text(stringResource(R.string.arena_run_rating_unavailable), style = NoirType.rowSub)
    }
    if (rating.submitting) CircularProgressIndicator(color = LocalNoirAccent.current)
    if (rating.submitFailed) Text(stringResource(R.string.arena_run_rating_submit_failed), style = NoirType.rowSub)
}

private fun ratingOptionResource(value: Int): Int =
    when (value) {
        RATING_ONE -> R.string.arena_run_rating_option_1
        RATING_TWO -> R.string.arena_run_rating_option_2
        RATING_THREE -> R.string.arena_run_rating_option_3
        else -> error("Arena rating must be between 1 and 3")
    }

@Composable
private fun ArenaAttention(
    frame: ArenaRunnerView.Attention,
    component: ArenaRunnerRootComponent,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(frame.status.messageResource()), style = NoirType.rowSub)
        if (frame.busy) CircularProgressIndicator(color = LocalNoirAccent.current)
        if (frame.canReload) {
            NoirButton(
                text = stringResource(R.string.arena_run_reload),
                onClick = component::onArenaReload,
                style = NoirButtonStyle.Ghost,
                fillWidth = false,
            )
        }
        if (!frame.busy && frame.status in SYNC_STATUSES) {
            NoirButton(
                text = stringResource(R.string.arena_run_sync),
                onClick = component::onSyncResult,
                style = NoirButtonStyle.Ghost,
                fillWidth = false,
            )
        }
        NoirButton(
            text = stringResource(R.string.arena_run_back_to_tickets),
            onClick = component::onBack,
            enabled = !frame.busy,
            style = NoirButtonStyle.Ghost,
            fillWidth = false,
        )
    }
}

private fun ArenaRunnerStatus.messageResource(): Int =
    when (this) {
        ArenaRunnerStatus.LOADING -> R.string.arena_run_loading
        ArenaRunnerStatus.ACCOUNT_CHANGED -> R.string.arena_run_account_changed
        ArenaRunnerStatus.PREPARED_REQUIRES_EXPLICIT_START -> R.string.arena_run_prepared
        ArenaRunnerStatus.LOCAL_ABSENT -> R.string.arena_run_local_absent
        ArenaRunnerStatus.NEEDS_RECOVERY -> R.string.arena_run_needs_recovery
        ArenaRunnerStatus.LOCAL_FAILURE -> R.string.arena_run_local_failure
        ArenaRunnerStatus.COMPLETING -> R.string.arena_run_completing
        ArenaRunnerStatus.PENDING_COMPLETION -> R.string.arena_run_pending_completion
        ArenaRunnerStatus.PENDING_CANCEL -> R.string.arena_run_pending_cancel
        ArenaRunnerStatus.CANCELLED -> R.string.arena_run_cancelled
        ArenaRunnerStatus.SETTLED_AWAITING_RECEIPT -> R.string.arena_run_receipt_needed
    }

private const val FEEDBACK_ARM_MS = 250L
private const val RATING_ONE = 1
private const val RATING_TWO = 2
private const val RATING_THREE = 3
private val ARENA_RATING_VALUES = RATING_ONE..RATING_THREE
internal val SYNC_STATUSES =
    setOf(
        ArenaRunnerStatus.PENDING_COMPLETION,
        ArenaRunnerStatus.PENDING_CANCEL,
        ArenaRunnerStatus.SETTLED_AWAITING_RECEIPT,
    )
// Ручной Send предлагается только статусам, у которых в локальном журнале ещё живёт незакрытая
// мутация (DecisionPending SETTLE/CANCEL — она читается как PENDING_COMPLETION/PENDING_CANCEL,
// в том числе запаркованная согласием заявка). NEEDS_RECOVERY — нечитаемый журнал: Send там
// ничего не отправляет (ретро L3), чинит карточку Reload, а не повтор.
