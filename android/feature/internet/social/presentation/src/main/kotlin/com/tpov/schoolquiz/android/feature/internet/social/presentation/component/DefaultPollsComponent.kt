package com.tpov.schoolquiz.android.feature.internet.social.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.internet.social.presentation.R
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollAuthorDraft
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollAuthorQuestion
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollsUiMessage
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollsUiState
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
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
 */
class DefaultPollsComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val listSeries: ListPollSeriesUseCase,
    private val questionState: GetPollQuestionStateUseCase,
    private val submitVote: SubmitPollVoteUseCase,
    private val publishSeries: PublishPollSeriesUseCase,
    private val idProvider: PollsIdProvider,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PollsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(PollsUiState())
    override val state: StateFlow<PollsUiState> = _state.asStateFlow()

    private var currentUid: String? = null
    private var confirmationPoll: Job? = null

    init {
        lifecycle.doOnDestroy {
            componentJob.cancel()
            confirmationPoll?.cancel()
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
        _state.update {
            it.copy(
                openSeriesId = seriesId,
                questionIndex = 0,
                questionState = null,
                questionError = null,
                selectedOptionIds = emptySet(),
                voteQueued = false,
            )
        }
        openQuestion(seriesId, 0)
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
