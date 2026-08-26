package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class RunFakeComponent(
    private val _uiState: MutableStateFlow<RunnerUiState>,
    private val _events: Channel<RunnerEvent> = Channel(Channel.BUFFERED),
    override val isHardMode: Boolean = false,
) : LessonRunnerRootComponent {

    override val uiState: StateFlow<RunnerUiState> = _uiState
    override val events: Flow<RunnerEvent> = _events.receiveAsFlow()

    var lastAnswer: UserAnswerDraft? = null
        private set
    var lastDraft: UserAnswerDraft? = null
        private set
    var lastRating: Int? = null
        private set
    var continueCount: Int = 0
        private set
    var exitCount: Int = 0
        private set
    var crossButtonTapCount: Int = 0
        private set
    var crossConfirmedCount: Int = 0
        private set
    var crossCancelledCount: Int = 0
        private set
    var timeoutCount: Int = 0
        private set
    var finishCount: Int = 0
        private set
    var backCount: Int = 0
        private set
    var runAgainCount: Int = 0
        private set
    var nextLessonCount: Int = 0
        private set
    var hintCount: Int = 0
        private set

    override fun onDraftChanged(draft: UserAnswerDraft) {
        lastDraft = draft
    }

    override fun onAnswer(answer: UserAnswerDraft) {
        lastAnswer = answer
    }

    override fun onTimeout() {
        timeoutCount++
    }

    override fun onContinue() {
        continueCount++
    }

    override fun onExit() {
        exitCount++
    }

    override fun onCrossButtonTap() {
        crossButtonTapCount++
    }

    override fun onCrossConfirmed() {
        crossConfirmedCount++
    }

    override fun onCrossCancelled() {
        crossCancelledCount++
    }

    override fun onSubmitRating(rating: Int) {
        lastRating = rating
    }

    override fun onFinish() {
        finishCount++
    }

    override fun onRunAgain() {
        runAgainCount++
    }

    override fun onNextLesson() {
        nextLessonCount++
    }

    override fun hintRequested(): Boolean {
        hintCount++
        return true
    }

    override fun onBack() {
        backCount++
    }

    fun emit(event: RunnerEvent) {
        _events.trySend(event)
    }

    fun setState(state: RunnerUiState) {
        _uiState.value = state
    }
}
