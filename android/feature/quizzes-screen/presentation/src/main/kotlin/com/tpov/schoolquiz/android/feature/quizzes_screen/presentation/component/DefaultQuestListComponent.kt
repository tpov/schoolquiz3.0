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
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
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
    private val setPublicQuestShelf: SetPublicQuestShelfUseCase,
    private val questContentSync: suspend (QuestId) -> Result<Unit>,
    private val navigation: StackNavigation<QuizzesConfig>,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, QuestListComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val catalogId = CatalogId(config.catalogId)
    override val titles: List<String> = config.titles
    override val mode: QuestListMode = config.mode
    override val selectionTargetShelf: String? = config.selectionTargetShelf
    private val forcedLessonMode = config.forcedLessonMode
    private val isCoursesCatalog = config.questType == QuestType.COURSE
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
        scope.launch {
            observeSourceQuests().flatMapLatest { quests ->
                if (quests.isEmpty()) {
                    flowOf(QuestListUiState.Empty)
                } else {
                    combine(
                        downloadingQuestIds,
                        completedQuestIds,
                        questAvailabilityFlow(quests),
                    ) { downloadingIds, completedIds, downloaded ->
                        mapToUi(
                            quests = quests,
                            downloadingIds = downloadingIds,
                            completedIds = completedIds,
                            downloaded = downloaded,
                        )
                    }
                }
            }
                .catch { /* log */ }
                .collect { _uiState.value = it }
        }
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onQuestClick(quest: QuestDisplayItem) {
        val targetShelf = selectionTargetShelf
        if (targetShelf != null) {
            setQuestShelf(quest, targetShelf, dismissOnSuccess = true)
            return
        }
        navigation.pushNew(
            QuizzesConfig.SectionList(
                questId = quest.id.value,
                titles = titles + listOf(quest.title),
                forcedLessonMode = forcedLessonMode,
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

    private fun setQuestShelf(
        quest: QuestDisplayItem,
        targetShelf: String,
        dismissOnSuccess: Boolean,
    ) {
        scope.launch {
            setPublicQuestShelf.execute(quest.id, targetShelf)
                .onSuccess {
                    questRepository.refreshByIds(setOf(quest.id))
                    if (dismissOnSuccess) {
                        navigation.popToFirst()
                    }
                }
                .onFailure { error ->
                    Log.w(
                        TAG,
                        "Failed to set quest ${quest.id.value} public shelf to $targetShelf",
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
            QuestListUiState.Loaded(items)
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
