package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentSyncUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewLanguagesUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQuestionUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueKindUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentDecisionUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentLabelKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentSyncState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestionScore
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecisionKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitLessonErrorReportCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentSyncUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitLessonErrorReportUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitReviewActionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Suppress("TooManyFunctions", "LongParameterList")
class DefaultReviewQueueComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val observeReviewAssignments: ObserveReviewAssignmentsUseCase,
    private val observeReviewAssignmentSync: ObserveReviewAssignmentSyncUseCase,
    private val submitReviewAction: SubmitReviewActionUseCase,
    private val submitLessonErrorReport: SubmitLessonErrorReportUseCase,
    private val questionContentParser: QuestionContentParser,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ReviewQueueComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(ReviewQueueUiState())
    private val json = Json { encodeDefaults = true }
    private var assignmentsJob: Job? = null
    private var assignments: List<ReviewAssignment> = emptyList()
    private var syncStates: Map<String, ReviewAssignmentSyncState> = emptyMap()

    override val state: StateFlow<ReviewQueueUiState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        observeAuth()
    }

    override fun onFilterMenuClick() {
        _state.update { it.copy(filterMenuExpanded = true) }
    }

    override fun onFilterMenuDismiss() {
        _state.update { it.copy(filterMenuExpanded = false) }
    }

    override fun onFilterSelected(filter: ReviewQueueFilter) {
        _state.update { state ->
            val selected = filter.takeIf { filter in state.availableFilters } ?: ReviewQueueFilter.ALL
            state.copy(
                selectedFilter = selected,
                filterMenuExpanded = false,
                detail = null,
                assignments = this@DefaultReviewQueueComponent.assignments.toListItems(selected, syncStates),
            )
        }
    }

    override fun onAssignmentSelected(id: String) {
        val assignment = assignments.firstOrNull { it.id == id } ?: return
        val detail = assignment.toDetail(_state.value.selectedFilter)
        _state.update { it.copy(detail = detail, errorMessage = null, successMessage = null) }
    }

    override fun onBackToListClick() {
        _state.update { it.copy(detail = null, errorMessage = null) }
    }

    override fun onScoreSelected(score: Int) {
        if (score !in MIN_REVIEW_SCORE..MAX_REVIEW_SCORE) return
        _state.update { state ->
            state.copy(detail = state.detail?.copy(selectedScore = score), errorMessage = null)
        }
    }

    override fun onLanguageSelected(language: String) {
        _state.update { state ->
            val detail = state.detail ?: return@update state
            val assignment = assignments.firstOrNull { it.id == detail.assignmentId } ?: return@update state
            state.copy(
                detail = assignment.toDetail(state.selectedFilter, forcedLanguage = language),
                errorMessage = null,
            )
        }
    }

    override fun onTranslationTextChanged(
        questionId: String,
        segmentKey: String,
        value: String,
    ) {
        _state.update { state ->
            state.copy(
                detail =
                    state.detail?.mapSegment(questionId, segmentKey) {
                        it.copy(translatedText = value)
                    },
                errorMessage = null,
            )
        }
    }

    override fun onQuestionScoreSelected(
        questionId: String,
        score: Int,
    ) {
        if (score !in MIN_REVIEW_SCORE..MAX_REVIEW_SCORE) return
        _state.update { state ->
            state.copy(
                detail = state.detail?.mapQuestion(questionId) { it.copy(score = score) },
                errorMessage = null,
            )
        }
    }

    override fun onSegmentDecided(
        questionId: String,
        segmentKey: String,
        decision: ReviewSegmentDecisionUi,
    ) {
        _state.update { state ->
            state.copy(
                detail =
                    state.detail?.mapSegment(questionId, segmentKey) {
                        it.copy(decision = decision)
                    },
                errorMessage = null,
            )
        }
    }

    /**
     * Решение уходит в очередь, а не в callable (AD-3, AD-6): ждать сети нечего, поэтому форма
     * закрывается тем же кадром, а спиннера «отправка» больше нет. Строка задания не меняется —
     * его исчезновение из списка принесёт журнал ревью (AD-25).
     */
    override fun onSubmitClick() {
        val detail = _state.value.detail ?: return
        val assignment = assignments.firstOrNull { it.id == detail.assignmentId } ?: return
        val command = detail.toSubmitCommand(assignment) ?: return
        _state.update {
            it.copy(
                detail = null,
                errorMessage = null,
                successMessage = UiMessage.Res(R.string.qa_review_queued),
            )
        }
        scope.launch {
            submitReviewAction(assignment.ownerUid, command).onFailure { error ->
                _state.update {
                    it.copy(
                        successMessage = null,
                        errorMessage =
                            error.message?.let(UiMessage::Raw)
                                ?: UiMessage.Res(R.string.qa_error_submit_review_failed),
                    )
                }
            }
        }
    }

    override fun onReportErrorRequested() {
        if (_state.value.detail == null) return
        _state.update { it.copy(reportDialogOpen = true, reportTextDraft = "", reportError = null) }
    }

    override fun onReportErrorDismissed() {
        // Пока отчёт ещё едет в очередь, диалог не бросают закрытием по неосторожности (AD-14).
        if (!_state.value.reportSubmitting) {
            _state.update { it.copy(reportDialogOpen = false, reportError = null) }
        }
    }

    override fun onReportErrorTextChanged(value: String) {
        _state.update { it.copy(reportTextDraft = value) }
    }

    override fun onReportErrorSubmit() {
        val detail = _state.value.detail ?: return
        val assignment = assignments.firstOrNull { it.id == detail.assignmentId } ?: return
        if (_state.value.reportSubmitting) return
        val text = _state.value.reportTextDraft.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(reportError = UiMessage.Res(R.string.qa_review_report_blank)) }
            return
        }
        _state.update { it.copy(reportSubmitting = true, reportError = null) }
        scope.launch {
            val result = submitLessonErrorReport(
                assignment.ownerUid,
                SubmitLessonErrorReportCommand(detail.lessonId, text),
            )
            _state.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        reportSubmitting = false,
                        reportDialogOpen = false,
                        reportTextDraft = "",
                        successMessage = UiMessage.Res(R.string.qa_review_report_done),
                    )
                } else {
                    state.copy(
                        reportSubmitting = false,
                        reportError = result.exceptionOrNull()?.message?.let(UiMessage::Raw)
                            ?: UiMessage.Res(R.string.qa_review_report_blank),
                    )
                }
            }
        }
    }

    private fun observeAuth() {
        scope.launch {
            authRepository.observeUid().collect { uid ->
                assignmentsJob?.cancel()
                if (uid.isNullOrBlank()) {
                    assignments = emptyList()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            assignments = emptyList(),
                            detail = null,
                            errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready),
                        )
                    }
                } else {
                    observeAssignments(uid)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAssignments(uid: String) {
        assignmentsJob =
            scope.launch {
                observeReviewAssignments(uid)
                    .flatMapLatest { loaded -> loaded.withSyncStates(uid) }
                    .catch { error ->
                        assignments = emptyList()
                        syncStates = emptyMap()
                        _state.update {
                            it.copy(
                                isLoading = false,
                                assignments = emptyList(),
                                detail = null,
                                errorMessage =
                                    error.message?.let(UiMessage::Raw)
                                        ?: UiMessage.Res(R.string.qa_error_load_assignments_failed),
                            )
                        }
                    }
                    .collect { (loaded, states) ->
                        assignments = loaded
                        syncStates = states
                        _state.update { current ->
                            val filters = loaded.availableFilters()
                            val selected =
                                current.selectedFilter.takeIf { it in filters }
                                    ?: ReviewQueueFilter.ALL
                            val currentDetail = current.detail
                            val selectedDetail =
                                currentDetail?.assignmentId
                                    ?.let { id -> loaded.firstOrNull { it.id == id } }
                                    ?.toDetail(selected, currentDetail.selectedLanguage)
                            current.copy(
                                isLoading = false,
                                availableFilters = filters,
                                selectedFilter = selected,
                                assignments = loaded.toListItems(selected, states),
                                detail = selectedDetail,
                                errorMessage = null,
                            )
                        }
                    }
            }
    }

    /**
     * Пометка очереди на каждое задание — запросом по `entity_ref`, а не чтением таблицы очереди
     * (AD-14). Список и пометки едут одним потоком, чтобы карточка не мигала между ними.
     */
    private fun List<ReviewAssignment>.withSyncStates(
        uid: String,
    ): Flow<Pair<List<ReviewAssignment>, Map<String, ReviewAssignmentSyncState>>> {
        if (isEmpty()) return flowOf(this to emptyMap())
        val perAssignment =
            map { assignment ->
                observeReviewAssignmentSync(uid, assignment.id).map { state -> assignment.id to state }
            }
        return combine(perAssignment) { pairs -> this to pairs.toMap() }
    }

    private fun ReviewAssignmentDetailUiState.toSubmitCommand(
        assignment: ReviewAssignment,
    ): SubmitReviewActionCommand? =
        when (kind) {
            ReviewQueueKindUi.TESTING, ReviewQueueKindUi.LOGIC -> {
                val score = selectedScore
                if (score == null) {
                    _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_choose_score)) }
                    return null
                }
                // TESTING судит каждый вопрос ревизии (4.6): неотмеченный вопрос не даёт подать.
                // LOGIC оценивает урок целиком — вопросных оценок у него нет.
                val questionScores =
                    if (kind == ReviewQueueKindUi.TESTING) {
                        questions.map { question ->
                            ReviewQuestionScore(
                                questionId = question.id,
                                score =
                                    question.score ?: run {
                                        _state.update {
                                            it.copy(errorMessage = UiMessage.Res(R.string.qa_error_score_all_questions))
                                        }
                                        return null
                                    },
                            )
                        }
                    } else {
                        emptyList()
                    }
                SubmitReviewActionCommand(
                    assignmentId = assignmentId,
                    lessonId = lessonId,
                    kind = kind.toDomainKind(),
                    score = score,
                    questionScores = questionScores,
                )
            }
            ReviewQueueKindUi.TRANSLATION -> {
                val language = selectedLanguage
                if (language.isNullOrBlank()) {
                    _state.update {
                        it.copy(errorMessage = UiMessage.Res(R.string.qa_error_choose_translation_language))
                    }
                    return null
                }
                if (questions.any { question -> question.segments.any { it.translatedText.isBlank() } }) {
                    _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_fill_all_translations)) }
                    return null
                }
                SubmitReviewActionCommand(
                    assignmentId = assignmentId,
                    lessonId = lessonId,
                    kind = ReviewAssignmentKind.TRANSLATION,
                    language = language,
                    translatedQuestions = translatedQuestions(assignment, language),
                )
            }
            ReviewQueueKindUi.TRANSLATION_REVIEW -> {
                val language = selectedLanguage
                if (language.isNullOrBlank()) {
                    _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_choose_review_language)) }
                    return null
                }
                val undecided =
                    questions.any { question -> question.segments.any { it.decision == null } }
                if (undecided) {
                    // Решения по сегментам явные и полные до подачи (CAP-3): unchecked не едет.
                    _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_decide_all_segments)) }
                    return null
                }
                SubmitReviewActionCommand(
                    assignmentId = assignmentId,
                    lessonId = lessonId,
                    kind = ReviewAssignmentKind.TRANSLATION_REVIEW,
                    language = language,
                    segmentResults =
                        questions.flatMap { question ->
                            question.segments.map {
                                ReviewSegmentDecision(
                                    questionId = question.id,
                                    segmentKey = it.key,
                                    decision =
                                        when (it.decision) {
                                            ReviewSegmentDecisionUi.CORRECT -> ReviewSegmentDecisionKind.CORRECT
                                            ReviewSegmentDecisionUi.ERROR -> ReviewSegmentDecisionKind.ERROR
                                            null -> return null
                                        },
                                )
                            }
                        },
                )
            }
        }

    private fun ReviewAssignmentDetailUiState.translatedQuestions(
        assignment: ReviewAssignment,
        language: String,
    ): List<ReviewQuestion> {
        val sourceById = assignment.sourceQuestions().associateBy { it.id }
        return questions.mapNotNull { question ->
            val source = sourceById[question.id] ?: return@mapNotNull null
            source.withTranslatedSegments(
                targetLanguage = language,
                translatedSegments =
                    question.segments.associate {
                        it.key to it.translatedText
                    },
            )
        }
    }

    private fun ReviewAssignment.toDetail(
        selectedFilter: ReviewQueueFilter,
        forcedLanguage: String? = null,
    ): ReviewAssignmentDetailUiState {
        val kind = chooseKind(selectedFilter)
        val languages = languagesFor(kind)
        val selectedLanguage = forcedLanguage?.takeIf { it in languages } ?: languages.firstOrNull()
        val questions =
            when (kind) {
                ReviewQueueKindUi.TESTING, ReviewQueueKindUi.LOGIC -> sourceQuestions().map { it.toQuestionUi() }
                ReviewQueueKindUi.TRANSLATION ->
                    sourceQuestions().map { it.toTranslationQuestionUi() }
                ReviewQueueKindUi.TRANSLATION_REVIEW ->
                    sourceQuestions().map { source ->
                        source.toTranslationReviewQuestionUi(targetQuestionFor(source, selectedLanguage))
                    }
            }
        return ReviewAssignmentDetailUiState(
            assignmentId = id,
            lessonId = lessonId,
            title = title,
            kind = kind,
            selectedLanguage = selectedLanguage,
            availableLanguages = languages,
            selectedScore = null,
            questions = questions,
        )
    }

    private fun ReviewAssignment.chooseKind(filter: ReviewQueueFilter): ReviewQueueKindUi {
        val kinds = taskKinds.mapNotNull { it.toUiKindOrNull() }.toSet()
        return when (filter) {
            ReviewQueueFilter.TESTING -> ReviewQueueKindUi.TESTING
            ReviewQueueFilter.LOGIC -> ReviewQueueKindUi.LOGIC
            ReviewQueueFilter.TRANSLATION ->
                if (ReviewQueueKindUi.TRANSLATION in kinds) {
                    ReviewQueueKindUi.TRANSLATION
                } else {
                    ReviewQueueKindUi.TRANSLATION_REVIEW
                }
            ReviewQueueFilter.TRANSLATION_REVIEW -> ReviewQueueKindUi.TRANSLATION_REVIEW
            ReviewQueueFilter.ALL ->
                REVIEW_KIND_PRIORITY.firstOrNull { it in kinds } ?: ReviewQueueKindUi.TESTING
        }
    }

    private fun ReviewAssignment.languagesFor(kind: ReviewQueueKindUi): List<String> =
        when (kind) {
            ReviewQueueKindUi.TRANSLATION -> newTranslationLanguages.sorted()
            ReviewQueueKindUi.TRANSLATION_REVIEW -> reviewLanguages.sorted()
            else -> emptyList()
        }

    private fun ReviewAssignment.sourceQuestions(): List<ReviewQuestion> {
        val source = sourceLanguages.ifEmpty { questions.mapTo(linkedSetOf()) { it.language } }
        return questions
            .filter { it.language in source }
            .sortedBy { it.order }
    }

    private fun ReviewAssignment.targetQuestionFor(
        source: ReviewQuestion,
        language: String?,
    ): ReviewQuestion? {
        if (language == null) return null
        return questions
            .filter { it.language == language }
            .firstOrNull { it.id == "${source.id}__$language" }
            ?: questions
                .filter { it.language == language }
                .firstOrNull { it.order == source.order }
    }

    private fun ReviewQuestion.toQuestionUi(): ReviewQuestionUiState =
        ReviewQuestionUiState(
            id = id,
            order = order,
            language = language,
            text = text,
        )

    private fun ReviewQuestion.toTranslationQuestionUi(): ReviewQuestionUiState =
        ReviewQuestionUiState(
            id = id,
            order = order,
            language = language,
            text = text,
            segments =
                extractSegments().map {
                    ReviewSegmentUiState(
                        questionId = id,
                        key = it.key,
                        labelKind = it.kind,
                        labelArg = it.arg,
                        sourceText = it.text,
                        translatedText = "",
                    )
                },
        )

    private fun ReviewQuestion.toTranslationReviewQuestionUi(target: ReviewQuestion?): ReviewQuestionUiState {
        val targetSegments = target?.extractSegments().orEmpty().associateBy { it.key }
        return ReviewQuestionUiState(
            id = id,
            order = order,
            language = language,
            text = text,
            segments =
                extractSegments().map {
                    val translatedText = targetSegments[it.key]?.text.orEmpty()
                    ReviewSegmentUiState(
                        questionId = id,
                        key = it.key,
                        labelKind = it.kind,
                        labelArg = it.arg,
                        sourceText = it.text,
                        translatedText = translatedText,
                    )
                },
        )
    }

    private fun ReviewQuestion.withTranslatedSegments(
        targetLanguage: String,
        translatedSegments: Map<String, String>,
    ): ReviewQuestion {
        val content =
            parseContent()
                ?: return copy(
                    language = targetLanguage,
                    text = translatedSegments.translated("text", text),
                )
        val translated = content.translate(translatedSegments)
        return copy(
            language = targetLanguage,
            text = translated.text,
            payload = json.encodeToString<QuestionContent>(translated),
        )
    }

    private fun QuestionContent.translate(segments: Map<String, String>): QuestionContent =
        when (this) {
            is QuestionContent.SingleChoice ->
                copy(
                    text = segments.translated("text", text),
                    options = options.map { it.copy(text = segments.translated("option:${it.id.raw}", it.text)) },
                    info = segments.translatedInfo(info),
                )
            is QuestionContent.MultipleChoice ->
                copy(
                    text = segments.translated("text", text),
                    options = options.map { it.copy(text = segments.translated("option:${it.id.raw}", it.text)) },
                    info = segments.translatedInfo(info),
                )
            is QuestionContent.Survey ->
                copy(
                    text = segments.translated("text", text),
                    options = options.map { it.copy(text = segments.translated("option:${it.id.raw}", it.text)) },
                    info = segments.translatedInfo(info),
                )
            is QuestionContent.Ordering ->
                copy(
                    text = segments.translated("text", text),
                    items = items.map { it.copy(text = segments.translated("item:${it.id.raw}", it.text)) },
                    info = segments.translatedInfo(info),
                )
            is QuestionContent.FillBlank ->
                copy(
                    text = segments.translated("text", text),
                    candidates =
                        candidates.map {
                            it.copy(text = segments.translated("candidate:${it.id.raw}", it.text))
                        },
                    info = segments.translatedInfo(info),
                )
        }

    private fun Map<String, String>.translated(
        key: String,
        fallback: String,
    ): String = get(key) ?: fallback

    private fun Map<String, String>.translatedInfo(info: String?): String? = info?.let { translated("info", it) }

    private fun ReviewQuestion.extractSegments(): List<ReviewSegmentDraft> {
        val content = parseContent()
        if (content == null) return listOf(ReviewSegmentDraft("text", ReviewSegmentLabelKind.TEXT, null, text))
        val segments = mutableListOf(ReviewSegmentDraft("text", ReviewSegmentLabelKind.TEXT, null, content.text))
        when (content) {
            is QuestionContent.SingleChoice ->
                segments +=
                    content.options.map {
                        ReviewSegmentDraft(
                            key = "option:${it.id.raw}",
                            kind = ReviewSegmentLabelKind.OPTION,
                            arg = it.id.raw,
                            text = it.text,
                        )
                    }
            is QuestionContent.Survey ->
                segments +=
                    content.options.map {
                        ReviewSegmentDraft(
                            key = "option:${it.id.raw}",
                            kind = ReviewSegmentLabelKind.OPTION,
                            arg = it.id.raw,
                            text = it.text,
                        )
                    }
            is QuestionContent.MultipleChoice ->
                segments +=
                    content.options.map {
                        ReviewSegmentDraft(
                            key = "option:${it.id.raw}",
                            kind = ReviewSegmentLabelKind.OPTION,
                            arg = it.id.raw,
                            text = it.text,
                        )
                    }
            is QuestionContent.Ordering ->
                segments +=
                    content.items.map {
                        ReviewSegmentDraft(
                            key = "item:${it.id.raw}",
                            kind = ReviewSegmentLabelKind.ITEM,
                            arg = it.id.raw,
                            text = it.text,
                        )
                    }
            is QuestionContent.FillBlank ->
                segments +=
                    content.candidates.map {
                        ReviewSegmentDraft(
                            key = "candidate:${it.id.raw}",
                            kind = ReviewSegmentLabelKind.CANDIDATE,
                            arg = it.id.raw,
                            text = it.text,
                        )
                    }
        }
        content.info?.takeIf { it.isNotBlank() }?.let {
            segments += ReviewSegmentDraft("info", ReviewSegmentLabelKind.INFO, null, it)
        }
        return segments
    }

    private fun ReviewQuestion.parseContent(): QuestionContent? =
        questionContentParser.parse(
            payload = payload,
            fallbackId = id,
            fallbackText = text,
            fallbackDifficulty = runCatching { Difficulty.valueOf(difficulty) }.getOrDefault(Difficulty.EASY),
        ).getOrNull()

    private fun ReviewAssignmentDetailUiState.mapQuestion(
        questionId: String,
        transform: (ReviewQuestionUiState) -> ReviewQuestionUiState,
    ): ReviewAssignmentDetailUiState =
        copy(questions = questions.map { question -> if (question.id == questionId) transform(question) else question })

    private fun ReviewAssignmentDetailUiState.mapSegment(
        questionId: String,
        segmentKey: String,
        transform: (ReviewSegmentUiState) -> ReviewSegmentUiState,
    ): ReviewAssignmentDetailUiState =
        copy(
            questions =
                questions.map { question ->
                    if (question.id != questionId) {
                        question
                    } else {
                        question.copy(
                            segments =
                                question.segments.map { segment ->
                                    if (segment.key == segmentKey) transform(segment) else segment
                                },
                        )
                    }
                },
        )

    private fun List<ReviewAssignment>.availableFilters(): List<ReviewQueueFilter> {
        val filters = mutableListOf(ReviewQueueFilter.ALL)
        val kinds = flatMap { it.taskKinds }.toSet()
        if (ReviewAssignmentKind.TESTING in kinds) filters += ReviewQueueFilter.TESTING
        if (ReviewAssignmentKind.LOGIC in kinds) filters += ReviewQueueFilter.LOGIC
        if (ReviewAssignmentKind.TRANSLATION in kinds) filters += ReviewQueueFilter.TRANSLATION
        if (ReviewAssignmentKind.TRANSLATION_REVIEW in kinds) filters += ReviewQueueFilter.TRANSLATION_REVIEW
        return filters
    }

    private fun List<ReviewAssignment>.toListItems(
        filter: ReviewQueueFilter,
        syncStates: Map<String, ReviewAssignmentSyncState>,
    ): List<ReviewAssignmentListItemUiState> =
        filter { assignment -> assignment.matches(filter) }
            .map { it.toListItem(syncStates[it.id] ?: ReviewAssignmentSyncState.NONE) }

    private fun ReviewAssignment.matches(filter: ReviewQueueFilter): Boolean =
        when (filter) {
            ReviewQueueFilter.ALL -> true
            ReviewQueueFilter.TESTING -> ReviewAssignmentKind.TESTING in taskKinds
            ReviewQueueFilter.LOGIC -> ReviewAssignmentKind.LOGIC in taskKinds
            ReviewQueueFilter.TRANSLATION ->
                ReviewAssignmentKind.TRANSLATION in taskKinds
            ReviewQueueFilter.TRANSLATION_REVIEW ->
                ReviewAssignmentKind.TRANSLATION_REVIEW in taskKinds
        }

    private fun ReviewAssignment.toListItem(syncState: ReviewAssignmentSyncState): ReviewAssignmentListItemUiState =
        ReviewAssignmentListItemUiState(
            id = id,
            title = title,
            kinds = taskKinds.mapNotNull { it.toUiKindOrNull() }.sorted(),
            languages =
                ReviewLanguagesUi(
                    source = sourceLanguages.sorted(),
                    translationTargets = newTranslationLanguages.sorted(),
                    reviewTargets = reviewLanguages.sorted(),
                ),
            questionCount = questions.size,
            testingScore = checks.testingScore?.formatScore(),
            logicScore = checks.logicScore?.formatScore(),
            translationScore = checks.translationScore?.toString(),
            syncState = syncState.toUi(),
        )

    private fun ReviewAssignmentSyncState.toUi(): ReviewAssignmentSyncUi =
        when (this) {
            ReviewAssignmentSyncState.NONE -> ReviewAssignmentSyncUi.NONE
            ReviewAssignmentSyncState.PENDING -> ReviewAssignmentSyncUi.PENDING
            ReviewAssignmentSyncState.QUARANTINED -> ReviewAssignmentSyncUi.QUARANTINED
        }

    private fun ReviewAssignmentKind.toUiKindOrNull(): ReviewQueueKindUi? =
        when (this) {
            ReviewAssignmentKind.TESTING -> ReviewQueueKindUi.TESTING
            ReviewAssignmentKind.LOGIC -> ReviewQueueKindUi.LOGIC
            ReviewAssignmentKind.TRANSLATION -> ReviewQueueKindUi.TRANSLATION
            ReviewAssignmentKind.TRANSLATION_REVIEW -> ReviewQueueKindUi.TRANSLATION_REVIEW
        }

    private fun ReviewQueueKindUi.toDomainKind(): ReviewAssignmentKind =
        when (this) {
            ReviewQueueKindUi.TESTING -> ReviewAssignmentKind.TESTING
            ReviewQueueKindUi.LOGIC -> ReviewAssignmentKind.LOGIC
            ReviewQueueKindUi.TRANSLATION -> ReviewAssignmentKind.TRANSLATION
            ReviewQueueKindUi.TRANSLATION_REVIEW -> ReviewAssignmentKind.TRANSLATION_REVIEW
        }

    private fun Double.formatScore(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

    private data class ReviewSegmentDraft(
        val key: String,
        val kind: ReviewSegmentLabelKind,
        val arg: String?,
        val text: String,
    )

    private companion object {
        private const val MIN_REVIEW_SCORE = 1
        private const val MAX_REVIEW_SCORE = 3

        val REVIEW_KIND_PRIORITY =
            listOf(
                ReviewQueueKindUi.TESTING,
                ReviewQueueKindUi.LOGIC,
                ReviewQueueKindUi.TRANSLATION,
                ReviewQueueKindUi.TRANSLATION_REVIEW,
            )
    }
}
