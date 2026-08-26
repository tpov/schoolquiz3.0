package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnStop
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toEventSaveError
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUiReason
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUserAnswer
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState.RatingSubmissionState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.GetResultAdviceUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

@Suppress("LongParameterList")
class DefaultLessonRunnerRootComponent(
    componentContext: ComponentContext,
    private val lessonId: LessonId,
    private val mode: Difficulty,
    private val sessionMode: SessionMode = SessionMode.LEARNING,
    private val useCases: LessonRunnerUseCases,
    private val lessonRepository: LessonRepository,
    private val attemptRepository: LessonAttemptRepository,
    /**
     * Read once per completed round for the lives figure on the result card. Null (the default)
     * when the host has no profile to read — the figure stays hidden instead of guessing.
     */
    private val profileRepository: ProfileRepository? = null,
    private val getResultAdvice: GetResultAdviceUseCase,
    private val clock: Clock,
    mainContext: CoroutineContext = kotlinx.coroutines.Dispatchers.Main.immediate,
) : ComponentContext by componentContext, LessonRunnerRootComponent {
    private val stateHolder =
        instanceKeeper.getOrCreate("runner_state_holder") {
            RunnerStateHolder(mainContext)
        }

    private val scope get() = stateHolder.scope

    override val isHardMode: Boolean = mode == Difficulty.HARD

    override val uiState: StateFlow<RunnerUiState> = stateHolder.uiState.asStateFlow()

    private val _events = Channel<RunnerEvent>(capacity = Channel.BUFFERED)
    override val events: Flow<RunnerEvent> = _events.receiveAsFlow()

    init {
        // doOnDestroy sets the marker so the next component init (config change) can detect
        // that the pause triggered in doOnStop was spurious. InstanceKeeper.onDestroy() clears
        // the marker on real navigation pop / process death, so it's only true for rotation.
        lifecycle.doOnDestroy {
            _events.close()
            stateHolder.pendingConfigChangeRestore = true
        }
        lifecycle.doOnStop {
            val state = stateHolder.domainState
            if (state is RunnerState.Ready && !state.isPaused &&
                state.indexInPool < state.playOrder.size
            ) {
                stateHolder.snapshotBeforeStop = Pair(state, stateHolder.uiState.value)
            }
            handlePause()
        }

        if (stateHolder.pendingConfigChangeRestore) {
            // Config change: undo the auto-pause triggered by the old component's doOnStop.
            stateHolder.pendingConfigChangeRestore = false
            val snapshot = stateHolder.snapshotBeforeStop
            if (snapshot != null) {
                stateHolder.domainState = snapshot.first
                stateHolder.uiState.value = snapshot.second
                stateHolder.snapshotBeforeStop = null
            }
        } else if (stateHolder.uiState.value is RunnerUiState.Loading) {
            scope.launch { triggerStart() }
        }
    }

    override fun onDraftChanged(draft: UserAnswerDraft) {
        stateHolder.currentDraftAnswer = draft
        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
        val updatedDomain = domainState.copy(currentDraftAnswer = draft)
        stateHolder.domainState = updatedDomain
        stateHolder.uiState.value =
            updatedDomain.toQuestionUiState(stateHolder.livesRemainingHearts)
    }

    override fun onAnswer(answer: UserAnswerDraft) {
        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
        if (domainState.indexInPool >= domainState.playOrder.size) return
        stateHolder.currentDraftAnswer = null
        val userAnswer = answer.toUserAnswer()
        val nowMs = clock.now().toEpochMilliseconds()
        val newState = submitAnswer(domainState, userAnswer, nowMs)
        stateHolder.domainState = newState
        if (newState.indexInPool >= newState.playOrder.size) {
            scope.launch { triggerComplete(newState) }
        } else {
            stateHolder.uiState.value =
                newState.toQuestionUiState(stateHolder.livesRemainingHearts)
        }
    }

    override fun onTimeout() {
        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
        if (domainState.indexInPool >= domainState.playOrder.size) return
        val nowMs = clock.now().toEpochMilliseconds()
        val newState = autoAnswerOnTimeout(domainState, domainState.seed, nowMs)
        stateHolder.domainState = newState
        if (newState.indexInPool >= newState.playOrder.size) {
            scope.launch { triggerComplete(newState) }
        } else {
            stateHolder.uiState.value =
                newState.toQuestionUiState(stateHolder.livesRemainingHearts)
        }
    }

    override fun onContinue() {
        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
        if (!domainState.isPaused) return
        // Reset deadline from now so the paused-during-background timer doesn't start expired.
        val nowMs = clock.now().toEpochMilliseconds()
        val currentQuestion = domainState.playOrder[domainState.indexInPool]
        val duration = computeTimer(currentQuestion.content, domainState.mode, TimerCoefficients.Default)
        val resumedState =
            domainState.copy(
                isPaused = false,
                deadlineMs = nowMs + duration.seconds * 1000L,
            )
        stateHolder.domainState = resumedState
        stateHolder.uiState.value =
            resumedState.toQuestionUiState(stateHolder.livesRemainingHearts)
    }

    override fun onExit() {
        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
        scope.launch { triggerAbort(domainState) }
    }

    override fun onCrossButtonTap() {
        val current = stateHolder.uiState.value
        if (current is RunnerUiState.Question) {
            stateHolder.uiState.value = current.copy(showExitConfirmDialog = true)
        }
    }

    override fun onCrossConfirmed() {
        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
        scope.launch { triggerAbort(domainState) }
    }

    override fun onCrossCancelled() {
        val current = stateHolder.uiState.value
        if (current is RunnerUiState.Question) {
            stateHolder.uiState.value = current.copy(showExitConfirmDialog = false)
        }
    }

    override fun onSubmitRating(rating: Int) {
        val current = stateHolder.uiState.value as? RunnerUiState.Result ?: return
        val domainState = stateHolder.domainState
        val userId =
            when (domainState) {
                is RunnerState.Completed -> domainState.attempt.userId
                is RunnerState.Aborted -> domainState.attempt.userId
                else -> return
            }
        stateHolder.uiState.value = current.copy(ratingSubmissionState = RatingSubmissionState.InProgress)
        scope.launch {
            val result = useCases.submitRating(userId, lessonId, rating)
            val latest = stateHolder.uiState.value as? RunnerUiState.Result ?: return@launch
            if (result.isSuccess) {
                stateHolder.uiState.value =
                    latest.copy(
                        ratingSubmissionState = RatingSubmissionState.Done,
                        showRatingPrompt = false,
                    )
            } else {
                emitEvent(RunnerEvent.SaveRatingFailed)
                stateHolder.uiState.value = latest.copy(ratingSubmissionState = RatingSubmissionState.Failed)
            }
        }
    }

    override fun onFinish() {
        emitEvent(RunnerEvent.NavigateBack)
    }

    override fun onRunAgain() {
        stateHolder.resetForRestart()
        scope.launch { triggerStart() }
    }

    override fun onNextLesson() {
        scope.launch {
            val nextLessonId = resolveNextLessonId()
            if (nextLessonId != null) {
                emitEvent(RunnerEvent.OpenNextLesson(nextLessonId))
            } else {
                emitEvent(RunnerEvent.NavigateBack)
            }
        }
    }

    @Suppress("ReturnCount")
    override fun hintRequested(): Boolean {
        val current = stateHolder.uiState.value as? RunnerUiState.Question ?: return false
        val remaining = current.lives ?: return false
        if (remaining <= 0) return false
        val updated = remaining - 1
        stateHolder.livesRemainingHearts = updated
        stateHolder.uiState.value = current.copy(lives = updated)
        return true
    }

    override fun onBack() {
        emitEvent(RunnerEvent.NavigateBack)
    }

    /** First lesson of the same theme ordered after the current one, if it is synced locally. */
    private suspend fun resolveNextLessonId(): String? {
        val lesson = lessonRepository.getById(lessonId) ?: return null
        return lessonRepository
            .observeByTheme(lesson.themeId)
            .first()
            .firstOrNull { it.order > lesson.order }
            ?.id
            ?.value
    }

    @Suppress("ReturnCount")
    private fun handlePause() {
        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
        if (domainState.isPaused) return
        if (domainState.indexInPool >= domainState.playOrder.size) return
        val nowMs = clock.now().toEpochMilliseconds()
        val newState = autoAnswerOnTimeout(domainState, domainState.seed, nowMs)
        if (newState.indexInPool >= newState.playOrder.size) {
            scope.launch { triggerComplete(newState) }
        } else {
            val pausedState = newState.copy(isPaused = true)
            stateHolder.domainState = pausedState
            stateHolder.uiState.value =
                pausedState.toQuestionUiState(stateHolder.livesRemainingHearts)
        }
    }

    private suspend fun triggerStart() {
        val result = useCases.startAttempt(lessonId, mode, sessionMode)
        stateHolder.domainState = result
        stateHolder.uiState.value =
            when (result) {
                is RunnerState.Ready -> {
                    if (stateHolder.livesRemainingHearts == null) {
                        stateHolder.livesRemainingHearts = readLivesFromProfile()
                    }
                    result.toQuestionUiState(stateHolder.livesRemainingHearts)
                }
                is RunnerState.InitFailed -> RunnerUiState.InitFailed(result.reason.toUiReason())
                else -> RunnerUiState.Loading
            }
    }

    /**
     * Hearts the profile can currently pay for. Null when there is no profile to read — the UI
     * then hides the pill and disables the hint instead of inventing a budget.
     */
    private suspend fun readLivesFromProfile(): Int? =
        profileRepository
            ?.observeCurrentProfile()
            ?.firstOrNull()
            ?.let { it.lifePoints / UserProfile.LIFE_POINTS_PER_HEART }

    private suspend fun triggerComplete(readyState: RunnerState.Ready) {
        val result = useCases.completeAttempt(readyState)
        stateHolder.domainState = result
        when (result) {
            is RunnerState.Completed -> {
                stateHolder.uiState.value =
                    buildResultUiState(result.attempt, result.ratingPrompt, saveWarning = false)
            }
            is RunnerState.SaveFailed -> {
                emitEvent(RunnerEvent.SaveAttemptFailed(result.error.toEventSaveError()))
                stateHolder.uiState.value =
                    buildResultUiState(
                        result.attempt,
                        showRatingPrompt = false,
                        saveWarning = true,
                    )
            }
            else -> {}
        }
    }

    private suspend fun triggerAbort(readyState: RunnerState.Ready) {
        val result = useCases.abortAttempt(readyState)
        stateHolder.domainState = result
        when (result) {
            is RunnerState.SaveFailed -> {
                emitEvent(RunnerEvent.SaveAttemptFailed(result.error.toEventSaveError()))
                stateHolder.uiState.value =
                    buildResultUiState(
                        result.attempt,
                        showRatingPrompt = false,
                        saveWarning = true,
                    )
            }
            else -> emitEvent(RunnerEvent.NavigateBack)
        }
    }

    private suspend fun buildResultUiState(
        attempt: Attempt,
        showRatingPrompt: Boolean,
        saveWarning: Boolean,
    ): RunnerUiState.Result {
        val lesson = lessonRepository.getById(attempt.lessonId)
        val userAttempts = attemptRepository.observeByLesson(attempt.userId, attempt.lessonId).first()
        val userAttemptCount = userAttempts.size
        val userAveragePercentScore =
            if (userAttempts.isEmpty()) {
                0
            } else {
                userAttempts.sumOf { it.percentScore.raw } / userAttempts.size
            }
        val previousBestPercentScore = userAttempts.maxOfOrNull { it.percentScore.raw } ?: 0
        val userBestPercentScore = maxOf(previousBestPercentScore, attempt.percentScore.raw)
        val profile = profileRepository?.observeCurrentProfile()?.first()
        return RunnerUiState.Result(
            percentScore = attempt.percentScore,
            mode = attempt.mode,
            completedAt = attempt.completedAt,
            hardUnlocked = computeHardUnlocked(userAttempts),
            bestStarsRawTenths = computeBestStars(userAttempts).rawTenths,
            currentAttemptStarsRawTenths = computeStars(attempt.percentScore, attempt.mode).rawTenths,
            lessonAverageRating = lesson?.averageRating,
            lessonRatingCount = lesson?.ratingCount ?: 0,
            top3 = lesson?.top3 ?: emptyList(),
            userAttemptCount = userAttemptCount,
            userAveragePercentScore = userAveragePercentScore,
            userBestPercentScore = userBestPercentScore,
            advice = getResultAdvice(attempt),
            questionScores = attempt.codeAnswer.raw.map { it - '0' },
            livesRemainingHearts = profile?.let { it.lifePoints / UserProfile.LIFE_POINTS_PER_HEART },
            livesMaxHearts = profile?.let { it.maxLifePoints / UserProfile.LIFE_POINTS_PER_HEART },
            showRatingPrompt = showRatingPrompt,
            saveWarning = saveWarning,
        )
    }

    private fun emitEvent(event: RunnerEvent) {
        _events.trySend(event)
    }
}
