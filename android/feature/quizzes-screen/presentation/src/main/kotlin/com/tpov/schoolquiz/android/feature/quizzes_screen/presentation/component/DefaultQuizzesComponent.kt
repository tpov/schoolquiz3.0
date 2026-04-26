package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.popToFirst
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer

class DefaultQuizzesComponent(
    componentContext: ComponentContext,
    private val questRepository: QuestRepository,
    private val sectionRepository: SectionRepository,
    private val themeRepository: ThemeRepository,
    private val lessonRepository: LessonRepository,
    private val mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, QuizzesComponent {

    private val navigation = StackNavigation<QuizzesConfig>()

    // saveStack/restoreStack overload: runCatching wraps SerializationException on corrupted
    // process-death state so the stack falls back to [Idle] instead of crashing on launch.
    // Spec: docs/features/quizzes-screen/plan/phase-03/overview.md Problem 6 / AC#21.
    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> =
        childStack(
            source = navigation,
            initialStack = { listOf(QuizzesConfig.Idle) },
            saveStack = { stack ->
                SerializableContainer(
                    value = stack,
                    strategy = ListSerializer(QuizzesConfig.serializer()),
                )
            },
            restoreStack = { container ->
                runCatching {
                    container.consume(ListSerializer(QuizzesConfig.serializer()))
                }.getOrNull()
            },
            handleBackButton = false,
            childFactory = ::createChild,
        )

    // priority = 100: PRIORITY_OVERLAY absent in Essenty 2.1.0 (ADR-QS-12, Pattern Invariant 5)
    private val backCallback = BackCallback(priority = 100, isEnabled = false) {
        navigation.pop()
    }

    init {
        backHandler.register(backCallback)
        childStack.subscribe { stack ->
            backCallback.isEnabled = stack.backStack.isNotEmpty()
        }
    }

    override fun openQuestList(catalogId: CatalogId, catalogName: String) {
        navigation.pushNew(QuizzesConfig.QuestList(catalogId.value, listOf("Каталоги", catalogName)))
    }

    override fun openSectionList(questId: QuestId, titles: List<String>) {
        navigation.pushNew(QuizzesConfig.SectionList(questId.value, titles))
    }

    override fun popToLevel(uiLevel: Int) {
        if (uiLevel < 0) return
        val active = childStack.value.active
        val titlesSize = when (val cfg = active.configuration) {
            is QuizzesConfig.Idle -> 0
            is QuizzesConfig.QuestList -> cfg.titles.size
            is QuizzesConfig.SectionList -> cfg.titles.size
            is QuizzesConfig.ThemeList -> cfg.titles.size
            is QuizzesConfig.LessonList -> cfg.titles.size
            is QuizzesConfig.LessonPlaceholder -> cfg.titles.size
        }
        // In MyQuests entry path, SectionList is pushed directly (no QuestList in stack).
        // titles may contain virtual "decoration" segments that have no stack entry.
        // virtualCount = how many leading titles have no corresponding stack entry.
        val virtualCount = (titlesSize + 1 - childStack.value.items.size).coerceAtLeast(0)
        val adjustedLevel = uiLevel - virtualCount
        if (adjustedLevel < 0) {
            // Clicked a virtual breadcrumb (e.g., catalog name from MyQuests path) — dismiss overlay
            navigation.popToFirst()
        } else {
            navigation.popTo(adjustedLevel + 1)
        }
    }

    override fun dismissQuizzes() {
        navigation.popToFirst()
    }

    private fun createChild(config: QuizzesConfig, ctx: ComponentContext): QuizzesChild =
        when (config) {
            is QuizzesConfig.Idle ->
                QuizzesChild.Idle
            is QuizzesConfig.QuestList ->
                QuizzesChild.QuestList(
                    DefaultQuestListComponent(ctx, config, questRepository, navigation, mainContext)
                )
            is QuizzesConfig.SectionList ->
                QuizzesChild.SectionList(
                    DefaultSectionListComponent(ctx, config, sectionRepository, navigation, mainContext)
                )
            is QuizzesConfig.ThemeList ->
                QuizzesChild.ThemeList(
                    DefaultThemeListComponent(ctx, config, themeRepository, navigation, mainContext)
                )
            is QuizzesConfig.LessonList ->
                QuizzesChild.LessonList(
                    DefaultLessonListComponent(ctx, config, lessonRepository, navigation, mainContext)
                )
            is QuizzesConfig.LessonPlaceholder ->
                QuizzesChild.LessonPlaceholder(DefaultLessonPlaceholderComponent(ctx, config))
        }
}
