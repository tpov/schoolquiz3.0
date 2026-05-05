package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.FillBlankAnswerSpec
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.buildFillBlankRuntimeText
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.extractFillBlankAnswers
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.extractProtectedTextSegments
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.orderFillBlankAnswersByText
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.removeFillBlankAnswerMarker
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.restoreFillBlankAuthorText
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.upsertFillBlankAnswerMarker
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.mapper.toAuthoringDisplayItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DraftQuestionListItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankAnswerItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankMarkerKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.LessonPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestCreateUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestQuestionEditorUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.SectionPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ThemePathItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
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
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.CreateQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetActiveQuestDraftUseCase
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
private const val MIN_FILL_BLANK_ANSWERS = 1
private const val MAX_FILL_BLANK_ANSWERS = 3
private const val MAX_FILL_BLANK_CANDIDATES = 10
private const val MIN_FILL_BLANK_CANDIDATES = 5
private const val FIRST_GENERATED_DISTRACTOR_INDEX = 1
private val STRUCTURE_SHELVES = listOf("home", "archive", "arena")

@Suppress("LargeClass", "LongParameterList", "TooManyFunctions")
class DefaultQuestCreateComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val createQuestDraft: CreateQuestDraftUseCase,
    private val getActiveQuestDraft: GetActiveQuestDraftUseCase,
    private val saveDraftQuestion: SaveDraftQuestionUseCase,
    private val submitQuestDraftToArena: SubmitQuestDraftToArenaUseCase,
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
    private var ownerUid: String? = null
    private var activeBundle: QuestAuthoringBundle? = null
    private var questsJob: Job? = null
    private var sectionsJob: Job? = null
    private var themesJob: Job? = null
    private var lessonsJob: Job? = null

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

    override fun onSectionTitleChanged(value: String) {
        _state.update { it.copy(newSectionTitle = value, errorMessage = null) }
    }

    override fun onThemeTitleChanged(value: String) {
        _state.update { it.copy(newThemeTitle = value, errorMessage = null) }
    }

    override fun onLessonTitleChanged(value: String) {
        _state.update { it.copy(newLessonTitle = value, errorMessage = null) }
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
        val uid = ownerUid
        val draftId = activeBundle?.draft?.id
        when {
            uid == null ->
                _state.update {
                    it.copy(
                        errorMessage = "Авторизация еще не готова",
                        arenaMessage = null,
                    )
                }
            draftId == null ->
                _state.update {
                    it.copy(
                        errorMessage = "Сначала создайте черновик",
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
                    val result = submitQuestDraftToArena(draftId)
                    if (result.isSuccess) {
                        refreshActiveDraft(uid)
                        _state.update {
                            it.copy(
                                isSubmittingToArena = false,
                                errorMessage = null,
                                arenaMessage = "Квест поставлен в очередь отправки на арену",
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

    override fun onQuestionsClick(difficulty: Difficulty) {
        val snapshot = _state.value.copy(defaultDifficulty = difficulty)
        _state.update { it.copy(defaultDifficulty = difficulty, errorMessage = null) }
        if (snapshot.hasActiveDraft) {
            openQuestions(difficulty)
        } else {
            createDraft(
                snapshot = snapshot,
                showEditor = true,
                editorDifficulty = difficulty,
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
                        errorMessage = result.exceptionOrNull()?.message ?: "Не удалось сохранить черновик",
                    )
                }
            }
        }
    }

    private fun openQuestions(difficulty: Difficulty) {
        scope.launch {
            val uid = ownerUid
            if (uid == null) {
                _state.update { it.copy(errorMessage = "Авторизация еще не готова") }
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
        val structureError = if (requireStructureTitles) snapshot.structureValidationError() else null
        return when {
            uid == null -> {
                _state.update { it.copy(errorMessage = "Авторизация еще не готова") }
                null
            }
            catalogId == null -> {
                _state.update { it.copy(errorMessage = "Выберите каталог") }
                null
            }
            structureError != null -> {
                _state.update { it.copy(errorMessage = structureError) }
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
                    defaultDifficulty = snapshot.defaultDifficulty,
                    sectionTitle = snapshot.selectedSectionTitle(),
                    themeTitle = snapshot.selectedThemeTitle(),
                    lessonTitle = snapshot.selectedLessonTitle(),
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
        updateEditor { it.copy(type = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionDifficultySelected(value: Difficulty) {
        updateEditor { it.copy(difficulty = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionLanguageChanged(value: String) {
        updateEditor { it.copy(language = value.trim(), errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionTextChanged(value: String) {
        updateEditor { it.copy(text = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionImagePathChanged(value: String) {
        updateEditor { it.copy(imagePath = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onQuestionInfoChanged(value: String) {
        updateEditor { it.copy(info = value, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onOptionTextChanged(
        index: Int,
        value: String,
    ) {
        updateEditor {
            it.copy(
                optionTexts = it.optionTexts.withValue(index, value),
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onOptionAdded() {
        updateEditor {
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
        updateEditor {
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
        updateEditor { it.copy(correctSingleIndex = index, errorMessage = null, lastSavedMessage = null) }
    }

    override fun onMultipleCorrectToggled(index: Int) {
        updateEditor {
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
        updateEditor {
            it.copy(
                orderingItems = it.orderingItems.withValue(index, value),
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onOrderingItemAdded() {
        updateEditor {
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
        updateEditor {
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
        updateEditor {
            it.withSyncedFillBlankText(value)
                .copy(errorMessage = null, lastSavedMessage = null)
        }
    }

    override fun onFillBlankMarkerAdded(kind: FillBlankMarkerKind) {
        updateEditor {
            it.withSyncedFillBlankText(it.fillBlankText.appendFillBlankMarker(kind))
                .copy(errorMessage = null, lastSavedMessage = null)
        }
    }

    override fun onFillBlankAnswerChanged(
        index: Int,
        value: String,
    ) {
        updateEditor {
            val currentAnswer = it.fillBlankAnswers.getOrNull(index) ?: return@updateEditor it
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
        updateEditor {
            val currentAnswer = it.fillBlankAnswers.getOrNull(index) ?: return@updateEditor it
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
        updateEditor {
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
        updateEditor {
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
        updateEditor {
            it.copy(
                fillBlankDistractors = it.fillBlankDistractors.withValue(index, value),
                errorMessage = null,
                lastSavedMessage = null,
            )
        }
    }

    override fun onFillBlankDistractorAdded() {
        updateEditor {
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
        updateEditor {
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
                        errorMessage = it.autosaveValidationMessage(),
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
            updateEditor { it.copy(errorMessage = "Авторизация еще не готова") }
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
                    errorMessage = contentResult.exceptionOrNull()?.message ?: "Проверьте поля вопроса",
                )
            }
            return null
        }

        val payload = questionJson.encodeToString<QuestionContent>(content)
        val result =
            saveDraftQuestion(
                SaveDraftQuestionCommand(
                    draftId = editor.draftId,
                    lessonId = editor.lessonId,
                    questionId = editor.selectedQuestionId,
                    type = editor.type,
                    language = editor.language.ifBlank { "ru" },
                    difficulty = editor.difficulty,
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
                            if (isNewQuestion) "Вопрос создан" else "Вопрос сохранен"
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
                errorMessage = result.exceptionOrNull()?.message ?: "Не удалось сохранить вопрос",
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
                    .catch { error -> _state.update { it.copy(errorMessage = error.message) } }
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
                    .catch { error -> _state.update { it.copy(errorMessage = error.message) } }
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
                    .catch { error -> _state.update { it.copy(errorMessage = error.message) } }
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
                    .catch { error -> _state.update { it.copy(errorMessage = error.message) } }
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
            _state.update {
                activeBundle?.applyStructureToState(it, editor) ?: it.copy(activeDraftTitle = null, editor = null)
            }
            activeBundle?.draft?.catalogId?.let(::observeQuestsForCatalog)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            activeBundle = null
            _state.update { it.copy(activeDraftTitle = null, editor = null) }
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
            editor = editor,
            errorMessage = null,
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
        _state.update { it.copy(editor = editor, errorMessage = null) }
    }

    private fun updateEditor(transform: (QuestQuestionEditorUiState) -> QuestQuestionEditorUiState) {
        _state.update { current ->
            current.copy(editor = current.editor?.let(transform))
        }
    }

    private fun buildQuestionContent(editor: QuestQuestionEditorUiState): Result<QuestionContent> =
        runCatching {
            val imagePath = editor.imagePath.cleanNullable()
            val info = editor.info.cleanNullable()
            val questionId = editor.selectedQuestionId?.value ?: "draft-question-${editor.activeIndex + 1}"
            when (editor.type) {
                DraftQuestionType.SINGLE_CHOICE ->
                    QuestionContent.SingleChoice(
                        id = questionId,
                        difficulty = editor.difficulty,
                        text = editor.text.trim(),
                        imageUrl = imagePath,
                        options = editor.optionTexts.toOptions("opt"),
                        correctOptionId = OptionId("opt-${editor.correctSingleIndex}"),
                        info = info,
                    )
                DraftQuestionType.MULTIPLE_CHOICE ->
                    QuestionContent.MultipleChoice(
                        id = questionId,
                        difficulty = editor.difficulty,
                        text = editor.text.trim(),
                        imageUrl = imagePath,
                        options = editor.optionTexts.toOptions("opt"),
                        correctOptionIds = editor.correctMultipleIndexes.map { OptionId("opt-$it") }.toSet(),
                        info = info,
                    )
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
                            title = question.text.ifBlank { question.type.title },
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
}

private val DraftQuestionType.title: String
    get() =
        when (this) {
            DraftQuestionType.SINGLE_CHOICE -> "Один ответ"
            DraftQuestionType.MULTIPLE_CHOICE -> "Несколько ответов"
            DraftQuestionType.ORDERING -> "Порядок"
            DraftQuestionType.FILL_BLANK -> "Пропуск"
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

private fun QuestCreateUiState.structureValidationError(): String? =
    when {
        selectedQuestId == null && newQuestTitle.isBlank() -> "Введите название квеста"
        selectedSectionId == null && newSectionTitle.isBlank() -> "Введите название раздела"
        selectedThemeId == null && newThemeTitle.isBlank() -> "Введите название темы"
        selectedLessonId == null && newLessonTitle.isBlank() -> "Введите название урока"
        else -> null
    }

private fun Throwable?.arenaSubmissionErrorMessage(): String =
    when (this) {
        is IllegalArgumentException -> "Для арены нужны сохраненные легкие и сложные вопросы без ошибок"
        else -> this?.message ?: "Не удалось поставить квест в очередь арены"
    }

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

private fun QuestQuestionEditorUiState.autosaveValidationMessage(): String =
    when (type) {
        DraftQuestionType.SINGLE_CHOICE -> "Нужны вопрос, два варианта и правильный ответ"
        DraftQuestionType.MULTIPLE_CHOICE -> "Нужны вопрос, два варианта и минимум два правильных ответа"
        DraftQuestionType.ORDERING -> "Нужны вопрос и минимум два элемента"
        DraftQuestionType.FILL_BLANK -> "Нужны 1-3 пропуска через **текст** или правильные ответы в тексте"
    }

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
    val targetSize =
        if (correctAnswers.size + normalizedDistractors.size <= MIN_FILL_BLANK_CANDIDATES) {
            MIN_FILL_BLANK_CANDIDATES
        } else {
            MAX_FILL_BLANK_CANDIDATES
        }
    val generatedDistractors =
        generateSequence(FIRST_GENERATED_DISTRACTOR_INDEX) { it + 1 }
            .map { "extra-$it" }
            .filterNot { generated -> correctAnswers.any { it.equals(generated, ignoreCase = true) } }
            .filterNot { generated -> normalizedDistractors.any { it.equals(generated, ignoreCase = true) } }
            .take((targetSize - correctAnswers.size - normalizedDistractors.size).coerceAtLeast(0))
            .toList()
    return correctAnswers + normalizedDistractors + generatedDistractors
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
