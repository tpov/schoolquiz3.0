package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.FillBlankAnswerSpec
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.QuestionEditorHistory
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.buildFillBlankRuntimeText
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.extractFillBlankAnswers
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.extractProtectedTextSegments
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.orderFillBlankAnswersByText
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.removeFillBlankAnswerMarker
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.restoreFillBlankAuthorText
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.upsertFillBlankAnswerMarker
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.mapper.toAuthoringDisplayItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.MILLIS_PER_DAY
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DraftConflictUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DraftQuestionListItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankAnswerItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankMarkerKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.LessonPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.NewLessonProposalSubmissionState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.NewLessonProposalUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestArenaTargetNode
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestCreateUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ProposalLessonChoice
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestQuestionEditorUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.SectionPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ThemePathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.validationMessageRes
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command.CreateQuestDraftCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command.SaveDraftQuestionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLesson
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QUEST_ARCHIVE_TARGET_SHELF
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QUEST_ARENA_TARGET_SHELF
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.PrivateQuizSettings
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionRejection
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposalTarget
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ClosePrivateQuizLinkUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.CreateQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetActiveQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftConflictUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ProposeDraftLessonToCourseUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.QuoteLessonProposalFeeUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ResolveQuestDraftConflictUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SaveDraftQuestionUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitQuestDraftToArenaUseCase
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MIN_EDITOR_ROWS = 2
private const val MAX_EDITOR_ROWS = 8
private const val MIN_MULTIPLE_CORRECT = 2
private const val MIN_FILL_BLANK_ANSWERS = 1
private const val MAX_FILL_BLANK_ANSWERS = 3
private const val MAX_FILL_BLANK_CANDIDATES = 10
private const val MIN_FILL_BLANK_CANDIDATES = 5
private val STRUCTURE_SHELVES = listOf("home", "archive", "arena")

/** Вид предложения в теле подачи — тот же словарь, что у приёмника 4.21 (PROPOSAL_KINDS). */
private const val KIND_NEW_LESSON = "NEW_LESSON"

/** Техническое имя узла иерархии, которого у приватного квиза автор не видит (плоский набор, AD-29). */
private const val PRIVATE_FLAT_NODE_TITLE = "Вопросы"

