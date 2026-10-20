package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.AnswerFeedback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.flow.StateFlow

/** Internal extension consumed by the shared screen; navigation still uses LessonRunnerRootComponent. */
internal interface ArenaRunnerRootComponent : LessonRunnerRootComponent {

    override val needsTermsConsent: kotlinx.coroutines.flow.StateFlow<Boolean>

    override fun acceptTerms()
    val arenaView: StateFlow<ArenaRunnerView>

    fun onArenaAction(
        token: ArenaRunToken,
        action: ArenaRunAction,
    )

    fun onArenaReload()

    fun onArenaForeground()

    fun onArenaBackground(changingConfigurations: Boolean)

    fun onArenaRefreshWallet()

    fun onArenaSubmitRating(
        frame: ArenaRunnerView.Result,
        rating: Int,
    )

    fun onArenaRequestRatingSync(frame: ArenaRunnerView.Result)
}

internal sealed interface ArenaRunnerView {
    data class Question(
        val token: ArenaRunToken,
        val question: RunnerUiState.Question,
        val feedback: AnswerFeedback?,
        val busy: Boolean,
        val foreground: Boolean,
    ) : ArenaRunnerView

    /**
     * Frozen-ticket result only. This deliberately does not adapt an ordinary [RunnerUiState.Result],
     * because the normal result fields need a live lesson hierarchy which an Arena ticket must not read.
     */
    data class Result(
        val stamp: ArenaSessionStamp,
        val header: ArenaTicketHeader,
        val attempt: AttemptIdentity,
        val settlement: ServerAttemptSettlement,
        /** True only while the frozen SETTLE command remains retryable and its receipt is unconfirmed. */
        val canSync: Boolean,
        val rating: ArenaRunRatingState = ArenaRunRatingState(),
    ) : ArenaRunnerView

    data class Attention(
        val status: ArenaRunnerStatus,
        val busy: Boolean = false,
        val canReload: Boolean = false,
    ) : ArenaRunnerView
}

internal enum class ArenaRunnerStatus {
    LOADING,
    ACCOUNT_CHANGED,
    PREPARED_REQUIRES_EXPLICIT_START,
    LOCAL_ABSENT,
    NEEDS_RECOVERY,
    LOCAL_FAILURE,
    COMPLETING,
    PENDING_COMPLETION,
    PENDING_CANCEL,
    CANCELLED,
    SETTLED_AWAITING_RECEIPT,
}
