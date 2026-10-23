package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import android.util.Log
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.popToFirst
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.PrivateQuestItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestShelfSyncMark
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.toShelfSyncMark
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.PrivateQuizSettings
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.RetirePublicQuestUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
class DefaultQuestListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.QuestList,
    private val questRepository: QuestRepository,
    private val sectionRepository: SectionRepository,
    private val themeRepository: ThemeRepository,
    private val lessonRepository: LessonRepository,
    private val questionRepository: QuestionRepository,
    private val authRepository: AuthRepository,
    private val setPublicQuestShelf: SetPublicQuestShelfUseCase,
    private val retirePublicQuest: RetirePublicQuestUseCase,
    private val questContentSync: suspend (QuestId) -> Result<Unit>,
    /** Приватная категория (AD-29): сохранённые приватные квизы владельца, тап — дверь прогона. */
    private val observePrivateQuests: (String) -> Flow<List<PrivateQuestItem>> = { flowOf(emptyList()) },
    private val navigation: StackNavigation<QuizzesConfig>,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, QuestListComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val catalogId = CatalogId(config.catalogId)
    override val breadcrumbs: List<BreadcrumbRoot> = config.breadcrumbs
    override val mode: QuestListMode = config.mode
    override val selectionTargetShelf: String? = config.selectionTargetShelf
    private val forcedLessonMode = config.forcedLessonMode
    private val questType = config.questType
    private val isCoursesCatalog = questType == QuestType.COURSE

    /** Приватная категория одна и засеяна техническим id (AD-29); questType у неё REGULAR. */
    private val isPrivateCatalog = config.catalogId == PrivateQuizSettings.PRIVATE_QUIZ_CATALOG_ID
    private val privateCatalogId = config.catalogId
    private val sourceShelf =
        when {
            mode == QuestListMode.Archive -> ARCHIVE_SHELF
            mode == QuestListMode.Arena -> config.shelf
            isCoursesCatalog -> ARCHIVE_SHELF
            else -> config.shelf
        }

    private val _uiState = MutableValue<QuestListUiState>(QuestListUiState.Loading)
    override val uiState: Value<QuestListUiState> = _uiState
    private val downloadingQuestIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    private val completedQuestIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    private var currentQuestItems: List<QuestDisplayItem> = emptyList()

    init {
        if (isPrivateCatalog) {
            observePrivateQuestsFlow()
        } else {
            observePublicQuests()
        }
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    /** Тап по приватному квизу — дверь прогона; без аккаунта входа не существует (AD-29). */
    override fun onPrivateQuestClick(quest: PrivateQuestItem) {
        scope.launch {
            val ownerUid = authRepository.currentUid() ?: return@launch
            navigation.pushNew(
                QuizzesConfig.PrivateQuizRunner(
                    ownerUid = ownerUid,
                    catalogId = privateCatalogId,
                    questId = quest.questId,
                    title = quest.title,
                    breadcrumbs = breadcrumbs + BreadcrumbRoot.Dynamic(quest.title),
                ),
            )
        }
    }

    /** Приватная категория читает СОХРАНЁННЫЕ приватные квизы владельца, а не публичные полки. */
    private fun observePrivateQuestsFlow() {
        scope.launch {
            authRepository.observeUid().flatMapLatest { uid ->
                if (uid == null) flowOf(emptyList()) else observePrivateQuests(uid)
            }
                .catch { /* экран пустоты честнее выдуманного списка */ }
                .collect { items ->
                    _uiState.value =
                        if (items.isEmpty()) QuestListUiState.Empty else QuestListUiState.PrivateQuests(items)
                }
        }
    }

    private fun observePublicQuests() {
        scope.launch {
            observeSourceQuests().flatMapLatest { quests ->
                if (quests.isEmpty()) {
                    flowOf(QuestListUiState.Empty)
                } else {
                    combine(
                        downloadingQuestIds,
                        completedQuestIds,
                        questAvailabilityFlow(quests),
                        shelfSyncFlow(quests),
                    ) { downloadingIds, completedIds, downloaded, shelfSync ->
                        mapToUi(
                            quests = quests,
                            downloadingIds = downloadingIds,
                            completedIds = completedIds,
                            downloaded = downloaded,
                            shelfSync = shelfSync,
                        )
                    }
                }
            }
                .catch { /* log */ }
                .collect { _uiState.value = it }
        }
    }

    override fun onQuestClick(quest: QuestDisplayItem) {
        // В приватной категории публичных строк нет: тап по приватному квизу — отдельный вход.
        if (isPrivateCatalog) return
        val targetShelf = selectionTargetShelf
        if (targetShelf != null) {
            setQuestShelf(quest, targetShelf, dismissOnSuccess = true)
            return
        }
        navigation.pushNew(
            QuizzesConfig.SectionList(
                questId = quest.id.value,
                breadcrumbs = breadcrumbs + BreadcrumbRoot.Dynamic(quest.title),
                forcedLessonMode = forcedLessonMode,
                questType = questType,
            ),
        )
    }

    override fun onQuestDownloadClick(quest: QuestDisplayItem) {
        if (!quest.isDownloadable || quest.isDownloading) return
        scope.launch {
            downloadingQuestIds.update { it + quest.id.value }
            completedQuestIds.update { it - quest.id.value }
            try {
                val result = questContentSync(quest.id)
                if (result.isSuccess) {
                    completedQuestIds.update { it + quest.id.value }
                }
            } finally {
                downloadingQuestIds.update { it - quest.id.value }
            }
        }
    }

    override fun onShareClick(quest: QuestDisplayItem) {
        // Intent dispatched from QuestListScreen (ADR-QS-08)
    }

    override fun onRandomQuestClick() {
        if (mode != QuestListMode.Arena) return
        currentQuestItems.randomOrNull()?.let(::onQuestClick)
    }

    override fun onSetShelfClick(
        quest: QuestDisplayItem,
        targetShelf: String,
    ) {
        setQuestShelf(quest, targetShelf, dismissOnSuccess = false)
    }

    override fun onRetireClick(quest: QuestDisplayItem) {
        scope.launch {
            retirePublicQuest(quest.id)
                .onSuccess {
                    // The refresh sees the empty shelf list and deletes the local row.
                    questRepository.refreshByIds(setOf(quest.id))
                }
                .onFailure { error ->
                    Log.w(TAG, "Failed to retire quest ${quest.id.value}", error)
                }
        }
    }

    /**
     * Перенос на полку — намерение в очередь, а не вызов сервера (история 5.4).
     *
     * Локальная строка не трогается и перечитывать её здесь нечего: полка серверно-защищена
     * (AD-25), новая приедет журналом изменений, когда сервер применит перенос. До этого карточка
     * показывает пометку «ждёт отправки» по ответу очереди, а не квест на новой полке.
     */
    private fun setQuestShelf(
        quest: QuestDisplayItem,
        targetShelf: String,
        dismissOnSuccess: Boolean,
    ) {
        scope.launch {
            // Без аккаунта намерению некому принадлежать (AD-8): полки и так видны только
            // разработчику, так что кнопка сюда без входа не приводит.
            val ownerUid = authRepository.currentUid() ?: return@launch
            setPublicQuestShelf.execute(ownerUid, quest.id, targetShelf)
                .onSuccess {
                    if (dismissOnSuccess) {
                        navigation.popToFirst()
                    }
                }
                .onFailure { error ->
                    Log.w(
                        TAG,
                        "Failed to queue quest ${quest.id.value} public shelf move to $targetShelf",
                        error,
                    )
                }
        }
    }

    private fun mapToUi(
        quests: List<Quest>,
        downloadingIds: Set<String>,
        completedIds: Set<String>,
        downloaded: Map<QuestId, Boolean>,
        shelfSync: Map<QuestId, QuestShelfSyncMark>,
    ): QuestListUiState {
        val items =
            quests
                .filter { quest -> shouldShowQuest(quest, downloaded[quest.id] == true) }
                .map { quest ->
                    val isDownloading = quest.id.value in downloadingIds
                    val isCompleted = quest.id.value in completedIds
                    val isDownloaded = downloaded[quest.id] == true
                    val showPersistentDownloadState = mode == QuestListMode.Archive
                    quest.toQuestDisplayItem(
                        isDownloadable = quest.archived && !isDownloaded && !isCompleted && !isDownloading,
                        isDownloading = isDownloading,
                        isDownloadComplete =
                            if (showPersistentDownloadState) {
                                isDownloaded || isCompleted
                            } else {
                                isCompleted
                            },
                    )
                }
                .sortedForMode()
        currentQuestItems = items
        return if (items.isEmpty()) {
            QuestListUiState.Empty
        } else {
            QuestListUiState.Loaded(
                quests = items,
                shelfSync = shelfSync.filterKeys { id -> items.any { it.id == id } },
            )
        }
    }

    private fun List<QuestDisplayItem>.sortedForMode(): List<QuestDisplayItem> =
        when (mode) {
            QuestListMode.Arena,
            QuestListMode.Archive,
            ->
                sortedByDescending { it.averageRating ?: NO_RATING_SORT_VALUE }
            else -> this
        }

    private fun shouldShowQuest(
        quest: Quest,
        isDownloaded: Boolean,
    ): Boolean =
        when {
            isCoursesCatalog && mode == QuestListMode.Home -> false
            mode == QuestListMode.Archive -> quest.archived
            mode == QuestListMode.Arena -> !quest.archived
            isCoursesCatalog -> !quest.archived || isDownloaded
            else -> true
        }

    private fun observeSourceQuests() =
        if (isCoursesCatalog && mode == QuestListMode.Home) {
            flowOf(emptyList())
        } else {
            questRepository.observeByCatalog(catalogId, sourceShelf)
        }

    /**
     * Пометки о неподтверждённых переносах — по ответу очереди на ссылку квеста (история 4.4).
     *
     * Подписка переподписывается на смену аккаунта: записи очереди принадлежат uid, и чужие
     * пометки после выхода остаться на экране не должны. Без аккаунта пометок нет.
     */
    private fun shelfSyncFlow(quests: List<Quest>): Flow<Map<QuestId, QuestShelfSyncMark>> =
        authRepository.observeUid().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyMap())
            } else {
                combine(
                    quests.map { quest ->
                        questRepository.observeShelfChangeState(uid, quest.id).map { state ->
                            quest.id to state.toShelfSyncMark()
                        }
                    },
                ) { entries ->
                    entries.mapNotNull { (id, mark) -> mark?.let { id to it } }.toMap()
                }
            }
        }

    private fun questAvailabilityFlow(quests: List<Quest>) =
        combine(
            quests.map { quest ->
                if (quest.archived) {
                    questDownloadedFlow(quest)
                } else {
                    flowOf(quest.id to true)
                }
            },
        ) { entries -> entries.toMap() }

    private fun questDownloadedFlow(quest: Quest) =
        sectionRepository.observeByQuest(quest.id)
            .flatMapLatest(::themesFlow)
            .flatMapLatest(::lessonsFlow)
            .flatMapLatest { lessons ->
                if (lessons.isEmpty()) {
                    flowOf(false)
                } else {
                    combine(
                        lessons.map { lesson ->
                            questionRepository.observeByLesson(lesson.id).map { questions ->
                                questions.isNotEmpty()
                            }
                        },
                    ) { flags -> flags.all { it } }
                }
            }
            .map { isDownloaded -> quest.id to isDownloaded }

    private fun themesFlow(sections: List<Section>) =
        if (sections.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(sections.map { section -> themeRepository.observeBySection(section.id) }) { themes ->
                themes.flatMap { it }
            }
        }

    private fun lessonsFlow(themes: List<Theme>) =
        if (themes.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(themes.map { theme -> lessonRepository.observeByTheme(theme.id) }) { lessons ->
                lessons.flatMap { it }
            }
        }
}

private const val ARCHIVE_SHELF = "archive"
private const val NO_RATING_SORT_VALUE = -1f
private const val TAG = "QuestListComponent"