@Suppress("LargeClass", "LongParameterList", "TooManyFunctions")
class DefaultQuestCreateComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val createQuestDraft: CreateQuestDraftUseCase,
    private val getActiveQuestDraft: GetActiveQuestDraftUseCase,
    private val saveDraftQuestion: SaveDraftQuestionUseCase,
    private val submitQuestDraftToArena: SubmitQuestDraftToArenaUseCase,
    private val closePrivateQuizLink: ClosePrivateQuizLinkUseCase,
    /**
     * «Предложить автору» (CAP-27, история 4.24): урок черновика с целевым путём в чужом курсе.
     * null — хост без авторинг-графа (тесты): вход скрыт, а не мёртвая кнопка.
     */
    private val proposeDraftLessonToCourse: ProposeDraftLessonToCourseUseCase? = null,
    private val quoteProposalFee: QuoteLessonProposalFeeUseCase? = null,
    private val timestampProvider: QuestAuthoringTimestampProvider,
    private val observeQuestDraftConflict: ObserveQuestDraftConflictUseCase,
    private val resolveQuestDraftConflict: ResolveQuestDraftConflictUseCase,
    private val questionContentParser: QuestionContentParser,
    private val questRepository: QuestRepository,
    private val sectionRepository: SectionRepository,
    private val themeRepository: ThemeRepository,
    private val lessonRepository: LessonRepository,
    private val navigator: Navigator,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : QuestCreateComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(QuestCreateUiState())
    private val questionJson = Json { encodeDefaults = true }
    private val editorHistory = QuestionEditorHistory()
    private var ownerUid: String? = null
    private var activeBundle: QuestAuthoringBundle? = null
    private var questsJob: Job? = null
    private var sectionsJob: Job? = null
    private var themesJob: Job? = null
    private var lessonsJob: Job? = null
    private var conflictJob: Job? = null
    private var proposalQuestsJob: Job? = null
    private var proposalSectionsJob: Job? = null
    private var proposalThemesJob: Job? = null

    override val state: StateFlow<QuestCreateUiState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        observeUser()
        observeCatalogsList()
    }

    override fun onCatalogSelected(id: CatalogId?) {
        selectCatalog(id)
    }

    override fun onQuestSelected(id: QuestId?) {
        selectQuest(id)
    }

    override fun onSectionSelected(id: SectionId?) {
        selectSection(id)
    }

    override fun onThemeSelected(id: ThemeId?) {
        selectTheme(id)
    }

    override fun onLessonSelected(id: LessonId?) {
        _state.update {
            it.copy(
                selectedLessonId = id,
                newLessonTitle = if (id == null) it.newLessonTitle else "",
                errorMessage = null,
            )
        }
    }

    override fun onTitleChanged(value: String) {
        _state.update { it.copy(newQuestTitle = value, errorMessage = null) }
    }

    override fun onDescriptionChanged(value: String) {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun onLanguageChanged(value: String) {
        _state.update { it.copy(defaultLanguage = value.trim(), errorMessage = null) }
    }

    override fun onDifficultySelected(value: Difficulty) {
        _state.update { it.copy(defaultDifficulty = value, errorMessage = null) }
    }

    // ─── Приватная категория (AD-29): настройки ссылки ──────────────────────────────────────────
    // Дефолты живут в домене (PrivateQuizSettings); форма только показывает их и передаёт
    // выбранное. Лимит — целый ≥ 1: ноль и отрицательное до домена не доходят.

    override fun onPrivateAttemptLimitChanged(value: Int) {
        _state.update {
            it.copy(
                privateAttemptLimit = value.coerceAtLeast(PrivateQuizSettings.MIN_ATTEMPT_LIMIT),
                errorMessage = null,
            )
        }
    }

    override fun onPrivateShowResultChanged(value: Boolean) {
        _state.update { it.copy(privateShowResult = value, errorMessage = null) }
    }

    override fun onPrivateLinkExpiryToggled(hasExpiry: Boolean) {
        _state.update {
            it.copy(
                privateLinkHasExpiry = hasExpiry,
                // Трогая управление сроком, автор переезжает на пресет дней вместо
                // сохранённого момента — только тогда перезаявка несёт новый момент.
                expiryEdited = true,
                savedLinkExpiresAtMs = if (hasExpiry) it.savedLinkExpiresAtMs else null,
                errorMessage = null,
            )
        }
    }

    override fun onPrivateLinkExpiryDaysChanged(days: Int) {
        _state.update {
            it.copy(
                privateLinkHasExpiry = true,
                expiryEdited = true,
                savedLinkExpiresAtMs = null,
                privateLinkExpiresInDays = days.coerceAtLeast(1),
                errorMessage = null,
            )
        }
    }

    override fun onClosePrivateQuizLinkClick() {
        val uid = ownerUid
        val bundle = activeBundle
        if (uid == null || bundle == null) {
            _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready)) }
            return
        }
        scope.launch {
            _state.update { it.copy(isLinkCloseQueued = true, errorMessage = null) }
            val result = closePrivateQuizLink(
                ownerUid = uid,
                catalogId = bundle.draft.catalogId.value,
                questId = bundle.draft.id.value,
            )
            _state.update {
                if (result.isSuccess) {
                    it.copy(
                        isLinkCloseQueued = true,
                        arenaMessage = UiMessage.Res(R.string.qa_private_link_closing),
                    )
                } else {
                    it.copy(
                        isLinkCloseQueued = false,
                        errorMessage = UiMessage.Res(R.string.qa_private_link_close_failed),
                    )
                }
            }
        }
    }

    override fun onSectionTitleChanged(value: String) {
        _state.update { it.copy(newSectionTitle = value, errorMessage = null) }
    }

    override fun onThemeTitleChanged(value: String) {
        _state.update { it.copy(newThemeTitle = value, errorMessage = null) }
    }

    override fun onLessonTitleChanged(value: String) {
        _state.update { it.copy(newLessonTitle = value, errorMessage = null) }
    }

    // ─── «Предложить автору» (CAP-27, история 4.24): целевой путь в чужом курсе ─────────────────

    private val _newLessonProposalState = MutableStateFlow(NewLessonProposalUiState())

    override val newLessonProposalState: StateFlow<NewLessonProposalUiState> =
        _newLessonProposalState.asStateFlow()

    /** Диалог открыт: уроки черновика на выбор и плата 1000 до отправки (UX-23). */
    override fun onNewProposalDialogOpened() {
        val bundle = activeBundle
        _newLessonProposalState.value = NewLessonProposalUiState(
            visible = true,
            catalogs = _state.value.catalogs,
            lessonChoices =
                bundle?.lessons
                    ?.sortedBy { it.order }
                    ?.map { ProposalLessonChoice(id = it.id, title = it.title) }
                    .orEmpty(),
            selectedLessonId = bundle?.lessons?.sortedBy { it.order }?.firstOrNull()?.id,
        )
        val quote = quoteProposalFee ?: return
        scope.launch {
            val fee = quote(KIND_NEW_LESSON).getOrNull()
            _newLessonProposalState.update { it.copy(feeQuote = fee) }
        }
    }

    override fun onNewProposalDialogDismissed() {
        proposalQuestsJob?.cancel()
        proposalSectionsJob?.cancel()
        proposalThemesJob?.cancel()
        _newLessonProposalState.value = NewLessonProposalUiState()
    }

    override fun onProposalTargetCatalogSelected(id: CatalogId?) {
        proposalQuestsJob?.cancel()
        _newLessonProposalState.update {
            it.copy(
                selectedCatalogId = id,
                questItems = emptyList(),
                selectedQuestId = null,
                sectionItems = emptyList(),
                selectedSectionId = null,
                themeItems = emptyList(),
                selectedThemeId = null,
            )
        }
        if (id != null) observeProposalQuestsForCatalog(id)
    }

    override fun onProposalTargetQuestSelected(id: QuestId?) {
        proposalSectionsJob?.cancel()
        _newLessonProposalState.update {
            it.copy(
                selectedQuestId = id,
                sectionItems = emptyList(),
                selectedSectionId = null,
                themeItems = emptyList(),
                selectedThemeId = null,
            )
        }
        if (id != null) observeProposalSectionsForQuest(id)
    }

    override fun onProposalTargetSectionSelected(id: SectionId?) {
        proposalThemesJob?.cancel()
        _newLessonProposalState.update {
            it.copy(
                selectedSectionId = id,
                themeItems = emptyList(),
                selectedThemeId = null,
            )
        }
        if (id != null) observeProposalThemesForSection(id)
    }

    override fun onProposalTargetThemeSelected(id: ThemeId?) {
        _newLessonProposalState.update { it.copy(selectedThemeId = id) }
    }

    override fun onProposalLessonSelected(id: DraftLessonId?) {
        _newLessonProposalState.update { it.copy(selectedLessonId = id) }
    }

    /** Подача (AD-2/AD-3): урок черновика уезжает телом предложения, шторка показывает состояние. */
    override fun onProposeLessonToAuthorClick() {
        val propose = proposeDraftLessonToCourse ?: return
        val state = _newLessonProposalState.value
        if (state.submission == NewLessonProposalSubmissionState.Sending) return
        val uid = ownerUid
        val bundle = activeBundle
        val draftId = bundle?.draft?.id
        val lessonId = state.selectedLessonId
        val target = state.proposalTarget()
        if (uid == null || draftId == null || lessonId == null || target == null) {
            _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_save_quest_first)) }
            return
        }
        scope.launch {
            _newLessonProposalState.update { it.copy(submission = NewLessonProposalSubmissionState.Sending) }
            val outcome = propose(uid, draftId, lessonId, target)
            _newLessonProposalState.update {
                it.copy(
                    submission =
                        if (outcome.isSuccess) NewLessonProposalSubmissionState.Done
                        else NewLessonProposalSubmissionState.Failed,
                )
            }
        }
    }

    private fun NewLessonProposalUiState.proposalTarget(): NewLessonProposalTarget? {
        val catalogId = selectedCatalogId ?: return null
        val questId = selectedQuestId ?: return null
        val sectionId = selectedSectionId ?: return null
        val themeId = selectedThemeId ?: return null
        return NewLessonProposalTarget(
            catalogId = catalogId.value,
            questId = questId.value,
            sectionId = sectionId.value,
            themeId = themeId.value,
        )
    }

    private fun observeProposalQuestsForCatalog(catalogId: CatalogId) {
        proposalQuestsJob =
            scope.launch {
                combine(STRUCTURE_SHELVES.map { shelf -> questRepository.observeByCatalog(catalogId, shelf) }) {
                        questLists ->
                    questLists
                        .flatMap { it }
                        .distinctBy { it.id }
                        .sortedWith(compareBy<Quest> { it.title.lowercase() }.thenBy { it.id.value })
                }
                    .collect { quests ->
                        _newLessonProposalState.update { current ->
                            val selectedQuest =
                                current.selectedQuestId?.takeIf { id -> quests.any { it.id == id } }
                            current.copy(
                                questItems = quests.map { QuestPathItem(id = it.id, title = it.title) },
                                selectedQuestId = selectedQuest,
                            )
                        }
                    }
            }
    }

    private fun observeProposalSectionsForQuest(questId: QuestId) {
        proposalSectionsJob =
            scope.launch {
                sectionRepository.observeByQuest(questId).collect { sections ->
                    _newLessonProposalState.update { current ->
                        val selectedSection =
                            current.selectedSectionId?.takeIf { id -> sections.any { it.id == id } }
                        current.copy(
                            sectionItems = sections.toSectionPathItems(),
                            selectedSectionId = selectedSection,
                        )
                    }
                }
            }
    }

    private fun observeProposalThemesForSection(sectionId: SectionId) {
        proposalThemesJob =
            scope.launch {
                themeRepository.observeBySection(sectionId).collect { themes ->
                    _newLessonProposalState.update { current ->
                        val selectedTheme =
                            current.selectedThemeId?.takeIf { id -> themes.any { it.id == id } }
                        current.copy(
                            themeItems = themes.toThemePathItems(),
                            selectedThemeId = selectedTheme,
                        )
                    }
                }
            }
    }

    override fun onStructureCheckClick() {
        if (_state.value.hasActiveDraft) {
            _state.update { it.copy(errorMessage = null) }
        } else {
            onCreateClick()
        }
    }

    override fun onCreateClick() {
        createDraft(
            snapshot = _state.value,
            showEditor = false,
        )
    }

    override fun onContinueDraftClick() {
        openQuestions(_state.value.defaultDifficulty)
    }

    override fun onSubmitToArenaClick() {
        submitNodeToArena(
            target = QuestArenaTargetNode.QUEST,
            targetShelfOverride = QUEST_ARENA_TARGET_SHELF,
        )
    }

    override fun onSubmitNodeToArenaLongClick(target: QuestArenaTargetNode) {
        submitNodeToArena(target)
    }

    private fun submitNodeToArena(
        target: QuestArenaTargetNode,
        targetShelfOverride: String? = null,
    ) {
        val uid = ownerUid
        val bundle = activeBundle
        val draftId = bundle?.draft?.id
        val targetShelf = targetShelfOverride ?: _state.value.targetShelf()
        val lessonIds = bundle?.targetLessonIds(target).orEmpty()
        when {
            uid == null ->
                _state.update {
                    it.copy(
                        errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready),
                        arenaMessage = null,
                    )
                }
            draftId == null ->
                _state.update {
                    it.copy(
                        errorMessage = UiMessage.Res(R.string.qa_error_save_quest_first),
                        arenaMessage = null,
                    )
                }
            lessonIds.isEmpty() ->
                _state.update {
                    it.copy(
                        errorMessage = UiMessage.Res(R.string.qa_error_no_lessons_in_node),
                        arenaMessage = null,
                    )
                }
            else ->
                scope.launch {
                    _state.update {
                        it.copy(
                            isSubmittingToArena = true,
                            errorMessage = null,
                            arenaMessage = null,
                        )
                    }
                    val result =
                        submitQuestDraftToArena(
                            draftId = draftId,
                            lessonIds = lessonIds,
                            targetShelf = targetShelf,
                        )
                    if (result.isSuccess) {
                        refreshActiveDraft(uid)
                        _state.update {
                            it.copy(
                                isSubmittingToArena = false,
                                errorMessage = null,
                                arenaMessage =
                                    UiMessage.ArenaQueued(
                                        target = target,
                                        toArchive = targetShelf == QUEST_ARCHIVE_TARGET_SHELF,
                                    ),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isSubmittingToArena = false,
                                errorMessage = result.exceptionOrNull().arenaSubmissionErrorMessage(),
                                arenaMessage = null,
                            )
                        }
                    }
                }
        }
    }

    /**
     * Принять серверную версию и отправить работу заново (AD-24).
     *
     * Прежняя заявка не повторяется: её ключ сервер уже видел и отверг, повтор по нему не применит
     * ничего (AD-2). Уходит новая мутация с новым ключом, а прежняя запись снимается — обе
     * половины одной транзакцией на стороне данных.
     *
     * Конфликт без названной сервером версии отклоняется здесь, а не только прячется с экрана:
     * новая мутация ушла бы с пустой ожидаемой версией, то есть вообще без сверки, — ровно то, от
     * чего конфликт и защищает. Такое расхождение снимает следующий снимок с сервера.
     */
    override fun onResubmitOverServerVersionClick() {
        val uid = ownerUid
        val draftId = activeBundle?.draft?.id
        val conflict = _state.value.draftConflict
        val serverVersion = conflict?.serverVersion
        if (uid == null || draftId == null || conflict == null) {
            _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready)) }
            return
        }
        if (serverVersion == null) {
            _state.update {
                it.copy(errorMessage = UiMessage.Res(R.string.qa_conflict_unknown_version_hint))
            }
            return
        }
        scope.launch {
            _state.update { it.copy(isSubmittingToArena = true, errorMessage = null, arenaMessage = null) }
            val result = resolveQuestDraftConflict(uid, draftId)
            if (result.isSuccess) {
                refreshActiveDraft(uid)
                _state.update {
                    it.copy(
                        isSubmittingToArena = false,
                        errorMessage = null,
                        arenaMessage =
                            UiMessage.Res(
                                R.string.qa_conflict_resubmitted,
                                listOf(serverVersion),
                            ),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isSubmittingToArena = false,
                        errorMessage = UiMessage.Res(R.string.qa_conflict_resolve_failed),
                        arenaMessage = null,
                    )
                }
            }
        }
    }

    override fun onQuestionsClick(difficulty: Difficulty) {
        // Приватная категория — плоский HARD-набор (AD-29): кнопки лёгких у неё нет, и любая
        // дорога к вопросам ведёт на HARD.
        val effective = if (_state.value.isPrivateCategory) Difficulty.HARD else difficulty
        val snapshot = _state.value.copy(defaultDifficulty = effective)
        _state.update { it.copy(defaultDifficulty = effective, errorMessage = null) }
        if (snapshot.hasActiveDraft) {
            openQuestions(effective)
        } else {
            createDraft(
                snapshot = snapshot,
                showEditor = true,
                editorDifficulty = effective,
                requireStructureTitles = false,
            )
        }
    }

    private fun createDraft(
        snapshot: QuestCreateUiState,
        showEditor: Boolean,
        editorDifficulty: Difficulty = snapshot.defaultDifficulty,
        requireStructureTitles: Boolean = true,
    ) {
        val command = createDraftCommandOrNull(snapshot, requireStructureTitles) ?: return
        scope.launch {
            _state.update { it.copy(isCreating = true, errorMessage = null) }
            val result = createQuestDraft(command)
            if (result.isSuccess) {
                refreshActiveDraft(
                    uid = command.ownerUid,
                    selectedQuestionId = null,
                    openNewQuestion = false,
                    showEditor = showEditor,
                    difficulty = editorDifficulty,
                )
                _state.update { it.copy(isCreating = false, errorMessage = null) }
            } else {
                _state.update {
                    it.copy(
                        isCreating = false,
                        errorMessage =
                            result.exceptionOrNull()?.message?.let(UiMessage::Raw)
                                ?: UiMessage.Res(R.string.qa_error_save_quest_failed),
                    )
                }
            }
        }
    }

    private fun openQuestions(difficulty: Difficulty) {
        scope.launch {
            val uid = ownerUid
            if (uid == null) {
                _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready)) }
                return@launch
            }
            _state.update { it.copy(defaultDifficulty = difficulty, errorMessage = null) }
            refreshActiveDraft(
                uid = uid,
                selectedQuestionId = null,
                openNewQuestion = false,
                showEditor = true,
                difficulty = difficulty,
            )
        }
    }

    private fun createDraftCommandOrNull(
        snapshot: QuestCreateUiState,
        requireStructureTitles: Boolean,
    ): CreateQuestDraftCommand? {
        val uid = ownerUid
        val catalogId = snapshot.selectedCatalogId
        val structureErrorRes = if (requireStructureTitles) snapshot.structureValidationErrorRes() else null
        return when {
            uid == null -> {
                _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready)) }
                null
            }
            catalogId == null -> {
                _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_select_catalog)) }
                null
            }
            structureErrorRes != null -> {
                _state.update { it.copy(errorMessage = UiMessage.Res(structureErrorRes)) }
                null
            }
            else ->
                CreateQuestDraftCommand(
                    ownerUid = uid,
                    catalogId = catalogId,
                    sourceQuestId = snapshot.selectedQuestId,
                    title = snapshot.selectedQuestTitle(),
                    description = null,
                    defaultLanguage = snapshot.defaultLanguage.ifBlank { "ru" },
                    // Приватный формат — плоский HARD-набор (AD-29): сложность формы не спрашивает.
                    defaultDifficulty =
                        if (snapshot.isPrivateCategory) {
                            Difficulty.HARD
                        } else {
                            snapshot.defaultDifficulty
                        },
                    // Иерархии у приватного квиза нет — раздел/тема/урок создаются конвейером
                    // под капот. Названия технические: автору они не показываются.
                    sectionTitle = snapshot.selectedSectionTitle().ifBlank { PRIVATE_FLAT_NODE_TITLE },
                    themeTitle = snapshot.selectedThemeTitle().ifBlank { PRIVATE_FLAT_NODE_TITLE },
                    lessonTitle = snapshot.selectedLessonTitle().ifBlank { PRIVATE_FLAT_NODE_TITLE },
                    privateQuiz =
                        if (snapshot.isPrivateCategory) {
                            val settings =
                                PrivateQuizSettings(
                                    attemptLimit = snapshot.privateAttemptLimit,
                                    showResult = snapshot.privateShowResult,
                                    linkExpiresAtMs =
                                        when {
                                            !snapshot.privateLinkHasExpiry -> null
                                            // Нетронутый сохранённый срок перезаявке едет как был:
                                            // пересчёт в дни продлевал бы авторский дедлайн (AD-29).
                                            !snapshot.expiryEdited -> snapshot.savedLinkExpiresAtMs
                                            else ->
                                                timestampProvider.nowMs() + snapshot.privateLinkExpiresInDays * MILLIS_PER_DAY
                                        },
                                )
                            // Срок на сохранении только в будущем: часы ушли вперёд настроенного
                            // момента — отказ, а не ссылка, рождающаяся уже закрытой (AD-29).
                            if (settings.validateForSave(timestampProvider.nowMs())) {
                                settings
                            } else {
                                _state.update {
                                    it.copy(errorMessage = UiMessage.Res(R.string.qa_private_expiry_in_past))
                                }
                                return null
                            }
                        } else {
                            null
                        },
                )
        }
    }

    override fun onQuestionSelected(index: Int) {
        navigateFromEditor { difficulty ->
            selectQuestion(index, difficulty)
        }
    }

    override fun onPreviousQuestionClick() {
        val current = _state.value.editor ?: return
        navigateFromEditor { difficulty ->
            selectQuestion(current.activeIndex - 1, difficulty)
        }
    }

    override fun onNextQuestionClick() {
        val current = _state.value.editor ?: return
        navigateFromEditor { difficulty ->
            selectQuestion(current.activeIndex + 1, difficulty)
        }
    }

    override fun onAddQuestionClick() {
        navigateFromEditor { difficulty ->
            val bundle = activeBundle ?: return@navigateFromEditor
            selectQuestion(bundle.sortedQuestionsForActiveLesson(difficulty).size, difficulty)
        }
    }

    override fun onQuestionTypeSelected(value: DraftQuestionType) {
        updateEditable { it.copy(type = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionDifficultySelected(value: Difficulty) {
        updateEditable { it.copy(difficulty = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionLanguageChanged(value: String) {
        updateEditable { it.copy(language = value.trim(), errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionTextChanged(value: String) {
        updateEditable { it.copy(text = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionImagePathChanged(value: String) {
        updateEditable { it.copy(imagePath = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionInfoChanged(value: String) {
        updateEditable { it.copy(info = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onOptionTextChanged(
        index: Int,
        value: String,
    ) {
        updateEditable {
            it.copy(
                optionTexts = it.optionTexts.withValue(index, value),
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onOptionAdded() {
        updateEditable {
            if (it.optionTexts.size >= MAX_EDITOR_ROWS) {
                it
            } else {
                it.copy(
                    optionTexts = it.optionTexts + "",
                    errorMessage = null,
                    lastSavedMessage = null,
                )
            }
        }
    }

    override fun onOptionRemoved(index: Int) {
        updateEditable {
            if (it.optionTexts.size <= MIN_EDITOR_ROWS) {
                it
            } else {
                val nextOptions = it.optionTexts.removeAtOrSame(index).ensureEditorRows()
                it.copy(
                    optionTexts = nextOptions,
                    correctSingleIndex =
                        it.correctSingleIndex
                            .shiftAfterRemove(index)
                            .coerceIn(0, nextOptions.lastIndex),
                    correctMultipleIndexes = it.correctMultipleIndexes.shiftAfterRemove(index),
                    errorMessage = null,
                    lastSavedMessage = null,
                )
            }
        }
    }

    override fun onSingleCorrectSelected(index: Int) {
        updateEditable { it.copy(correctSingleIndex = index, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onMultipleCorrectToggled(index: Int) {
        updateEditable {
            val nextIndexes =
                if (index in it.correctMultipleIndexes) {
                    it.correctMultipleIndexes - index
                } else {
                    it.correctMultipleIndexes + index
                }
            it.copy(correctMultipleIndexes = nextIndexes, errorMessage = null, lastSavedMessage = null)
        }
    }

    override fun onOrderingItemTextChanged(
        index: Int,
        value: String,
    ) {
        updateEditable {
            it.copy(
                orderingItems = it.orderingItems.withValue(index, value),
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onOrderingItemAdded() {
        updateEditable {
            if (it.orderingItems.size >= MAX_EDITOR_ROWS) {
                it
            } else {
                it.copy(
                    orderingItems = it.orderingItems + "",
                    errorMessage = null,
                    lastSavedMessage = null,
                )
            }
        }
    }

    override fun onOrderingItemRemoved(index: Int) {
        updateEditable {
            if (it.orderingItems.size <= MIN_EDITOR_ROWS) {
                it
            } else {
                it.copy(
                    orderingItems = it.orderingItems.removeAtOrSame(index).ensureEditorRows(),
                    errorMessage = null,
                    lastSavedMessage = null,
                )
            }
        }
    }

    override fun onFillBlankTextChanged(value: String) {
        updateEditable {
            it.withSyncedFillBlankText(value)
                .copy(errorMessage = null, lastSavedMessage = null)
        }
    }

    override fun onFillBlankMarkerAdded(kind: FillBlankMarkerKind) {
        updateEditable {
            it.withSyncedFillBlankText(it.fillBlankText.appendFillBlankMarker(kind))
                .copy(errorMessage = null, lastSavedMessage = null)
        }
    }

    override fun onFillBlankAnswerChanged(
        index: Int,
        value: String,
    ) {
        updateEditable {
            val currentAnswer = it.fillBlankAnswers.getOrNull(index) ?: return@updateEditable it
            val nextAnswer = currentAnswer.copy(text = value)
            it.copy(
                fillBlankText =
                    upsertFillBlankAnswerMarker(
                        text = it.fillBlankText,
                        answerIndex = index,
                        answer =
                            FillBlankAnswerSpec(
                                text = value,
                                isProtected = nextAnswer.isProtected,
                            ),
                    ),
                fillBlankAnswers =
                    it.fillBlankAnswers.withValue(index) { answer ->
                        answer.copy(text = value)
                    },
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onFillBlankAnswerProtectedChanged(
        index: Int,
        value: Boolean,
    ) {
        updateEditable {
            val currentAnswer = it.fillBlankAnswers.getOrNull(index) ?: return@updateEditable it
            it.copy(
                fillBlankText =
                    upsertFillBlankAnswerMarker(
                        text = it.fillBlankText,
                        answerIndex = index,
                        answer =
                            FillBlankAnswerSpec(
                                text = currentAnswer.text,
                                isProtected = value,
                            ),
                    ),
                fillBlankAnswers =
                    it.fillBlankAnswers.withValue(index) { answer ->
                        answer.copy(isProtected = value)
                    },
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onFillBlankAnswerAdded() {
        updateEditable {
            if (it.fillBlankAnswers.size >= MAX_FILL_BLANK_ANSWERS) {
                it
            } else {
                val nextIndex = it.fillBlankAnswers.size
                it.copy(
                    fillBlankText =
                        upsertFillBlankAnswerMarker(
                            text = it.fillBlankText,
                            answerIndex = nextIndex,
                            answer = FillBlankAnswerSpec(text = ""),
                        ),
                    fillBlankAnswers = it.fillBlankAnswers + FillBlankAnswerItem(),
                    errorMessage = null,
                    lastSavedMessage = null,
                )
            }
        }
    }

    override fun onFillBlankAnswerRemoved(index: Int) {
        updateEditable {
            if (it.fillBlankAnswers.size <= MIN_FILL_BLANK_ANSWERS) {
                it
            } else {
                it.copy(
                    fillBlankText =
                        removeFillBlankAnswerMarker(
                            text = it.fillBlankText,
                            answerIndex = index,
                        ),
                    fillBlankAnswers = it.fillBlankAnswers.removeAtOrSame(index).ensureFillBlankAnswerRows(),
                    errorMessage = null,
                    lastSavedMessage = null,
                )
            }
        }
    }

    override fun onFillBlankDistractorChanged(
        index: Int,
        value: String,
    ) {
        updateEditable {
            it.copy(
                fillBlankDistractors = it.fillBlankDistractors.withValue(index, value),
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onFillBlankDistractorAdded() {
        updateEditable {
            if (it.fillBlankAnswers.size + it.fillBlankDistractors.size >= MAX_FILL_BLANK_CANDIDATES) {
                it
            } else {
                it.copy(
                    fillBlankDistractors = it.fillBlankDistractors + "",
                    errorMessage = null,
                    lastSavedMessage = null,
                )
            }
        }
    }

    override fun onFillBlankDistractorRemoved(index: Int) {
        updateEditable {
            it.copy(
                fillBlankDistractors = it.fillBlankDistractors.removeAtOrSame(index),
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onSaveQuestionClick() {
        val editor = _state.value.editor
        if (editor == null) return
        scope.launch {
            saveEditorQuestion(editor, showSavedMessage = true)
        }
    }

    override fun onQuestionPreviewClick() {
        updateEditor { it.copy(isPreviewVisible = !it.isPreviewVisible) }
    }

    override fun onBackToStructureClick() {
        navigateFromEditor {
            editorHistory.clear()
            _state.update { it.copy(editor = null, errorMessage = null) }
        }
    }

    override fun onBackClick() {
        navigator.goTo(Destination.Back)
    }

    private fun navigateFromEditor(navigate: (Difficulty) -> Unit) {
        val editor = _state.value.editor ?: return
        val difficulty = editor.difficulty
        when {
            editor.canSave -> {
                scope.launch {
                    if (saveEditorQuestion(editor, showSavedMessage = false) != null) {
                        navigate(difficulty)
                    }
                }
            }
            editor.isEmptyNewQuestion() -> navigate(difficulty)
            else ->
                updateEditor {
                    it.copy(
                        errorMessage = UiMessage.Res(it.type.validationMessageRes()),
                        lastSavedMessage = null,
                    )
                }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun saveEditorQuestion(
        editor: QuestQuestionEditorUiState,
        showSavedMessage: Boolean,
    ): DraftQuestionId? {
        val uid = ownerUid
        if (uid == null) {
            updateEditor { it.copy(errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready)) }
            return null
        }

        updateEditor { it.copy(isSaving = true, errorMessage = null, lastSavedMessage = null) }
        val isNewQuestion = editor.selectedQuestionId == null
        val contentResult = buildQuestionContent(editor)
        val content = contentResult.getOrNull()
        if (content == null) {
            updateEditor {
                it.copy(
                    isSaving = false,
                    errorMessage =
                        contentResult.exceptionOrNull()?.message?.let(UiMessage::Raw)
                            ?: UiMessage.Res(R.string.qa_error_check_fields),
                )
            }
            return null
        }

        val payload = questionJson.encodeToString<QuestionContent>(content)
        // Приватная категория: SURVEY исключён и сложность ровно одна — HARD (AD-29).
        val activeBundleDraft = activeBundle?.draft
        val isPrivate = activeBundleDraft?.privateQuiz != null
        val effectiveType = if (isPrivate && editor.type == DraftQuestionType.SURVEY) DraftQuestionType.SINGLE_CHOICE else editor.type
        val effectiveDifficulty =
            if (isPrivate) Difficulty.HARD else editor.difficulty
        val result =
            saveDraftQuestion(
                SaveDraftQuestionCommand(
                    draftId = editor.draftId,
                    lessonId = editor.lessonId,
                    questionId = editor.selectedQuestionId,
                    type = effectiveType,
                    language = editor.language.ifBlank { "ru" },
                    difficulty = effectiveDifficulty,
                    order = editor.selectedQuestionId?.existingQuestionOrder() ?: nextQuestionOrder(editor.lessonId),
                    imagePath = editor.imagePath.cleanNullable(),
                    payload = payload,
                ),
            )
        val savedQuestionId = result.getOrNull()
        if (savedQuestionId != null) {
            refreshActiveDraft(
                uid = uid,
                selectedQuestionId = savedQuestionId,
                openNewQuestion = false,
                showEditor = true,
                difficulty = editor.difficulty,
            )
            updateEditor {
                it.copy(
                    isSaving = false,
                    lastSavedMessage =
                        if (showSavedMessage) {
                            val savedRes =
                                if (isNewQuestion) {
                                    R.string.qa_saved_question_created
                                } else {
                                    R.string.qa_saved_question_saved
                                }
                            UiMessage.Res(savedRes)
                        } else {
                            null
                        },
                )
            }
            return savedQuestionId
        }

        updateEditor {
            it.copy(
                isSaving = false,
                errorMessage =
                    result.exceptionOrNull()?.message?.let(UiMessage::Raw)
                        ?: UiMessage.Res(R.string.qa_error_save_question_failed),
            )
        }
        return null
    }

    private fun observeUser() {
        scope.launch {
            authRepository.observeUid().collect { uid ->
                ownerUid = uid
                _state.update { it.copy(isWaitingForUser = uid == null) }
                if (uid != null) {
                    refreshActiveDraft(uid)
                }
            }
        }
    }

    private fun observeCatalogsList() {
        scope.launch {
            observeCatalogs().collect { catalogs ->
                val displayItems = catalogs.map { it.toAuthoringDisplayItem() }
                val selectedCatalogId = _state.value.selectedCatalogId ?: displayItems.firstOrNull()?.id
                _state.update { current ->
                    current.copy(
                        catalogs = displayItems,
                        selectedCatalogId = selectedCatalogId,
                    )
                }
                if (selectedCatalogId != null && _state.value.questItems.isEmpty()) {
                    observeQuestsForCatalog(selectedCatalogId)
                }
            }
        }
    }

    private fun selectCatalog(id: CatalogId?) {
        clearHierarchyJobs()
        _state.update {
            it.copy(
                selectedCatalogId = id,
                selectedQuestId = null,
                questItems = emptyList(),
                newQuestTitle = "",
                selectedSectionId = null,
                sectionItems = emptyList(),
                newSectionTitle = "",
                selectedThemeId = null,
                themeItems = emptyList(),
                newThemeTitle = "",
                selectedLessonId = null,
                lessonItems = emptyList(),
                newLessonTitle = "",
                errorMessage = null,
            )
        }
        if (id != null) observeQuestsForCatalog(id)
    }

    private fun selectQuest(id: QuestId?) {
        sectionsJob?.cancel()
        themesJob?.cancel()
        lessonsJob?.cancel()
        _state.update {
            it.copy(
                selectedQuestId = id,
                newQuestTitle = if (id == null) it.newQuestTitle else "",
                selectedSectionId = null,
                sectionItems = emptyList(),
                newSectionTitle = "",
                selectedThemeId = null,
                themeItems = emptyList(),
                newThemeTitle = "",
                selectedLessonId = null,
                lessonItems = emptyList(),
                newLessonTitle = "",
                errorMessage = null,
            )
        }
        if (id != null) observeSectionsForQuest(id)
    }

    private fun selectSection(id: SectionId?) {
        themesJob?.cancel()
        lessonsJob?.cancel()
        _state.update {
            it.copy(
                selectedSectionId = id,
                newSectionTitle = if (id == null) it.newSectionTitle else "",
                selectedThemeId = null,
                themeItems = emptyList(),
                newThemeTitle = "",
                selectedLessonId = null,
                lessonItems = emptyList(),
                newLessonTitle = "",
                errorMessage = null,
            )
        }
        if (id != null) observeThemesForSection(id)
    }

    private fun selectTheme(id: ThemeId?) {
        lessonsJob?.cancel()
        _state.update {
            it.copy(
                selectedThemeId = id,
                newThemeTitle = if (id == null) it.newThemeTitle else "",
                selectedLessonId = null,
                lessonItems = emptyList(),
                newLessonTitle = "",
                errorMessage = null,
            )
        }
        if (id != null) observeLessonsForTheme(id)
    }

    private fun observeQuestsForCatalog(catalogId: CatalogId) {
        questsJob?.cancel()
        questsJob =
            scope.launch {
                combine(STRUCTURE_SHELVES.map { shelf -> questRepository.observeByCatalog(catalogId, shelf) }) {
                        questLists ->
                    questLists
                        .flatMap { it }
                        .distinctBy { it.id }
                        .sortedWith(compareBy<Quest> { it.title.lowercase() }.thenBy { it.id.value })
                }
                    .catch { error -> _state.update { it.copy(errorMessage = error.message?.let(UiMessage::Raw)) } }
                    .collect { quests ->
                        _state.update { current ->
                            val selectedQuest =
                                current.selectedQuestId?.takeIf { id -> quests.any { it.id == id } }
                            current.copy(
                                questItems = quests.map { QuestPathItem(id = it.id, title = it.title) },
                                selectedQuestId = selectedQuest,
                            )
                        }
                        _state.value.selectedQuestId?.let(::observeSectionsForQuest)
                    }
            }
    }

    private fun observeSectionsForQuest(questId: QuestId) {
        sectionsJob?.cancel()
        sectionsJob =
            scope.launch {
                sectionRepository.observeByQuest(questId)
                    .catch { error -> _state.update { it.copy(errorMessage = error.message?.let(UiMessage::Raw)) } }
                    .collect { sections ->
                        _state.update { current ->
                            val selectedSection =
                                current.selectedSectionId?.takeIf { id -> sections.any { it.id == id } }
                            current.copy(
                                sectionItems = sections.toSectionPathItems(),
                                selectedSectionId = selectedSection,
                            )
                        }
                    }
            }
    }

    private fun observeThemesForSection(sectionId: SectionId) {
        themesJob?.cancel()
        themesJob =
            scope.launch {
                themeRepository.observeBySection(sectionId)
                    .catch { error -> _state.update { it.copy(errorMessage = error.message?.let(UiMessage::Raw)) } }
                    .collect { themes ->
                        _state.update { current ->
                            val selectedTheme =
                                current.selectedThemeId?.takeIf { id -> themes.any { it.id == id } }
                            current.copy(
                                themeItems = themes.toThemePathItems(),
                                selectedThemeId = selectedTheme,
                            )
                        }
                    }
            }
    }

    private fun observeLessonsForTheme(themeId: ThemeId) {
        lessonsJob?.cancel()
        lessonsJob =
            scope.launch {
                lessonRepository.observeByTheme(themeId)
                    .catch { error -> _state.update { it.copy(errorMessage = error.message?.let(UiMessage::Raw)) } }
                    .collect { lessons ->
                        _state.update { current ->
                            val selectedLesson =
                                current.selectedLessonId?.takeIf { id -> lessons.any { it.id == id } }
                            current.copy(
                                lessonItems = lessons.toLessonPathItems(),
                                selectedLessonId = selectedLesson,
                            )
                        }
                    }
            }
    }

    private fun clearHierarchyJobs() {
        questsJob?.cancel()
        sectionsJob?.cancel()
        themesJob?.cancel()
        lessonsJob?.cancel()
    }

    private suspend fun refreshActiveDraft(
        uid: String,
        selectedQuestionId: DraftQuestionId? = null,
        openNewQuestion: Boolean = false,
        showEditor: Boolean = false,
        difficulty: Difficulty? = null,
    ) {
        try {
            activeBundle = getActiveQuestDraft(uid).getOrNull()
            val editor =
                if (showEditor) {
                    activeBundle?.toEditorUiState(
                        selectedQuestionId = selectedQuestionId,
                        openNewQuestion = openNewQuestion,
                        difficulty = difficulty,
                    )
                } else {
                    null
                }
            // Редактор пересобран из сохранённого черновика — прежняя история правок описывала
            // предыдущую сессию и после сохранения продолжаться не может.
            editorHistory.clear()
            _state.update { current ->
                val installed = editor?.let(editorHistory::attach)
                activeBundle?.applyStructureToState(current, installed)
                    ?: current.copy(activeDraftTitle = null, editor = null)
            }
            activeBundle?.draft?.catalogId?.let(::observeQuestsForCatalog)
            observeDraftConflict(uid, activeBundle?.draft?.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            activeBundle = null
            observeDraftConflict(uid, draftId = null)
            _state.update { it.copy(activeDraftTitle = null, editor = null) }
        }
    }

    /**
     * Держит на экране ответ очереди по этому черновику (AD-14, AD-24).
     *
     * Поток, а не разовый запрос: конфликт появляется не по нажатию автора, а когда прогон очереди
     * получает отказ по версии, — и экран, открытый в этот момент, обязан об этом узнать.
     */
    private fun observeDraftConflict(
        uid: String,
        draftId: QuestDraftId?,
    ) {
        conflictJob?.cancel()
        if (draftId == null) {
            _state.update { it.copy(draftConflict = null) }
            return
        }
        conflictJob =
            scope.launch {
                observeQuestDraftConflict(uid, draftId)
                    .catch { _state.update { current -> current.copy(draftConflict = null) } }
                    .collect { conflict ->
                        _state.update { current ->
                            current.copy(
                                draftConflict =
                                    conflict?.let {
                                        DraftConflictUiState(
                                            serverVersion = it.serverVersion,
                                            expectedVersion = it.expectedVersion,
                                        )
                                    },
                            )
                        }
                    }
            }
    }

    private fun QuestAuthoringBundle.applyStructureToState(
        current: QuestCreateUiState,
        editor: QuestQuestionEditorUiState?,
    ): QuestCreateUiState {
        return current.copy(
            selectedCatalogId = draft.catalogId,
            selectedQuestId = draft.publicQuestId,
            defaultLanguage = draft.defaultLanguage,
            defaultDifficulty = draft.defaultDifficulty,
            activeDraftTitle = draft.title,
            editor = editor?.copy(isPrivateQuiz = draft.privateQuiz != null),
            errorMessage = null,
            // Загрузка существующего приватного квиза восстанавливает СОХРАНЁННЫЕ настройки,
            // а не дефолты заново (AD-29); у обычного квиза настроек нет — поля остаются
            // дефолтными и не показываются.
            privateAttemptLimit = draft.privateQuiz?.attemptLimit ?: current.privateAttemptLimit,
            privateShowResult = draft.privateQuiz?.showResult ?: current.privateShowResult,
            privateLinkHasExpiry = draft.privateQuiz?.linkExpiresAtMs != null,
            // Сохранённый момент хранится как есть: дни на экране — информация, а не источник
            // перезаявки (иначе суб-суточный срок молча продлевался бы до 1 дня).
            savedLinkExpiresAtMs = draft.privateQuiz?.linkExpiresAtMs,
            expiryEdited = false,
            privateLinkExpiresInDays =
                draft.privateQuiz?.linkExpiresAtMs
                    ?.let { expiry ->
                        val days = kotlin.math.ceil((expiry - timestampProvider.nowMs()).toDouble() / MILLIS_PER_DAY).toInt()
                        days.coerceAtLeast(1)
                    }
                    ?: current.privateLinkExpiresInDays,
        )
    }

    private fun selectQuestion(
        index: Int,
        difficulty: Difficulty,
    ) {
        val bundle = activeBundle ?: return
        val questions = bundle.sortedQuestionsForActiveLesson(difficulty)
        val normalizedIndex = index.coerceIn(0, questions.size)
        val selectedQuestionId = questions.getOrNull(normalizedIndex)?.id
        val editor =
            bundle.toEditorUiState(
                selectedQuestionId = selectedQuestionId,
                openNewQuestion = selectedQuestionId == null,
                difficulty = difficulty,
            )
        // Новый вопрос — новая сессия правки: история предыдущего вопроса чужому снимку не достаётся.
        editorHistory.clear()
        _state.update { it.copy(editor = editor?.let(editorHistory::attach), errorMessage = null) }
    }

    private fun updateEditor(transform: (QuestQuestionEditorUiState) -> QuestQuestionEditorUiState) {
        _state.update { current ->
            current.copy(editor = current.editor?.let(transform))
        }
    }

    /**
     * Содержательная правка вопроса: прежний снимок уходит в историю прежде, чем применить новую.
     *
     * Единица undo/redo — целый снимок редактора, поэтому текст и его метки восстанавливаются
     * вместе (UX-O6 §4, AD-27). Служебные переходы (сохранение, ошибки, превью) заводит
     * [updateEditor] — историю они не трогают.
     */
    private fun updateEditable(transform: (QuestQuestionEditorUiState) -> QuestQuestionEditorUiState) {
        _state.update { current ->
            val editor = current.editor ?: return@update current
            editorHistory.push(editor)
            current.copy(editor = editorHistory.attach(transform(editor)))
        }
    }

    override fun onEditorUndoClick() {
        val current = _state.value.editor ?: return
        val restored = editorHistory.undo(current) ?: return
        _state.update { it.copy(editor = editorHistory.attach(restored.clearTransientFeedback())) }
    }

    override fun onEditorRedoClick() {
        val current = _state.value.editor ?: return
        val restored = editorHistory.redo(current) ?: return
        _state.update { it.copy(editor = editorHistory.attach(restored.clearTransientFeedback())) }
    }

    private fun QuestQuestionEditorUiState.clearTransientFeedback(): QuestQuestionEditorUiState =
        copy(errorMessage = null, lastSavedMessage = null)

    private fun buildQuestionContent(editor: QuestQuestionEditorUiState): Result<QuestionContent> =
        runCatching {
            val imagePath = editor.imagePath.cleanNullable()
            val info = editor.info.cleanNullable()
            val questionId = editor.selectedQuestionId?.value ?: "draft-question-${editor.activeIndex + 1}"
            when (editor.type) {
                DraftQuestionType.SINGLE_CHOICE -> {
                    val options = editor.optionTexts.toOptions("opt")
                    val correctOptionId = OptionId("opt-${editor.correctSingleIndex}")
                    // Пустой вариант считается удалённым (toOptions его отбрасывает), поэтому
                    // ссылка на него — не техническая ошибка схемы, а незаполненное поле.
                    require(correctOptionId in options.map { it.id }) {
                        "Отметьте правильным один из заполненных вариантов"
                    }
                    QuestionContent.SingleChoice(
                        id = questionId,
                        difficulty = editor.difficulty,
                        text = editor.text.trim(),
                        imageUrl = imagePath,
                        options = options,
                        correctOptionId = correctOptionId,
                        info = info,
                    )
                }
                DraftQuestionType.SURVEY -> {
                    val options = editor.optionTexts.toOptions("opt")
                    QuestionContent.Survey(
                        id = questionId,
                        difficulty = editor.difficulty,
                        text = editor.text.trim(),
                        imageUrl = imagePath,
                        options = options,
                        // Several correct marks in a survey simply mean several answers are allowed.
                        allowMultiple = editor.correctMultipleIndexes.size > 1,
                        info = info,
                    )
                }
                DraftQuestionType.MULTIPLE_CHOICE -> {
                    val options = editor.optionTexts.toOptions("opt")
                    val optionIds = options.map { it.id }.toSet()
                    // Отметки на очищенных вариантах отбрасываем вместе с самими вариантами,
                    // иначе correctOptionIds ссылается на несуществующий вариант.
                    val correctOptionIds =
                        editor.correctMultipleIndexes
                            .map { OptionId("opt-$it") }
                            .filterTo(mutableSetOf()) { it in optionIds }
                    require(correctOptionIds.size >= MIN_MULTIPLE_CORRECT) {
                        "Отметьте правильными минимум два заполненных варианта"
                    }
                    QuestionContent.MultipleChoice(
                        id = questionId,
                        difficulty = editor.difficulty,
                        text = editor.text.trim(),
                        imageUrl = imagePath,
                        options = options,
                        correctOptionIds = correctOptionIds,
                        info = info,
                    )
                }
                DraftQuestionType.ORDERING ->
                    QuestionContent.Ordering(
                        id = questionId,
                        difficulty = editor.difficulty,
                        text = editor.text.trim(),
                        imageUrl = imagePath,
                        items =
                            editor.orderingItems.mapIndexedNotNull { index, value ->
                                value.trim().takeIf { it.isNotEmpty() }?.let { text ->
                                    QuestionContent.OrderItem(id = OptionId("ord-$index"), text = text)
                                }
                            },
                        info = info,
                    )
                DraftQuestionType.FILL_BLANK -> {
                    val orderedAnswers =
                        editor.resolveFillBlankAnswers()
                            ?: error("FillBlank text must contain **blank** markers or matching correct answers")
                    val candidates = orderedAnswers.toCandidateTexts(editor.fillBlankDistractors)
                    val runtimeText =
                        buildFillBlankRuntimeText(
                            text = editor.fillBlankText,
                            answers = orderedAnswers,
                        )
                            ?: error("FillBlank answer must be present in text")
                    val protectedSegments =
                        extractProtectedTextSegments(
                            text = editor.fillBlankText,
                            answers = orderedAnswers,
                        )
                    QuestionContent.FillBlank(
                        id = questionId,
                        difficulty = editor.difficulty,
                        text = runtimeText,
                        imageUrl = imagePath,
                        blanks =
                            orderedAnswers.mapIndexed { index, _ ->
                                QuestionContent.Blank(
                                    id = BlankId("blank-$index"),
                                    correctCandidateId = CandidateId("cand-$index"),
                                )
                            },
                        candidates =
                            candidates.mapIndexed { index, value ->
                                QuestionContent.Candidate(id = CandidateId("cand-$index"), text = value)
                            },
                        protectedTextSegments = protectedSegments,
                        info = info,
                    )
                }
            }
        }

    private fun QuestAuthoringBundle.toEditorUiState(
        selectedQuestionId: DraftQuestionId?,
        openNewQuestion: Boolean,
        difficulty: Difficulty?,
    ): QuestQuestionEditorUiState? {
        val lesson = lessons.sortedForEditor().firstOrNull() ?: return null
        val targetDifficulty =
            difficulty
                ?: selectedQuestionId?.let { id -> questions.firstOrNull { it.id == id }?.difficulty }
                ?: draft.defaultDifficulty
        val sortedQuestions = sortedQuestionsForLesson(lesson, targetDifficulty)
        val resolvedIndex =
            if (openNewQuestion) {
                sortedQuestions.size
            } else {
                selectedQuestionId
                    ?.let { id -> sortedQuestions.indexOfFirst { it.id == id } }
                    ?.takeIf { it >= 0 }
                    ?: 0
            }
        val selectedQuestion = sortedQuestions.getOrNull(resolvedIndex)
        val base =
            QuestQuestionEditorUiState(
                draftId = draft.id,
                draftTitle = draft.title,
                lessonId = lesson.id,
                questionItems =
                    sortedQuestions.mapIndexed { index, question ->
                        DraftQuestionListItem(
                            id = question.id,
                            number = index + 1,
                            title = question.text,
                            type = question.type,
                            difficulty = question.difficulty,
                        )
                    },
                activeIndex = resolvedIndex.coerceAtLeast(0),
                selectedQuestionId = selectedQuestion?.id,
                type = selectedQuestion?.type ?: DraftQuestionType.SINGLE_CHOICE,
                difficulty = selectedQuestion?.difficulty ?: targetDifficulty,
                language = selectedQuestion?.language ?: draft.defaultLanguage,
            )
        return selectedQuestion?.let { base.withQuestion(it) } ?: base
    }

    private fun QuestQuestionEditorUiState.withQuestion(question: DraftQuestion): QuestQuestionEditorUiState {
        val fallback =
            copy(
                selectedQuestionId = question.id,
                type = question.type,
                difficulty = question.difficulty,
                language = question.language,
                text = question.text,
                imagePath = question.imagePath.orEmpty(),
                fillBlankText = if (question.type == DraftQuestionType.FILL_BLANK) question.text else fillBlankText,
            )
        val content =
            question.payload
                ?.let {
                    questionContentParser
                        .parse(
                            payload = it,
                            fallbackId = question.id.value,
                            fallbackText = question.text,
                            fallbackDifficulty = question.difficulty,
                        )
                        .getOrNull()
                }
                ?: return fallback
        return fallback.withQuestionContent(content)
    }

    private fun QuestQuestionEditorUiState.withQuestionContent(content: QuestionContent): QuestQuestionEditorUiState =
        when (content) {
            is QuestionContent.SingleChoice -> {
                val correctIndex = content.options.indexOfFirst { it.id == content.correctOptionId }.coerceAtLeast(0)
                copy(
                    type = DraftQuestionType.SINGLE_CHOICE,
                    difficulty = content.difficulty,
                    text = content.text,
                    imagePath = content.imageUrl.orEmpty(),
                    info = content.info.orEmpty(),
                    optionTexts = content.options.map { it.text }.ensureEditorRows(),
                    correctSingleIndex = correctIndex,
                )
            }
            is QuestionContent.Survey ->
                copy(
                    type = DraftQuestionType.SURVEY,
                    difficulty = content.difficulty,
                    text = content.text,
                    imagePath = content.imageUrl.orEmpty(),
                    info = content.info.orEmpty(),
                    optionTexts = content.options.map { it.text }.ensureEditorRows(),
                    correctSingleIndex = 0,
                    correctMultipleIndexes = emptySet(),
                )
            is QuestionContent.MultipleChoice -> {
                val correctIndexes =
                    content.correctOptionIds.mapNotNull { correctId ->
                        content.options.indexOfFirst { it.id == correctId }.takeIf { it >= 0 }
                    }.toSet()
                copy(
                    type = DraftQuestionType.MULTIPLE_CHOICE,
                    difficulty = content.difficulty,
                    text = content.text,
                    imagePath = content.imageUrl.orEmpty(),
                    info = content.info.orEmpty(),
                    optionTexts = content.options.map { it.text }.ensureEditorRows(),
                    correctMultipleIndexes = correctIndexes,
                )
            }
            is QuestionContent.Ordering ->
                copy(
                    type = DraftQuestionType.ORDERING,
                    difficulty = content.difficulty,
                    text = content.text,
                    imagePath = content.imageUrl.orEmpty(),
                    info = content.info.orEmpty(),
                    orderingItems = content.items.map { it.text }.ensureEditorRows(),
                )
            is QuestionContent.FillBlank -> {
                val correctCandidateIds = content.blanks.map { it.correctCandidateId }.toSet()
                val correctAnswers =
                    content.blanks.mapNotNull { blank ->
                        content.candidates.firstOrNull { it.id == blank.correctCandidateId }?.text
                    }
                copy(
                    type = DraftQuestionType.FILL_BLANK,
                    difficulty = content.difficulty,
                    fillBlankText =
                        restoreFillBlankAuthorText(
                            runtimeText = content.text,
                            answers = correctAnswers,
                            protectedTextSegments = content.protectedTextSegments,
                        ),
                    imagePath = content.imageUrl.orEmpty(),
                    info = content.info.orEmpty(),
                    fillBlankAnswers =
                        correctAnswers
                            .map { answer ->
                                FillBlankAnswerItem(
                                    text = answer,
                                    isProtected =
                                        content.protectedTextSegments.any { segment ->
                                            segment.equals(answer, ignoreCase = true)
                                        },
                                )
                            }
                            .ensureFillBlankAnswerRows(),
                    fillBlankDistractors =
                        content.candidates
                            .filterNot { it.id in correctCandidateIds }
                            .map { it.text }
                            .ifEmpty { listOf("", "", "", "") },
                )
            }
        }

    private fun QuestAuthoringBundle.sortedQuestionsForActiveLesson(
        difficulty: Difficulty? = null,
    ): List<DraftQuestion> {
        val lesson = lessons.sortedForEditor().firstOrNull() ?: return emptyList()
        return sortedQuestionsForLesson(lesson, difficulty)
    }

    private fun QuestAuthoringBundle.sortedQuestionsForLesson(
        lesson: DraftLesson,
        difficulty: Difficulty? = null,
    ): List<DraftQuestion> =
        questions
            .asSequence()
            .filter { it.lessonId == lesson.id }
            .filter { difficulty == null || it.difficulty == difficulty }
            .sortedWith(compareBy<DraftQuestion> { it.order }.thenBy { it.id.value })
            .toList()

    private fun List<DraftLesson>.sortedForEditor(): List<DraftLesson> =
        sortedWith(compareBy<DraftLesson> { it.order }.thenBy { it.id.value })

    private fun DraftQuestionId.existingQuestionOrder(): Int? =
        activeBundle?.questions?.firstOrNull { it.id == this }?.order

    private fun nextQuestionOrder(lessonId: DraftLessonId): Int =
        activeBundle
            ?.questions
            ?.filter { it.lessonId == lessonId }
            ?.maxOfOrNull { it.order + 1 }
            ?: 0

    private fun QuestAuthoringBundle.targetLessonIds(target: QuestArenaTargetNode): Set<DraftLessonId> {
        val sortedSections = sections.sortedWith(compareBy { it.order })
        val sortedThemes = themes.sortedWith(compareBy { it.order })
        val sortedLessons = lessons.sortedForEditor()
        return when (target) {
            QuestArenaTargetNode.QUEST -> sortedLessons.mapTo(linkedSetOf()) { it.id }
            QuestArenaTargetNode.SECTION -> {
                val sectionId = sortedSections.firstOrNull()?.id
                if (sectionId == null) {
                    emptySet()
                } else {
                    val themeIds =
                        sortedThemes
                            .filter { it.sectionId == sectionId }
                            .mapTo(linkedSetOf()) { it.id }
                    sortedLessons.filter { it.themeId in themeIds }.mapTo(linkedSetOf()) { it.id }
                }
            }
            QuestArenaTargetNode.THEME -> {
                val themeId = sortedThemes.firstOrNull()?.id
                if (themeId == null) {
                    emptySet()
                } else {
                    sortedLessons.filter { it.themeId == themeId }.mapTo(linkedSetOf()) { it.id }
                }
            }
            QuestArenaTargetNode.LESSON -> sortedLessons.firstOrNull()?.let { linkedSetOf(it.id) }.orEmpty()
        }
    }
}

private fun String.cleanNullable(): String? = trim().takeIf { it.isNotEmpty() }

private fun List<String>.withValue(
    index: Int,
    value: String,
): List<String> =
    mapIndexed { itemIndex, itemValue ->
        if (itemIndex == index) value else itemValue
    }

private fun List<String>.ensureEditorRows(): List<String> =
    take(MAX_EDITOR_ROWS) + List((MIN_EDITOR_ROWS - size).coerceAtLeast(0)) { "" }

private fun <T> List<T>.removeAtOrSame(index: Int): List<T> = filterIndexed { itemIndex, _ -> itemIndex != index }

private fun Int.shiftAfterRemove(removedIndex: Int): Int =
    when {
        this > removedIndex -> this - 1
        this == removedIndex -> 0
        else -> this
    }

private fun Set<Int>.shiftAfterRemove(removedIndex: Int): Set<Int> =
    mapNotNull { index ->
        when {
            index == removedIndex -> null
            index > removedIndex -> index - 1
            else -> index
        }
    }.toSet()

private fun <T> List<T>.withValue(
    index: Int,
    transform: (T) -> T,
): List<T> =
    mapIndexed { itemIndex, itemValue ->
        if (itemIndex == index) transform(itemValue) else itemValue
    }

private fun List<FillBlankAnswerItem>.ensureFillBlankAnswerRows(): List<FillBlankAnswerItem> =
    take(MAX_FILL_BLANK_ANSWERS) + List((MIN_FILL_BLANK_ANSWERS - size).coerceAtLeast(0)) { FillBlankAnswerItem() }

private fun QuestCreateUiState.selectedQuestTitle(): String =
    selectedQuestId
        ?.let { id -> questItems.firstOrNull { it.id == id }?.title }
        ?: newQuestTitle.trim().ifBlank { "Новый квест" }

private fun QuestCreateUiState.selectedSectionTitle(): String =
    selectedSectionId
        ?.let { id -> sectionItems.firstOrNull { it.id == id }?.title }
        ?: newSectionTitle.trim().ifBlank { "Новый раздел" }

private fun QuestCreateUiState.selectedThemeTitle(): String =
    selectedThemeId
        ?.let { id -> themeItems.firstOrNull { it.id == id }?.title }
        ?: newThemeTitle.trim().ifBlank { "Новая тема" }

private fun QuestCreateUiState.selectedLessonTitle(): String =
    selectedLessonId
        ?.let { id -> lessonItems.firstOrNull { it.id == id }?.title }
        ?: newLessonTitle.trim().ifBlank { "Новый урок" }

private fun QuestCreateUiState.structureValidationErrorRes(): Int? =
    when {
        selectedQuestId == null && newQuestTitle.isBlank() -> R.string.qa_error_enter_quest_title
        selectedSectionId == null && newSectionTitle.isBlank() -> R.string.qa_error_enter_section_title
        selectedThemeId == null && newThemeTitle.isBlank() -> R.string.qa_error_enter_theme_title
        selectedLessonId == null && newLessonTitle.isBlank() -> R.string.qa_error_enter_lesson_title
        else -> null
    }

/**
 * Подпись отказа по его причине.
 *
 * Прежде сюда попадал любой `IllegalArgumentException` и превращался в одну строку про
 * недостающие вопросы. Автору с готовым квестом, отклонённым по состоянию черновика,
 * предлагалось чинить исправные вопросы — сообщение уводило от настоящей причины. Теперь
 * каждая причина названа своим типом на стороне domain и своей подписью здесь.
 */
private fun Throwable?.arenaSubmissionErrorMessage(): UiMessage =
    when (this) {
        is QuestArenaSubmissionRejection.DraftNotFound ->
            UiMessage.Res(R.string.qa_error_arena_draft_missing)

        is QuestArenaSubmissionRejection.DraftNotSubmittable ->
            UiMessage.Res(R.string.qa_error_arena_draft_not_submittable)

        is QuestArenaSubmissionRejection.UnsupportedTargetShelf ->
            UiMessage.Res(R.string.qa_error_arena_bad_shelf)

        is QuestArenaSubmissionRejection.NoLessonsChosen ->
            UiMessage.Res(R.string.qa_error_no_lessons_in_node)

        is QuestArenaSubmissionRejection.LessonOutsideDraft ->
            UiMessage.Res(R.string.qa_error_arena_foreign_lesson)

        is QuestArenaSubmissionRejection.LessonNotReady ->
            UiMessage.Res(R.string.qa_error_arena_missing_questions)

        else -> this?.message?.let(UiMessage::Raw) ?: UiMessage.Res(R.string.qa_error_arena_enqueue_failed)
    }

/**
 * Where a finished quest is published — derived from the catalog's type rather than from its id.
 * Comparing against the literal "courses" tied this decision to one particular catalog and had to
 * be kept in sync by hand with the quest list and the seed scripts.
 */
private fun QuestCreateUiState.targetShelf(): String =
    when (selectedCatalogType()) {
        QuestType.COURSE -> QUEST_ARCHIVE_TARGET_SHELF
        QuestType.REGULAR, QuestType.SURVEY -> QUEST_ARENA_TARGET_SHELF
    }

private fun QuestCreateUiState.selectedCatalogType(): QuestType =
    catalogs.firstOrNull { it.id == selectedCatalogId }?.questType ?: QuestType.REGULAR

private fun String.appendFillBlankMarker(kind: FillBlankMarkerKind): String {
    val marker =
        when (kind) {
            FillBlankMarkerKind.BLANK -> "**blank**"
            FillBlankMarkerKind.PROTECTED -> "*text*"
            FillBlankMarkerKind.BLANK_PROTECTED -> "***blank***"
        }
    val base = trimEnd()
    return if (base.isBlank()) marker else "$base $marker"
}

private fun QuestQuestionEditorUiState.resolveFillBlankAnswers(): List<FillBlankAnswerSpec>? {
    val markupAnswers = extractFillBlankAnswers(fillBlankText)
    if (markupAnswers.isNotEmpty()) return markupAnswers

    val manualAnswers =
        fillBlankAnswers.mapNotNull { answer ->
            answer.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
                FillBlankAnswerSpec(
                    text = text,
                    isProtected = answer.isProtected,
                )
            }
        }
    return orderFillBlankAnswersByText(
        text = fillBlankText,
        answers = manualAnswers,
    )
}

private fun QuestQuestionEditorUiState.isEmptyNewQuestion(): Boolean =
    selectedQuestionId == null &&
        text.isBlank() &&
        imagePath.isBlank() &&
        info.isBlank() &&
        optionTexts.all { it.isBlank() } &&
        orderingItems.all { it.isBlank() } &&
        fillBlankText.isBlank() &&
        fillBlankAnswers.all { it.text.isBlank() } &&
        fillBlankDistractors.all { it.isBlank() }

private fun QuestQuestionEditorUiState.withSyncedFillBlankText(value: String): QuestQuestionEditorUiState {
    val markupAnswers = extractFillBlankAnswers(value)
    return copy(
        fillBlankText = value,
        fillBlankAnswers =
            if (markupAnswers.isEmpty()) {
                fillBlankAnswers
            } else {
                markupAnswers
                    .map { answer ->
                        FillBlankAnswerItem(
                            text = answer.text,
                            isProtected = answer.isProtected,
                        )
                    }
                    .ensureFillBlankAnswerRows()
            },
    )
}

private fun List<FillBlankAnswerSpec>.toCandidateTexts(distractors: List<String>): List<String> {
    val correctAnswers =
        map { it.text.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    val normalizedDistractors =
        distractors
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { distractor -> correctAnswers.any { it.equals(distractor, ignoreCase = true) } }
            .distinctBy { it.lowercase() }
    val provided = correctAnswers.size + normalizedDistractors.size
    val targetSize =
        if (provided <= MIN_FILL_BLANK_CANDIDATES) {
            MIN_FILL_BLANK_CANDIDATES
        } else {
            MAX_FILL_BLANK_CANDIDATES
        }
    // Раньше недостающие варианты добивались строками "extra-1", "extra-2"… и попадали
    // в опубликованный вопрос как настоящие ответы. Просим автора дозаполнить сам.
    require(provided == targetSize) {
        val missing = targetSize - provided
        if (missing > 0) {
            "Нужно $targetSize вариантов (ответы и дистракторы), сейчас $provided — добавьте ещё $missing"
        } else {
            "Нужно $targetSize вариантов (ответы и дистракторы), сейчас $provided — уберите ${-missing}"
        }
    }
    return correctAnswers + normalizedDistractors
}

private fun List<Section>.toSectionPathItems(): List<SectionPathItem> =
    sortedWith(compareBy<Section> { it.order }.thenBy { it.title.lowercase() })
        .map { SectionPathItem(id = it.id, title = it.title) }

private fun List<Theme>.toThemePathItems(): List<ThemePathItem> =
    sortedWith(compareBy<Theme> { it.order }.thenBy { it.title.lowercase() })
        .map { ThemePathItem(id = it.id, title = it.title) }

private fun List<Lesson>.toLessonPathItems(): List<LessonPathItem> =
    sortedWith(compareBy<Lesson> { it.order }.thenBy { it.title.lowercase() })
        .map { LessonPathItem(id = it.id, title = it.title) }

private fun List<String>.toOptions(prefix: String): List<QuestionContent.Option> =
    mapIndexedNotNull { index, value ->
        value.trim().takeIf { it.isNotEmpty() }?.let { text ->
            QuestionContent.Option(id = OptionId("$prefix-$index"), text = text)
        }
    }
