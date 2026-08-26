package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewLanguagesUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQuestionUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueKindUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentLabelKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitReviewActionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Suppress("TooManyFunctions", "LongParameterList")
class DefaultReviewQueueComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val observeReviewAssignments: ObserveReviewAssignmentsUseCase,
    private val submitReviewAction: SubmitReviewActionUseCase,
    private val questionContentParser: QuestionContentParser,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ReviewQueueComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(ReviewQueueUiState())
    private val json = Json { encodeDefaults = true }
    private var assignmentsJob: Job? = null
    private var assignments: List<ReviewAssignment> = emptyList()

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
                assignments = this@DefaultReviewQueueComponent.assignments.toListItems(selected),
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

    override fun onSegmentAcceptedChanged(
        questionId: String,
        segmentKey: String,
        accepted: Boolean,
    ) {
        _state.update { state ->
            state.copy(
                detail =
                    state.detail?.mapSegment(questionId, segmentKey) {
                        it.copy(accepted = accepted)
                    },
                errorMessage = null,
            )
        }
    }

    override fun onSubmitClick() {
        val command =
            _state.value.detail
                ?.let { detail ->
                    assignments
                        .firstOrNull { it.id == detail.assignmentId }
                        ?.let { assignment -> detail.toSubmitCommand(assignment) }
                }
                ?: return
        scope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
            val result = submitReviewAction(command)
            _state.update {
                if (result.isSuccess) {
                    it.copy(
                        isSubmitting = false,
                        detail = null,
                        successMessage = UiMessage.Res(R.string.qa_review_submitted),
                    )
                } else {
                    it.copy(
                        isSubmitting = false,
                        errorMessage =
                            result.exceptionOrNull()?.message?.let(UiMessage::Raw)
                                ?: UiMessage.Res(R.string.qa_error_submit_review_failed),
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

    private fun observeAssignments(uid: String) {
        assignmentsJob =
            scope.launch {
                observeReviewAssignments(uid)
                    .catch { error ->
                        assignments = emptyList()
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
                    .collect { loaded ->
                        assignments = loaded
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
                                assignments = loaded.toListItems(selected),
                                detail = selectedDetail,
                                errorMessage = null,
                            )
                        }
                    }
            }
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
                SubmitReviewActionCommand(
                    assignmentId = assignmentId,
                    lessonId = lessonId,
                    kind = kind.toDomainKind(),
                    score = score,
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
                                    accepted = it.accepted,
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
                        accepted = translatedText.isNotBlank(),
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

    private fun List<ReviewAssignment>.toListItems(filter: ReviewQueueFilter): List<ReviewAssignmentListItemUiState> =
        filter { assignment -> assignment.matches(filter) }
            .map { it.toListItem() }

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

    private fun ReviewAssignment.toListItem(): ReviewAssignmentListItemUiState =
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
        )

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
