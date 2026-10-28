package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import androidx.annotation.StringRes
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsMarkStateUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsPreviewOptionUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsQuestionPreviewUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsSurfaceUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalRow
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionVerdict
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionsAccessRefusedException
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.DecideQuestProposalUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchQuestProposalsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchReviewDecisionsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.MarkReviewDecisionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Вкладка «Решения» (история 4.17, UX-22): страница строк — фильтр «Без отметки / Верно /
 * Неверно», детали с «Логами» записи, статическим предпросмотром вопроса и отметкой ✓/✗
 * с необязательным комментарием («Отправляется…» → «Сохранено»).
 *
 * Право — серверное: отказ роли переключает вкладку на авторскую сторону (только «Предложения»,
 * пустую до историй 4.22+). Предпросмотр разбирает payload вопроса общим парсером схемы; битый
 * payload — явная пометка, а не выдуманный вопрос.
 */
@Suppress("LongParameterList")
class DefaultDecisionsComponent(
    componentContext: ComponentContext,
    private val fetchDecisions: FetchReviewDecisionsUseCase,
    private val markDecision: MarkReviewDecisionUseCase,
    private val questionContentParser: QuestionContentParser,
    private val fetchProposals: FetchQuestProposalsUseCase? = null,
    private val decideProposal: DecideQuestProposalUseCase? = null,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : DecisionsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(DecisionsUiState())
    private var loadJob: Job? = null
    private var markJob: Job? = null
    private var entries: List<ReviewDecision> = emptyList()
    private var nextCursor: ReviewDecisionCursor? = null

    override val state: StateFlow<DecisionsUiState> = _state.asStateFlow()

    private var proposalsCursor: String? = null
    private val proposalsList = mutableListOf<QuestProposalRow>()
    private var proposalsLoadJob: Job? = null
    private var proposalDecisionJob: Job? = null

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        reload(ReviewDecisionFilter.UNMARKED)
        // «Предложения» — общая половина вкладки (UX-22): админ видит её переключателем,
        // не-админ после отказа роли — целиком. Страница прав ограничена, предзагрузка дешёвая.
        reloadProposals()
    }

    override fun onSurfaceSelected(surface: DecisionsSurfaceUi) {
        if (surface == _state.value.surface) return
        _state.update {
            it.copy(
                surface = surface,
                proposalsDetail = null,
                detail = null,
                errorMessage = null,
                proposalsErrorMessage = null,
            )
        }
        if (surface == DecisionsSurfaceUi.PROPOSALS && _state.value.proposals.isEmpty() &&
            !_state.value.proposalsLoading
        ) {
            reloadProposals()
        }
    }

    override fun onProposalsBoxSelected(box: QuestProposalBox) {
        if (box == _state.value.proposalsBox) return
        _state.update { it.copy(proposalsBox = box, proposalsDetail = null, proposalsErrorMessage = null) }
        reloadProposals()
    }

    override fun onLoadMoreProposalsClick() {
        val current = _state.value
        if (!current.proposalsHasMore || current.proposalsLoadingMore) return
        loadProposalsPage(append = true)
    }

    override fun onProposalSelected(proposalId: String) {
        val entry = proposalsList.firstOrNull { it.proposalId == proposalId } ?: return
        _state.update { it.copy(proposalsDetail = entry, proposalsErrorMessage = null) }
    }

    override fun onBackToProposalsListClick() {
        _state.update { it.copy(proposalsDetail = null, proposalsErrorMessage = null) }
    }

    override fun onProposalDecision(approve: Boolean) {
        val detail = _state.value.proposalsDetail ?: return
        val decide = decideProposal ?: return
        if (detail.stale || detail.unreadable) return
        if (detail.status != "CHECK_PASSED") return
        // Решение одноразовое: повтор по решённой записи — не второй ход (приёмник 4.21).
        if (detail.decision != null) return
        if (_state.value.proposalDecisionState == DecisionsMarkStateUi.SAVING) return
        _state.update { it.copy(proposalDecisionState = DecisionsMarkStateUi.SAVING, proposalsErrorMessage = null) }
        val commentAtSend = _state.value.proposalCommentDraft
        proposalDecisionJob?.cancel()
        proposalDecisionJob =
            scope.launch {
                val outcome = decide(detail.proposalId, approve, commentAtSend)
                outcome.fold(
                    onSuccess = {
                        // Решение одноразовое: перечитываем полку, чтобы статус пришёл сервером.
                        _state.update { it.copy(proposalDecisionState = DecisionsMarkStateUi.SAVED) }
                        reloadProposals()
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                proposalDecisionState = DecisionsMarkStateUi.NONE,
                                proposalsErrorMessage = UiMessage.Raw(error.message ?: "decision failed"),
                            )
                        }
                    },
                )
            }
    }

    override fun onProposalCommentChanged(value: String) {
        _state.update { it.copy(proposalCommentDraft = value) }
    }

    private fun reloadProposals() {
        proposalsCursor = null
        proposalsList.clear()
        _state.update { it.copy(proposals = emptyList(), proposalsHasMore = false, proposalsLoading = true) }
        loadProposalsPage(append = false)
    }

    private fun loadProposalsPage(append: Boolean) {
        val fetch = fetchProposals
        if (fetch == null) {
            _state.update { it.copy(proposalsLoading = false, proposalsLoadingMore = false) }
            return
        }
        proposalsLoadJob?.cancel()
        proposalsLoadJob =
            scope.launch {
                val box = _state.value.proposalsBox
                try {
                    val result = fetch(box, PROPOSALS_PAGE_LIMIT, if (append) proposalsCursor else null)
                    result.fold(
                        onSuccess = { page ->
                            if (!append) proposalsList.clear()
                            proposalsList.addAll(page.rows)
                            proposalsCursor = page.nextCursor
                            _state.update {
                                it.copy(
                                    proposals = proposalsList.toList(),
                                    proposalsHasMore = page.hasMore,
                                    proposalsLoading = false,
                                    proposalsLoadingMore = false,
                                )
                            }
                        },
                        onFailure = { error ->
                            _state.update {
                                it.copy(
                                    proposalsLoading = false,
                                    proposalsLoadingMore = false,
                                    proposalsErrorMessage = UiMessage.Raw(error.message ?: "proposals load failed"),
                                )
                            }
                        },
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                }
            }
    }

    override fun onFilterSelected(filter: ReviewDecisionFilter) {
        if (filter == _state.value.selectedFilter) return
        reload(filter)
    }

    override fun onLoadMoreClick() {
        val current = _state.value
        if (!current.hasMore || current.loadingMore) return
        loadPage(current.selectedFilter, append = true)
    }

    override fun onEntrySelected(
        verdictId: String,
        questionId: String,
    ) {
        val entry = entries.firstOrNull { it.verdictId == verdictId && it.questionId == questionId } ?: return
        _state.update {
            it.copy(
                detail = entry.toDetailState(questionContentParser),
                errorMessage = null,
            )
        }
    }

    override fun onBackToListClick() {
        _state.update { it.copy(detail = null, errorMessage = null) }
    }

    override fun onMarkSelected(mark: ReviewDecisionMarkValue) {
        val detail = _state.value.detail ?: return
        if (detail.unreadable) return
        if (detail.markState == DecisionsMarkStateUi.SAVING) return
        _state.update { state ->
            state.copy(
                detail = state.detail?.copy(markValue = mark, markState = DecisionsMarkStateUi.SAVING),
                errorMessage = null,
            )
        }
        val commentAtSend = detail.commentDraft
        val previousValue = detail.markValue
        markJob?.cancel()
        markJob =
            scope.launch {
                val saved = markDecision(detail.verdictId, detail.questionId, mark, commentAtSend)
                saved.fold(
                    onSuccess = { result ->
                        entries =
                            entries.map { entry ->
                                if (entry.verdictId == detail.verdictId && entry.questionId == detail.questionId) {
                                    entry.copy(mark = result)
                                } else {
                                    entry
                                }
                            }
                        _state.update { state ->
                            state.copy(
                                detail =
                                    state.detail?.copy(
                                        markValue = result.value,
                                        markComment = result.comment,
                                        markState = DecisionsMarkStateUi.SAVED,
                                    ),
                                entries = entries.toListItems(),
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update { state ->
                            state.copy(
                                detail =
                                    state.detail?.copy(
                                        markValue = previousValue,
                                        markState = DecisionsMarkStateUi.NONE,
                                    ),
                                errorMessage = UiMessageFactory.markFailed(),
                            )
                        }
                    },
                )
            }
    }

    override fun onCommentChanged(value: String) {
        _state.update { state -> state.copy(detail = state.detail?.copy(commentDraft = value.take(COMMENT_MAX_CHARS))) }
    }

    private fun reload(filter: ReviewDecisionFilter) {
        _state.value = DecisionsUiState(isLoading = true, selectedFilter = filter)
        loadPage(filter, append = false)
    }

    private fun loadPage(
        filter: ReviewDecisionFilter,
        append: Boolean,
    ) {
        loadJob?.cancel()
        loadJob =
            scope.launch {
                _state.update { if (append) it.copy(loadingMore = true) else it.copy(isLoading = true) }
                val cursor = if (append) nextCursor else null
                // Отказ роли — не сбой канала: транспорт бросает доменное исключение, сценарий
                // превращает его в обычный failure, и вкладка уходит на авторскую сторону.
                val page: Result<ReviewDecisionPage> =
                    try {
                        fetchDecisions(filter, cursor)
                    } catch (error: ReviewDecisionsAccessRefusedException) {
                        Result.failure(error)
                    }
                page.fold(
                    onSuccess = { result ->
                        if (append) {
                            entries = entries + result.entries
                        } else {
                            entries = result.entries
                        }
                        nextCursor = result.nextCursor
                        _state.update { state ->
                            state.copy(
                                isLoading = false,
                                loadingMore = false,
                                entries = entries.toListItems(),
                                hasMore = result.hasMore,
                                errorMessage = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is ReviewDecisionsAccessRefusedException) {
                            _state.value =
                                DecisionsUiState(
                                    isLoading = false,
                                    accessRefused = true,
                                    surface = DecisionsSurfaceUi.PROPOSALS,
                                    selectedFilter = filter,
                                )
                        } else {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    loadingMore = false,
                                    errorMessage = UiMessageFactory.loadFailed(),
                                )
                            }
                        }
                    },
                )
            }
    }

    private fun List<ReviewDecision>.toListItems(): List<DecisionsListItemUiState> =
        map { entry ->
            DecisionsListItemUiState(
                verdictId = entry.verdictId,
                questionId = entry.questionId,
                unreadable = entry.unreadable,
                questTitle = entry.questTitle,
                lessonTitle = entry.lessonTitle,
                stage = entry.stage,
                questionText = entry.question?.text ?: entry.reason,
                verdictPass = entry.verdict?.let { it == ReviewDecisionVerdict.PASS },
                markValue = entry.mark?.value,
            )
        }

    private fun ReviewDecision.toDetailState(parser: QuestionContentParser): DecisionsDetailUiState =
        DecisionsDetailUiState(
            verdictId = verdictId,
            questionId = questionId,
            unreadable = unreadable,
            questTitle = questTitle,
            lessonTitle = lessonTitle,
            stage = stage,
            attempt = attempt,
            revision = revision,
            model = model,
            promptVersion = promptVersion,
            rationale = rationale,
            sources = sources,
            verdictPass = verdict?.let { it == ReviewDecisionVerdict.PASS },
            reason = reason,
            preview = question?.let { it.toPreviewState(parser) },
            markValue = mark?.value,
            markComment = mark?.comment.orEmpty(),
            commentDraft = mark?.comment.orEmpty(),
            markState = if (mark == null) DecisionsMarkStateUi.NONE else DecisionsMarkStateUi.SAVED,
        )

    private fun ReviewDecisionQuestion.toPreviewState(parser: QuestionContentParser): DecisionsQuestionPreviewUiState {
        val parsed =
            parser.parse(payload).getOrNull()
                ?: return DecisionsQuestionPreviewUiState(
                    text = text,
                    options = emptyList(),
                    answerKeyLine = null,
                    parseFailed = true,
                )
        return when (parsed) {
            is QuestionContent.Survey ->
                DecisionsQuestionPreviewUiState(
                    text = parsed.text,
                    options = parsed.options.map { DecisionsPreviewOptionUiState(it.text, correct = false) },
                    answerKeyLine = null,
                    parseFailed = false,
                )
            is QuestionContent.SingleChoice ->
                DecisionsQuestionPreviewUiState(
                    text = parsed.text,
                    options =
                        parsed.options.map {
                            DecisionsPreviewOptionUiState(
                                it.text,
                                it.id == parsed.correctOptionId,
                            )
                        },
                    answerKeyLine = parsed.options.firstOrNull { it.id == parsed.correctOptionId }?.text,
                    parseFailed = false,
                )
            is QuestionContent.MultipleChoice ->
                DecisionsQuestionPreviewUiState(
                    text = parsed.text,
                    options =
                        parsed.options.map {
                            DecisionsPreviewOptionUiState(
                                it.text,
                                it.id in parsed.correctOptionIds,
                            )
                        },
                    answerKeyLine =
                        parsed.options.filter { it.id in parsed.correctOptionIds }.joinToString(
                            "; ",
                        ) { it.text },
                    parseFailed = false,
                )
            is QuestionContent.Ordering ->
                DecisionsQuestionPreviewUiState(
                    text = parsed.text,
                    options = parsed.items.map { DecisionsPreviewOptionUiState(it.text, correct = true) },
                    answerKeyLine = parsed.items.joinToString(" → ") { it.text },
                    parseFailed = false,
                )
            is QuestionContent.FillBlank ->
                DecisionsQuestionPreviewUiState(
                    text = parsed.text,
                    options =
                        parsed.candidates.map { candidate ->
                            DecisionsPreviewOptionUiState(
                                candidate.text,
                                correct = parsed.blanks.any { it.correctCandidateId == candidate.id },
                            )
                        },
                    answerKeyLine =
                        parsed.blanks.mapNotNull { blank ->
                            parsed.candidates.firstOrNull { it.id == blank.correctCandidateId }?.text
                        }.joinToString("; "),
                    parseFailed = false,
                )
        }
    }

    private companion object {
        /** Граница страницы «Предложений» — серверной вью quest-proposals-v1. */
        private const val PROPOSALS_PAGE_LIMIT = 20
        const val COMMENT_MAX_CHARS = 2000
    }
}

/** Экранные ошибки: сырые тексты сервера не показываются — только свои строки. */
private object UiMessageFactory {
    fun loadFailed(): UiMessage = message(R.string.qa_decisions_load_failed)

    fun markFailed(): UiMessage = message(R.string.qa_decisions_mark_failed)

    private fun message(
        @StringRes id: Int,
    ): UiMessage = UiMessage.Res(id)
}
