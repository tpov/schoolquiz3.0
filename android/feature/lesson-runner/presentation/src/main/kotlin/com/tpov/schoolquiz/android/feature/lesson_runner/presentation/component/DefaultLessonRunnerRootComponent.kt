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
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsEvent
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.analytics.NoOpAnalyticsTracker
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.computeStars
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.claimCharge
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.identity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.AttemptSyncMarkRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonCommentRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.GetResultAdviceUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

private const val DEFAULT_AUTHOR = "Аноним"

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
     * Ответ очереди про это прохождение (AD-14). Без значения по умолчанию намеренно: забытая
     * проводка обязана падать в графе, а не тихо показывать «всё отправлено».
     */
    private val attemptSyncMarks: AttemptSyncMarkRepository,
    private val attemptSettlements: ServerAttemptSettlementRepository,
    private val syncScheduler: SyncScheduler,
    /**
     * Read once per completed round for the lives figure on the result card. Null (the default)
     * when the host has no profile to read — the figure stays hidden instead of guessing.
     */
    private val profileRepository: ProfileRepository? = null,
    private val commentRepository: LessonCommentRepository? = null,
    private val getResultAdvice: GetResultAdviceUseCase,
    private val clock: Clock,
    /**
     * Funnel instrumentation. Defaulted to a no-op so tests and any host that does not care about
     * measurement construct this component unchanged.
     */
    private val analytics: AnalyticsTracker = NoOpAnalyticsTracker,
    mainContext: CoroutineContext = kotlinx.coroutines.Dispatchers.Main.immediate,
) : ComponentContext by componentContext, LessonRunnerRootComponent {
    private val stateHolder =
        instanceKeeper.getOrCreate("runner_state_holder") {
            RunnerStateHolder(mainContext)
        }

    private val scope get() = stateHolder.scope

    override val isHardMode: Boolean = mode == Difficulty.HARD

    override val uiState: StateFlow<RunnerUiState> = stateHolder.uiState.asStateFlow()

    override val comments: StateFlow<List<LessonComment>> =
        (commentRepository?.observe(lessonId) ?: flowOf(emptyList()))
            .stateIn(scope, SharingStarted.Lazily, emptyList())

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

    override fun onSyncResult() {
        val result = stateHolder.uiState.value as? RunnerUiState.Result ?: return
        if (result.canRequestSync) syncScheduler.enqueueManualSync()
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
        if (!current.canSubmitRating || current.ratingSubmissionState == RatingSubmissionState.InProgress) return
        val domainState = stateHolder.domainState
        val completedAttempt =
            when (domainState) {
                is RunnerState.Completed -> domainState.attempt.identity()
                is RunnerState.PendingCompletion -> domainState.intent.identity
                else -> return
            }
        stateHolder.uiState.value = current.copy(ratingSubmissionState = RatingSubmissionState.InProgress)
        scope.launch {
            val result =
                useCases.submitRating(
                    completedAttempt.userId,
                    completedAttempt.lessonId,
                    completedAttempt.id,
                    rating,
                )
            val latest = stateHolder.uiState.value as? RunnerUiState.Result ?: return@launch
            if (latest.attemptId != completedAttempt.id) return@launch
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
        if (stateHolder.terminalSaveMutex.isLocked) return
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

    /**
     * Игрок тратит заряд, чтобы увидеть ответ.
     *
     * Локально уменьшается только показанное число: сам заряд списывает сервер при отправке
     * результата (CAP-3), а здесь остаётся заявка — отметка в маске против позиции этого вопроса.
     * Без неё трата не доезжала бы вовсе: до сих пор она жила в памяти экрана и исчезала вместе с
     * ним.
     *
     * Заявка ставится только на лёгкой попытке. Подсказку покупает обычный заряд, а на сложном
     * вопросе тратится плазменный — и он не показывает ответ, а пропускает вопрос (CAP-1). Пока
     * такого управления нет, сложная попытка ведёт себя как прежде и не тарифицируется: заявить
     * пропуск там, где ответ был показан, значит соврать серверу.
     */
    @Suppress("ReturnCount")
    override fun hintRequested(): Boolean {
        val current = stateHolder.uiState.value as? RunnerUiState.Question ?: return false
        val remaining = current.lives ?: return false
        if (remaining <= 0) return false
        val ready = stateHolder.domainState as? RunnerState.Ready ?: return false
        if (ready.mode != Difficulty.EASY || ready.codeAnswer == null ||
            !current.questionUiState.hasLocalAnswerKey
        ) {
            return false
        }
        stateHolder.domainState = claimCharge(ready, ChargeClaimMask.Claim.STANDARD_HINT)
        val updated = remaining - 1
        stateHolder.livesRemainingHearts = updated
        stateHolder.uiState.value = current.copy(lives = updated)
        return true
    }

    override fun onBack() {
        emitEvent(RunnerEvent.NavigateBack)
    }

    override fun onPostComment(text: String) {
        val repository = commentRepository ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val profile = profileRepository?.observeCurrentProfile()?.firstOrNull()
            repository.post(
                lessonId = lessonId,
                authorNickname = profile?.nickname ?: DEFAULT_AUTHOR,
                authorAvatarUrl = profile?.avatarUrl,
                text = trimmed,
            )
        }
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
                    // Fired on Ready rather than on the call, so a lesson that failed to open is
                    // not counted as one that started — the drop-off between the two is the point.
                    analytics.track(
                        AnalyticsEvent.LessonStarted(
                            lessonId = lessonId.value,
                            difficulty = mode.name,
                        ),
                    )
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

    private suspend fun withTerminalSave(
        readyState: RunnerState.Ready,
        save: suspend () -> Unit,
    ) {
        // Do not queue a competing Complete/Abort to run after the first terminal write.
        if (!stateHolder.terminalSaveMutex.tryLock()) return
        try {
            val current = stateHolder.domainState as? RunnerState.Ready ?: return
            if (current.attemptId != readyState.attemptId) return
            save()
        } finally {
            // Cancellation propagates; a cancelled save must never leave the retained gate locked.
            stateHolder.terminalSaveMutex.unlock()
        }
    }

    private suspend fun triggerComplete(readyState: RunnerState.Ready) =
        withTerminalSave(readyState) {
            val result = useCases.completeAttempt(readyState)
            stateHolder.domainState = result
            when (result) {
                is RunnerState.Completed -> {
                    analytics.track(
                        AnalyticsEvent.LessonFinished(
                            lessonId = lessonId.value,
                            difficulty = mode.name,
                            percent = result.attempt.percentScore.raw,
                            stars = computeStars(result.attempt.percentScore, result.attempt.mode).rawTenths,
                        ),
                    )
                    stateHolder.uiState.value =
                        buildResultUiState(result.attempt, result.ratingPrompt, saveWarning = false)
                    observeSyncMark(result.attempt.identity())
                    observeServerSettlement(result.attempt.identity())
                }
                is RunnerState.SaveFailed -> {
                    emitEvent(RunnerEvent.SaveAttemptFailed(result.error.toEventSaveError()))
                    stateHolder.uiState.value =
                        buildResultUiState(
                            result.attempt,
                            showRatingPrompt = false,
                            saveWarning = true,
                        )
                    observeSyncMark(result.attempt.identity())
                    observeServerSettlement(result.attempt.identity())
                }
                is RunnerState.PendingCompletion -> {
                    // No LessonFinished score telemetry until an authoritative result exists.
                    stateHolder.uiState.value =
                        buildUnscoredResultUiState(
                            result.intent,
                            showRatingPrompt = true,
                            saveWarning = false,
                        )
                    observeSyncMark(result.intent.identity)
                    observeServerSettlement(result.intent.identity)
                }
                is RunnerState.PendingSaveFailed -> {
                    showUnscoredSaveFailure(result)
                }
                else -> {}
            }
        }

    private suspend fun triggerAbort(readyState: RunnerState.Ready) =
        withTerminalSave(readyState) {
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
                    observeSyncMark(result.attempt.identity())
                    observeServerSettlement(result.attempt.identity())
                }
                is RunnerState.PendingSaveFailed -> showUnscoredSaveFailure(result)
                else -> emitEvent(RunnerEvent.NavigateBack)
            }
        }

    private suspend fun showUnscoredSaveFailure(result: RunnerState.PendingSaveFailed) {
        emitEvent(RunnerEvent.SaveAttemptFailed(result.error.toEventSaveError()))
        stateHolder.uiState.value =
            buildUnscoredResultUiState(
                result.intent,
                showRatingPrompt = false,
                saveWarning = true,
            )
        observeSyncMark(result.intent.identity)
        observeServerSettlement(result.intent.identity)
    }

    private suspend fun buildUnscoredResultUiState(
        intent: UnscoredTerminalIntent,
        showRatingPrompt: Boolean,
        saveWarning: Boolean,
    ): RunnerUiState.Result {
        val identity = intent.identity
        val lesson = lessonRepository.getById(identity.lessonId)
        val history = attemptRepository.observeByLesson(identity.userId, identity.lessonId).first()
        val profile = profileRepository?.observeCurrentProfile()?.first()
        return RunnerUiState.Result(
            attemptId = identity.id,
            percentScore = null,
            mode = identity.mode,
            completedAt = intent.endedAtMs,
            hardUnlocked = computeHardUnlocked(history),
            bestStarsRawTenths = computeBestStars(history).rawTenths,
            currentAttemptStarsRawTenths = null,
            lessonAverageRating = lesson?.averageRating,
            lessonRatingCount = lesson?.ratingCount ?: 0,
            top3 = lesson?.top3.orEmpty(),
            userAttemptCount = history.size,
            userAveragePercentScore =
                history.takeIf { it.isNotEmpty() }?.let {
                    it.sumOf {
                            row ->
                        row.percentScore.raw
                    } / it.size
                },
            userBestPercentScore = history.maxOfOrNull { it.percentScore.raw },
            livesRemainingHearts = profile?.let { it.lifePoints / UserProfile.LIFE_POINTS_PER_HEART },
            livesMaxHearts = profile?.let { it.maxLifePoints / UserProfile.LIFE_POINTS_PER_HEART },
            showRatingPrompt = showRatingPrompt,
            saveWarning = saveWarning,
        )
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
            attemptId = attempt.id,
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

    /**
     * Подписка на пометку очереди для показанного прохождения.
     *
     * Спрашивается у ядра по ссылке на сущность; сама очередь этой фичей не читается (AD-7).
     * Подписка живёт, пока на экране результат: карантин может случиться уже после того, как
     * игрок увидел карточку, и тогда пометка меняется на месте.
     */
    private fun observeSyncMark(attempt: AttemptIdentity) {
        stateHolder.syncMarkJob?.cancel()
        stateHolder.syncMarkJob =
            scope.launch {
                attemptSyncMarks.observeAttemptMark(attempt.id).collect { mark ->
                    val current = stateHolder.uiState.value as? RunnerUiState.Result ?: return@collect
                    if (current.attemptId != attempt.id) return@collect
                    if (current.syncMark != mark) {
                        stateHolder.uiState.value = current.copy(syncMark = mark)
                    }
                }
            }
    }

    private fun observeServerSettlement(attempt: AttemptIdentity) {
        stateHolder.settlementJob?.cancel()
        stateHolder.settlementJob =
            scope.launch {
                attemptSettlements.observe(attempt).collect { settlement ->
                    val current = stateHolder.uiState.value as? RunnerUiState.Result ?: return@collect
                    if (current.attemptId != attempt.id) return@collect
                    stateHolder.uiState.value = current.copy(serverSettlement = settlement)
                }
            }
    }

    private fun emitEvent(event: RunnerEvent) {
        _events.trySend(event)
    }
}
