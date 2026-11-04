package com.tpov.schoolquiz.android.feature.internet.social.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.internet.social.presentation.R
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollAuthorDraft
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollAuthorQuestion
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollsUiMessage
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollsUiState
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.internet.social.data.outbox.PollOutboxRepository
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionDraft
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesDraft
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteCommand
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsIdProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.GetPollQuestionStateUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.ListPollSeriesUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.PublishPollSeriesUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.SubmitPollVoteUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Живое состояние поверхности опросов.
 *
 * Голос после подтверждения едет очередью (AD-6/AD-7): экран показывает «отправлено, ждёт
 * подтверждения» и перечитывает вью, пока сервер не ответит собственным принятым голосом —
 * подтверждение живёт на сервере, локальных догадок о квитанции экран не рисует (AD-8/AD-21).
 * То же с публикацией серии: список перечитывается, пока серия не появится из вью.
 *
 * Замок «отправлено» и отказ наблюдаются из очереди ([PollOutboxRepository], ретро эпика 8,
 * находка 7), а не только из памяти: ждущий ряд держит честную пометку «не отправлено»
 * (UX-O7 §4) и переживает смерть процесса, терминальный — снимает замок и называет отказ.
 * Карточка открывает первый неотвеченный вопрос (находка 8): надпись и навигация говорят одно.
 */
class DefaultPollsComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val listSeries: ListPollSeriesUseCase,
    private val questionState: GetPollQuestionStateUseCase,
    private val submitVote: SubmitPollVoteUseCase,
    private val publishSeries: PublishPollSeriesUseCase,
    private val outboxSync: PollOutboxRepository,
    private val idProvider: PollsIdProvider,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PollsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(PollsUiState())
    override val state: StateFlow<PollsUiState> = _state.asStateFlow()

    private var currentUid: String? = null
    private var confirmationPoll: Job? = null
    private var syncObserver: Job? = null
    private var observedUid: String? = null
    private var observedIds: List<String> = emptyList()
    private var lastSyncSnapshot: List<OutboxEntitySync> = emptyList()

    init {
        lifecycle.doOnDestroy {
            componentJob.cancel()
            confirmationPoll?.cancel()
            syncObserver?.cancel()
        }
        observeAuth()
    }

    override fun onRefreshClick() {
        if (_state.value.isQuestionOpen) {
            openQuestion(_state.value.openSeriesId!!, _state.value.questionIndex)
        } else {
            reloadSeries()
        }
    }

    // ── участник ──

    override fun onOpenSeriesClick(seriesId: String) {
        confirmationPoll?.cancel()
        // Находка 8: карточка обещает продолжение с первого неотвеченного — навигация открывает
        // ровно его (answeredCount вопросов уже отвечено), а не вопрос 1. Пустая сводка (0 вопросов)
        // открывает вопрос 1 — верхняя граница не становится отрицательной.
        val entry = _state.value.series.firstOrNull { it.seriesId == seriesId }
        val maxIndex = (entry?.questionCount ?: 1).coerceAtLeast(1) - 1
        val resumeIndex = (entry?.answeredCount ?: 0).coerceIn(0, maxIndex)
        _state.update {
            it.copy(
                openSeriesId = seriesId,
                questionIndex = resumeIndex,
                questionState = null,
                questionError = null,
                selectedOptionIds = emptySet(),
                voteQueued = false,
            )
        }
        openQuestion(seriesId, resumeIndex)
        restartOutboxObservation()
    }

    override fun onToggleOptionClick(optionId: String) {
        val state = _state.value
        val mode = state.questionState?.question?.mode ?: return
        _state.update { current ->
            val selected =
                if (mode == PollMode.SINGLE) {
                    setOf(optionId)
                } else if (optionId in current.selectedOptionIds) {
                    current.selectedOptionIds - optionId
                } else {
                    current.selectedOptionIds + optionId
                }
            current.copy(selectedOptionIds = selected)
        }
    }

    override fun onConfirmVoteClick() {
        val state = _state.value
        val uid = currentUid ?: return
        val snapshot = state.questionState ?: return
        val selected = state.selectedOptionIds.toList()
        if (selected.isEmpty() || state.voteQueued) return
        scope.launch {
            val result =
                submitVote(
                    ownerUid = uid,
                    command =
                        PollVoteCommand(
                            seriesId = snapshot.seriesId,
                            revisionId = snapshot.revisionId,
                            questionIndex = snapshot.questionIndex,
                            selectedOptionIds = selected,
                        ),
                )
            result.fold(
                onSuccess = {
                    _state.update { it.copy(voteQueued = true) }
                    startConfirmationPoll()
                },
                onFailure = { error ->
                    _state.update { it.copy(questionError = messageOf(error)) }
                },
            )
        }
    }

    override fun onNextQuestionClick() {
        val state = _state.value
        val snapshot = state.questionState ?: return
        val next = state.questionIndex + 1
        if (next >= snapshot.questionCount) return
        confirmationPoll?.cancel()
        _state.update {
            it.copy(
                questionIndex = next,
                questionState = null,
                selectedOptionIds = emptySet(),
                voteQueued = false,
                questionError = null,
            )
        }
        openQuestion(state.openSeriesId!!, next)
    }

    override fun onCloseSeriesClick() {
        confirmationPoll?.cancel()
        _state.update {
            it.copy(
                openSeriesId = null,
                questionState = null,
                selectedOptionIds = emptySet(),
                voteQueued = false,
                questionError = null,
            )
        }
        restartOutboxObservation()
        reloadSeries()
    }

    // ── автор ──

    override fun onCreateSeriesClick() {
        _state.update { it.copy(authoring = PollAuthorDraft()) }
    }

    override fun onAuthorTitleChange(title: String) {
        _state.update {
            val draft = it.authoring ?: return@update it
            it.copy(authoring = draft.copy(title = title))
        }
    }

    override fun onAuthorAddQuestionClick() {
        _state.update {
            val draft = it.authoring ?: return@update it
            if (draft.questions.size >= MAX_QUESTIONS) return@update it
            it.copy(authoring = draft.copy(questions = draft.questions + PollAuthorQuestion()))
        }
    }

    override fun onAuthorRemoveQuestionClick(questionIndex: Int) {
        _state.update {
            val draft = it.authoring ?: return@update it
            if (draft.questions.size <= 1) return@update it
            it.copy(
                authoring =
                    draft.copy(
                        questions =
                            draft.questions.filterIndexed {
                                    index,
                                    _,
                                ->
                                index != questionIndex
                            },
                    ),
            )
        }
    }

    override fun onAuthorQuestionTextChange(
        questionIndex: Int,
        text: String,
    ) {
        _state.update {
            val draft = it.authoring ?: return@update it
            it.copy(
                authoring =
                    draft.copy(
                        questions =
                            draft.questions.mapIndexed { index, question ->
                                if (index == questionIndex) question.copy(text = text) else question
                            },
                    ),
            )
        }
    }

    override fun onAuthorModeChange(
        questionIndex: Int,
        mode: PollMode,
    ) {
        _state.update {
            val draft = it.authoring ?: return@update it
            it.copy(
                authoring =
                    draft.copy(
                        questions =
                            draft.questions.mapIndexed { index, question ->
                                if (index == questionIndex) question.copy(mode = mode) else question
                            },
                    ),
            )
        }
    }

    override fun onAuthorAddOptionClick(questionIndex: Int) {
        _state.update {
            val draft = it.authoring ?: return@update it
            it.copy(
                authoring =
                    draft.copy(
                        questions =
                            draft.questions.mapIndexed { index, question ->
                                if (index == questionIndex && question.optionTexts.size < MAX_OPTIONS) {
                                    question.copy(optionTexts = question.optionTexts + "")
                                } else {
                                    question
                                }
                            },
                    ),
            )
        }
    }

    override fun onAuthorRemoveOptionClick(
        questionIndex: Int,
        optionIndex: Int,
    ) {
        _state.update {
            val draft = it.authoring ?: return@update it
            it.copy(
                authoring =
                    draft.copy(
                        questions =
                            draft.questions.mapIndexed { index, question ->
                                if (index == questionIndex && question.optionTexts.size > MIN_OPTIONS) {
                                    question.copy(
                                        optionTexts =
                                            question.optionTexts.filterIndexed {
                                                    option,
                                                    _,
                                                ->
                                                option != optionIndex
                                            },
                                    )
                                } else {
                                    question
                                }
                            },
                    ),
            )
        }
    }

    override fun onAuthorOptionTextChange(
        questionIndex: Int,
        optionIndex: Int,
        text: String,
    ) {
        _state.update {
            val draft = it.authoring ?: return@update it
            it.copy(
                authoring =
                    draft.copy(
                        questions =
                            draft.questions.mapIndexed { index, question ->
                                if (index == questionIndex) {
                                    question.copy(
                                        optionTexts =
                                            question.optionTexts.mapIndexed { option, value ->
                                                if (option == optionIndex) text else value
                                            },
                                    )
                                } else {
                                    question
                                }
                            },
                    ),
            )
        }
    }

    override fun onPublishClick() {
        val state = _state.value
        val uid = currentUid ?: return
        val draft = state.authoring ?: return
        if (state.publishQueued) return
        val seriesId = idProvider.nextId("poll").take(SERIES_ID_MAX_LENGTH)
        scope.launch {
            val result =
                publishSeries(
                    ownerUid = uid,
                    seriesId = seriesId,
                    draft =
                        PollSeriesDraft(
                            title = draft.title,
                            questions =
                                draft.questions.map { question ->
                                    PollQuestionDraft(
                                        text = question.text,
                                        mode = question.mode,
                                        optionTexts = question.optionTexts,
                                    )
                                },
                        ),
                    expectedRevisionId = 0,
                )
            result.fold(
                onSuccess = {
                    _state.update { it.copy(publishQueued = true) }
                    startPublishPoll(seriesId)
                },
                onFailure = { error ->
                    _state.update { it.copy(errorMessage = messageOf(error)) }
                },
            )
        }
    }

    override fun onAuthorCancelClick() {
        _state.update { it.copy(authoring = null, publishQueued = false, errorMessage = null) }
    }

    // ── механика ──

    private fun observeAuth() {
        scope.launch {
            authRepository.observeUid().collect { uid ->
                currentUid = uid
                if (uid.isNullOrBlank()) {
                    _state.update {
                        PollsUiState(errorMessage = PollsUiMessage.Res(R.string.polls_error_auth_not_ready))
                    }
                } else {
                    reloadSeries()
                }
            }
        }
    }

    private fun reloadSeries() {
        if (currentUid.isNullOrBlank()) return
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = listSeries()
            _state.update { state ->
                result.fold(
                    onSuccess = { entries ->
                        state.copy(isLoading = false, errorMessage = null, series = entries)
                    },
                    onFailure = { error ->
                        state.copy(isLoading = false, errorMessage = messageOf(error))
                    },
                )
            }
            restartOutboxObservation()
        }
    }

    /**
     * Наблюдение очереди открытой серии и списка (находка 7, UX-O7 §4): ждущий ряд держит
     * честную пометку «не отправлено», терминальный (карантин/конфликт) снимает замок и называет
     * отказ, отсутствие ряда снимает замок без ошибки — квитанцию приносит вью. Перезапуск — по
     * смене владельца или набора серий (ряды очереди owner-скоупны): по каждой ссылке живёт свой
     * поток, ссылки собирает фича (AD-7). Если набор не сменился, но открылась другая серия,
     * известный срез очереди пере-применяется сразу — замок встаёт из факта, не ждёт эмита.
     */
    private fun restartOutboxObservation() {
        val uid = currentUid
        if (uid == null) {
            // Нет владельца (вышли из аккаунта) — наблюдение прекращается и не оставляет
            // чужих пометок: ряды очереди owner-скоупны.
            syncObserver?.cancel()
            syncObserver = null
            observedIds = emptyList()
            lastSyncSnapshot = emptyList()
            _state.update { it.copy(seriesSync = emptyMap()) }
            return
        }
        val ids =
            (_state.value.series.map { it.seriesId } + listOfNotNull(_state.value.openSeriesId))
                .distinct()
                .sorted()
        if (uid == observedUid && ids == observedIds) {
            // Перезапуск не нужен, но открытая серия сменилась: срез очереди уже известен —
            // пере-применяем его, чтобы замок открытой серии встал из факта очереди, а не ждал
            // следующего эмита потока.
            applyOutboxSync(ids.zip(lastSyncSnapshot).toMap())
            return
        }
        observedUid = uid
        observedIds = ids
        syncObserver?.cancel()
        if (ids.isEmpty()) {
            lastSyncSnapshot = emptyList()
            _state.update { it.copy(seriesSync = emptyMap()) }
            return
        }
        syncObserver =
            scope.launch {
                outboxSync.observe(uid, ids).collect { syncs ->
                    lastSyncSnapshot = syncs
                    applyOutboxSync(ids.zip(syncs).toMap())
                }
            }
    }

    private fun applyOutboxSync(byId: Map<String, OutboxEntitySync>) {
        _state.update { state ->
            var updated = state.copy(seriesSync = byId)
            when (val sync = state.openSeriesId?.let(byId::get)) {
                is OutboxEntitySync.Queued ->
                    when {
                        sync.state == OutboxState.QUARANTINED -> {
                            updated =
                                updated.copy(
                                    voteQueued = false,
                                    publishQueued = false,
                                    questionError = PollsUiMessage.Res(R.string.polls_send_quarantined),
                                )
                        }

                        sync.state == OutboxState.CONFLICT -> {
                            updated =
                                updated.copy(
                                    voteQueued = false,
                                    publishQueued = false,
                                    questionError = PollsUiMessage.Res(R.string.polls_send_conflict),
                                )
                        }

                        // Ряд в очереди — «не отправлено» честно, даже если процесс
                        // перезапускался: пометка из факта очереди, не из памяти (находка 7).
                        // Своё принятое голосо (§5) сильнее: подтверждённое не изображается
                        // ждущим.
                        sync.state.isPending && updated.questionState?.ownVote == null ->
                            updated = updated.copy(voteQueued = true)

                        else -> Unit
                    }

                OutboxEntitySync.None ->
                    if (updated.voteQueued) updated = updated.copy(voteQueued = false)

                else -> Unit
            }
            updated
        }
    }

    private fun openQuestion(
        seriesId: String,
        questionIndex: Int,
    ) {
        scope.launch {
            _state.update { it.copy(questionLoading = true, questionError = null) }
            val result = questionState(seriesId, questionIndex)
            _state.update { state ->
                result.fold(
                    onSuccess = { snapshot ->
                        state.copy(
                            questionLoading = false,
                            questionError = null,
                            questionState = snapshot,
                            selectedOptionIds = snapshot.ownVote?.selectedOptionIds?.toSet() ?: state.selectedOptionIds,
                            voteQueued = snapshot.ownVote == null && state.voteQueued,
                        )
                    },
                    onFailure = { error ->
                        state.copy(questionLoading = false, questionError = messageOf(error))
                    },
                )
            }
        }
    }

    /**
     * Подтверждение голоса живёт на сервере: пока вью не отвечает собственным принятым голосом,
     * экран перечитывает её с коротким шагом (ограничено), чтобы распределение появилось само
     * после дренажа очереди. Перезачитывание — чтение, эффекта оно не создаёт.
     */
    private fun startConfirmationPoll() {
        confirmationPoll?.cancel()
        confirmationPoll =
            scope.launch {
                repeat(CONFIRMATION_POLLS) {
                    delay(POLL_STEP_MS)
                    val seriesId = _state.value.openSeriesId ?: return@launch
                    val index = _state.value.questionIndex
                    val result = questionState(seriesId, index)
                    result.getOrNull()?.let { snapshot ->
                        val ownVote = snapshot.ownVote
                        if (ownVote != null) {
                            _state.update {
                                it.copy(
                                    questionState = snapshot,
                                    voteQueued = false,
                                    questionError = null,
                                    selectedOptionIds = ownVote.selectedOptionIds.toSet(),
                                )
                            }
                            return@launch
                        }
                    }
                }
            }
    }

    private fun startPublishPoll(seriesId: String) {
        scope.launch {
            repeat(PUBLISH_POLLS) {
                delay(POLL_STEP_MS)
                val result = listSeries()
                result.getOrNull()?.let { entries ->
                    if (entries.any { it.seriesId == seriesId }) {
                        _state.update {
                            it.copy(series = entries, isLoading = false, publishQueued = false, authoring = null)
                        }
                        restartOutboxObservation()
                        return@launch
                    }
                }
            }
        }
    }

    private fun messageOf(error: Throwable): PollsUiMessage =
        error.syncErrorOrNull()?.let { PollsUiMessage.Raw(it.toString()) }
            ?: PollsUiMessage.Res(R.string.polls_error_unknown)

    private companion object {
        const val MAX_QUESTIONS = 50
        const val MAX_OPTIONS = 8
        const val MIN_OPTIONS = 2
        const val SERIES_ID_MAX_LENGTH = 64
        const val CONFIRMATION_POLLS = 10
        const val PUBLISH_POLLS = 10
        const val POLL_STEP_MS = 1_000L
    }
}
