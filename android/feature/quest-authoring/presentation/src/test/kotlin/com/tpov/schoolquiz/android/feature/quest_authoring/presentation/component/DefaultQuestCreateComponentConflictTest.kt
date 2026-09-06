package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraft
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftConflict
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.CreateQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetActiveQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftConflictUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ResolveQuestDraftConflictUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SaveDraftQuestionUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitQuestDraftToArenaUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Расхождение на экране черновика (Эпик 8, история 8.5).
 *
 * Две половины одной истории: конфликт из очереди виден автору числом и одним действием, а без
 * конфликта этого действия на экране нет. Третья — что нажатие порождает новую мутацию, а не
 * повторяет отвергнутую (AD-2, AD-24).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultQuestCreateComponentConflictTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `given a conflicted draft then the screen offers to resubmit over the server version`() =
        runTest(testScheduler) {
            val repository = FakeQuestAuthoringRepository()
            repository.conflict.value = QuestDraftConflict(DRAFT_ID, serverVersion = 7L, expectedVersion = 4L)

            val state = component(repository).state.value

            val conflict = assertNotNull(state.draftConflict, "расхождение обязано доехать до экрана")
            assertEquals(7L, conflict.serverVersion)
            assertEquals(4L, conflict.expectedVersion)
            assertTrue(state.canResubmitOverServerVersion, "автору есть что решать")
        }

    @Test
    fun `given no conflicted record then the screen offers nothing to resolve`() = runTest(testScheduler) {
        val repository = FakeQuestAuthoringRepository()

        val state = component(repository).state.value

        assertNull(state.draftConflict)
        assertFalse(state.canResubmitOverServerVersion)
    }

    @Test
    fun `given a conflict the server left without a number then nothing is offered to resubmit`() =
        runTest(testScheduler) {
            // Чужой ABORTED без деталей: расхождение названо, версия — нет. Прежде экран
            // подставлял сюда ноль и предлагал «отправить заново поверх версии 0», а отправка
            // уходила с пустой ожидаемой версией, то есть вообще без сверки.
            val repository = FakeQuestAuthoringRepository()
            repository.conflict.value = QuestDraftConflict(DRAFT_ID, serverVersion = null, expectedVersion = 4L)

            val state = component(repository).state.value

            val conflict = assertNotNull(state.draftConflict, "расхождение остаётся видимым")
            assertNull(conflict.serverVersion)
            assertFalse(conflict.hasServerVersion)
            assertFalse(
                state.canResubmitOverServerVersion,
                "поверх неизвестной версии отправлять нечего",
            )
        }

    @Test
    fun `given a conflict without a number then pressing resubmit asks the feature for nothing`() =
        runTest(testScheduler) {
            // Состояние — не защита: нажатие обязано быть отклонено самим компонентом, иначе
            // разрешение конфликта уходит в очередь без ожидаемой версии.
            val repository = FakeQuestAuthoringRepository()
            repository.conflict.value = QuestDraftConflict(DRAFT_ID, serverVersion = null, expectedVersion = 4L)
            val component = component(repository)

            component.onResubmitOverServerVersionClick()

            assertTrue(repository.resolved.isEmpty())
            assertNotNull(component.state.value.errorMessage, "автору сказано, почему нельзя")
            assertNotNull(component.state.value.draftConflict, "расхождение никуда не делось")
        }

    @Test
    fun `given the author resubmits then the decision reaches the feature once`() = runTest(testScheduler) {
        val repository = FakeQuestAuthoringRepository()
        repository.conflict.value = QuestDraftConflict(DRAFT_ID, serverVersion = 7L, expectedVersion = 4L)
        val component = component(repository)

        component.onResubmitOverServerVersionClick()

        assertEquals(listOf(OWNER_UID to DRAFT_ID), repository.resolved)
    }

    @Test
    fun `given no conflict then pressing resubmit asks the feature for nothing`() = runTest(testScheduler) {
        // Действия на экране нет, но состояние — не защита: компонент обязан отказать сам.
        val repository = FakeQuestAuthoringRepository()
        val component = component(repository)

        component.onResubmitOverServerVersionClick()

        assertTrue(repository.resolved.isEmpty())
    }

    private fun component(repository: FakeQuestAuthoringRepository): DefaultQuestCreateComponent {
        lifecycle = LifecycleRegistry()
        val component =
            DefaultQuestCreateComponent(
                componentContext = DefaultComponentContext(lifecycle),
                authRepository = FakeAuthRepository(),
                observeCatalogs = com.tpov.schoolquiz.shared.core.catalog.domain.use_case
                    .ObserveCatalogsUseCase(FakeCatalogRepository()),
                createQuestDraft =
                    CreateQuestDraftUseCase(
                        repository,
                        QuestAuthoringIdProvider { prefix -> "$prefix-1" },
                        QuestAuthoringTimestampProvider { 1_000L },
                    ),
                getActiveQuestDraft = GetActiveQuestDraftUseCase(repository),
                saveDraftQuestion =
                    SaveDraftQuestionUseCase(
                        repository,
                        KotlinxSerializationQuestionContentParser(),
                        QuestAuthoringIdProvider { prefix -> "$prefix-1" },
                        QuestAuthoringTimestampProvider { 1_000L },
                    ),
                submitQuestDraftToArena =
                    SubmitQuestDraftToArenaUseCase(
                        repository,
                        QuestAuthoringIdProvider { prefix -> "$prefix-1" },
                        QuestAuthoringTimestampProvider { 1_000L },
                    ),
                observeQuestDraftConflict = ObserveQuestDraftConflictUseCase(repository),
                resolveQuestDraftConflict = ResolveQuestDraftConflictUseCase(repository),
                questionContentParser = KotlinxSerializationQuestionContentParser(),
                questRepository = FakeQuestRepository(),
                sectionRepository = FakeSectionRepository(),
                themeRepository = FakeThemeRepository(),
                lessonRepository = FakeLessonRepository(),
                navigator = FakeNavigator(),
                mainContext = testDispatcher,
            )
        lifecycle.resume()
        testScheduler.advanceUntilIdle()
        return component
    }

    private class FakeNavigator : Navigator {
        override fun goTo(destination: Destination) = Unit
    }

    private class FakeAuthRepository : AuthRepository {
        override suspend fun currentUid(): String = OWNER_UID

        override fun observeUid(): Flow<String?> = flowOf(OWNER_UID)
    }

    private class FakeQuestAuthoringRepository : QuestAuthoringRepository {
        val conflict = MutableStateFlow<QuestDraftConflict?>(null)
        val resolved = mutableListOf<Pair<String, QuestDraftId>>()

        private val bundle = MutableStateFlow(activeBundle())

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> = flowOf(emptyList())

        override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> = bundle.map { it }

        override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? = bundle.value

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle = bundle.value

        var activatedDraftId: QuestDraftId? = null

        override suspend fun activateDraft(
            ownerUid: String,
            draftId: QuestDraftId,
        ): Result<Unit> {
            activatedDraftId = draftId
            return Result.success(Unit)
        }

        override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> = Result.success(Unit)

        override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> = Result.success(Unit)

        override suspend fun queueArenaSubmission(submission: QuestArenaSubmission): Result<Unit> =
            Result.success(Unit)

        override fun observeDraftConflict(
            ownerUid: String,
            draftId: QuestDraftId,
        ): Flow<QuestDraftConflict?> = conflict

        override suspend fun resolveDraftConflict(
            ownerUid: String,
            draftId: QuestDraftId,
        ): Result<Unit> {
            resolved += ownerUid to draftId
            conflict.value = null
            return Result.success(Unit)
        }

        override suspend fun setDraftStatus(
            draftId: QuestDraftId,
            status: QuestDraftStatus,
            updatedAtMs: Long,
        ): Result<Unit> = Result.success(Unit)
    }

    private class FakeCatalogRepository : CatalogRepository {
        override fun observeAll(): Flow<List<Catalog>> = flowOf(emptyList())

        override suspend fun refreshFromRemote(): Result<Set<CatalogId>> = Result.success(emptySet())

        override suspend fun getById(id: CatalogId): Catalog? = null
    }

    private class FakeQuestRepository : QuestRepository {
        override fun observeMyQuests(
            authorUid: String,
            catalogId: CatalogId?,
        ): Flow<List<Quest>> = flowOf(emptyList())

        override fun observeByCatalog(
            catalogId: CatalogId,
            shelf: String,
        ): Flow<List<Quest>> = flowOf(emptyList())

        override fun observeByShelf(shelf: String): Flow<List<Quest>> = flowOf(emptyList())

        override suspend fun getById(id: QuestId): Quest? = null

        override suspend fun refreshFromRemote(
            currentUserUid: String?,
            availableShelves: Set<String>,
            catalogIdsToSync: Set<CatalogId>,
            cursor: Long,
        ): Result<Set<QuestId>> = Result.success(emptySet())
    }

    private class FakeSectionRepository : SectionRepository {
        override fun observeByQuest(questId: QuestId): Flow<List<Section>> = flowOf(emptyList())

        override suspend fun getById(id: SectionId): Section? = null

        override suspend fun refreshByParents(
            questIds: Set<QuestId>,
            cursor: Long,
        ): Result<Set<SectionId>> = Result.success(emptySet())
    }

    private class FakeThemeRepository : ThemeRepository {
        override fun observeBySection(sectionId: SectionId): Flow<List<Theme>> = flowOf(emptyList())

        override suspend fun getById(id: ThemeId): Theme? = null

        override suspend fun refreshByParents(
            sectionIds: Set<SectionId>,
            cursor: Long,
        ): Result<Set<ThemeId>> = Result.success(emptySet())
    }

    private class FakeLessonRepository : LessonRepository {
        override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> = flowOf(emptyList())

        override suspend fun getById(id: LessonId): Lesson? = null

        override suspend fun refreshByParents(
            themeIds: Set<ThemeId>,
            cursor: Long,
        ): Result<Set<LessonId>> = Result.success(emptySet())
    }

    private companion object {
        const val OWNER_UID = "author-1"
        val DRAFT_ID = QuestDraftId("draft-1")

        fun activeBundle(): QuestAuthoringBundle =
            QuestAuthoringBundle(
                draft =
                    QuestDraft(
                        id = DRAFT_ID,
                        ownerUid = OWNER_UID,
                        catalogId = CatalogId("catalog-1"),
                        title = "Черновик",
                        description = null,
                        defaultLanguage = "ru",
                        defaultDifficulty = Difficulty.EASY,
                        status = QuestDraftStatus.SAVED,
                        localRevision = 5L,
                        serverRevision = 4L,
                        publicQuestId = null,
                        createdAtMs = 1_000L,
                        updatedAtMs = 1_000L,
                        isActive = true,
                    ),
                sections = emptyList(),
                themes = emptyList(),
                lessons = emptyList(),
                questions = emptyList(),
            )
    }
}
