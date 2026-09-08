package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.AnswerFeedback
import kotlinx.coroutines.flow.StateFlow

/** Internal extension consumed by the shared screen; navigation still uses LessonRunnerRootComponent. */
internal interface ArenaRunnerRootComponent : LessonRunnerRootComponent {
    val arenaView: StateFlow<ArenaRunnerView>

    fun onArenaAction(
        token: ArenaRunToken,
        action: ArenaRunAction,
    )

    fun onArenaReload()

    fun onArenaForeground()

    fun onArenaBackground(changingConfigurations: Boolean)
}

internal sealed interface ArenaRunnerView {
    data class Question(
        val token: ArenaRunToken,
        val question: RunnerUiState.Question,
        val feedback: AnswerFeedback?,
        val busy: Boolean,
        val foreground: Boolean,
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
