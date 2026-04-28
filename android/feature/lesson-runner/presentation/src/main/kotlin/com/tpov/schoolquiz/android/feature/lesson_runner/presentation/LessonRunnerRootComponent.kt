package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface LessonRunnerRootComponent {
    val uiState: StateFlow<RunnerUiState>
    val events: Flow<RunnerEvent>

    /** True when this runner session is in HARD mode — used for FLAG_SECURE (AC-28). */
    val isHardMode: Boolean

    fun onAnswer(answer: UserAnswerDraft)

    /** Called while user edits the current question — persists draft across rotation. */
    fun onDraftChanged(draft: UserAnswerDraft)

    fun onTimeout()

    fun onContinue()

    fun onExit()

    fun onCrossButtonTap()

    fun onCrossConfirmed()

    fun onCrossCancelled()

    fun onSubmitRating(rating: Int)

    fun onFinish()

    fun onBack()
}
