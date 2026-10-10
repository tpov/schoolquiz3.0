package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankMarkerKind
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
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLesson
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSection
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSectionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftTheme
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftThemeId
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
 * Undo/redo редактора вопроса (UX-O6 §4, история 4.5).
 *
 * Предмет контракта: правка, undo и redo восстанавливают текст И его метки вместе — пробел,
 * защита перевода и связка с ответом едут одним снимком, а не двумя. Служебные переходы
 * (превью) историю не заводят; уход от вопроса историю обрывает.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultQuestCreateComponentUndoRedoTest {

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
    fun `given marker and protected answer edits then undo restores text and annotation together`() =
        runTest(testScheduler) {
            val component = component()

            component.onQuestionsClick(Difficulty.EASY)
            component.onFillBlankMarkerAdded(FillBlankMarkerKind.BLANK)
            component.onFillBlankAnswerChanged(0, "Kotlin")
            component.onFillBlankAnswerProtectedChanged(0, true)

            val after = assertNotNull(component.state.value.editor)
            assertEquals("***Kotlin***", after.fillBlankText)
            assertTrue(after.fillBlankAnswers.first().isProtected)
            assertTrue(after.canUndo, "правки заведены в историю")
            assertFalse(after.canRedo)

            component.onEditorUndoClick()

            val oneStepBack = assertNotNull(component.state.value.editor)
            assertEquals("**Kotlin**", oneStepBack.fillBlankText, "текст вернулся на шаг")
            assertFalse(
                oneStepBack.fillBlankAnswers.first().isProtected,
                "метка защиты вернулась тем же шагом, не своим",
            )

            component.onEditorUndoClick()

            val twoStepsBack = assertNotNull(component.state.value.editor)
            assertEquals("**blank**", twoStepsBack.fillBlankText)
            assertEquals(
                "blank",
                twoStepsBack.fillBlankAnswers.first().text,
                "связка с ответом вернулась вместе с текстом",
            )
        }

    @Test
    fun `given undone edits then redo repeats the same text and annotation pair`() =
        runTest(testScheduler) {
            val component = component()
            component.onQuestionsClick(Difficulty.EASY)
            component.onFillBlankMarkerAdded(FillBlankMarkerKind.BLANK)
            component.onFillBlankAnswerChanged(0, "Kotlin")
            component.onFillBlankAnswerProtectedChanged(0, true)
            component.onEditorUndoClick()
            component.onEditorUndoClick()

            component.onEditorRedoClick()
            val redone = assertNotNull(component.state.value.editor)
            assertEquals("**Kotlin**", redone.fillBlankText)
            assertEquals("Kotlin", redone.fillBlankAnswers.first().text)
            assertFalse(redone.fillBlankAnswers.first().isProtected)

            component.onEditorRedoClick()
            val twice = assertNotNull(component.state.value.editor)
            assertEquals("***Kotlin***", twice.fillBlankText)
            assertTrue(twice.fillBlankAnswers.first().isProtected)
            assertFalse(twice.canRedo)
        }

    @Test
    fun `given no edits then undo and redo buttons have nothing to do`() = runTest(testScheduler) {
        val component = component()
        component.onQuestionsClick(Difficulty.EASY)

        val editor = assertNotNull(component.state.value.editor)

        assertFalse(editor.canUndo)
        assertFalse(editor.canRedo)
        component.onEditorUndoClick()
        component.onEditorRedoClick()
        assertNull(
            component.state.value.editor?.fillBlankText?.takeIf { it.isNotEmpty() },
            "холостое нажатие не меняет содержимое",
        )
    }

    @Test
    fun `given preview toggle then the history does not count it as an edit`() = runTest(testScheduler) {
        val component = component()
        component.onQuestionsClick(Difficulty.EASY)

        component.onQuestionPreviewClick()

        val editor = assertNotNull(component.state.value.editor)
        assertTrue(editor.isPreviewVisible)
        assertFalse(editor.canUndo, "превью — служебный переход, не правка")
    }

    @Test
    fun `given a saved question then the history does not follow past the save`() =
        runTest(testScheduler) {
            val component = component()
            component.onQuestionsClick(Difficulty.EASY)
            component.onQuestionTypeSelected(DraftQuestionType.FILL_BLANK)
            component.onFillBlankMarkerAdded(FillBlankMarkerKind.BLANK)
            component.onFillBlankAnswerChanged(0, "Kotlin")
            repeat(4) { index -> component.onFillBlankDistractorChanged(index, "distractor-${index + 1}") }
            assertTrue(component.state.value.editor?.canUndo == true)

            // Уход от валидного вопроса автосохраняет его; редактор пересобирается из черновика.
            component.onQuestionSelected(0)
            testScheduler.advanceUntilIdle()

            val after = assertNotNull(component.state.value.editor)
            assertEquals(
                null,
                after.errorMessage,
                "сохранение не прошло, история прежней сессии не сброшена",
            )
            assertFalse(
                after.canUndo,
                "после сохранения история прежней сессии правок не продолжается",
            )
        }

    @Test
    fun `given undo after an error message then the message does not resurrect`() = runTest(testScheduler) {
        val component = component()
        component.onQuestionsClick(Difficulty.EASY)
        component.onFillBlankMarkerAdded(FillBlankMarkerKind.BLANK)
        component.onFillBlankAnswerChanged(0, "Kotlin")
        component.onEditorUndoClick()

        val restored = assertNotNull(component.state.value.editor)

        assertNull(restored.errorMessage, "служебные поля снимка не переезжают из истории")
        assertNull(restored.lastSavedMessage)
    }

    private fun component(repository: FakeBundleRepository = FakeBundleRepository()): DefaultQuestCreateComponent {
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

    /** Черновик с одним уроком и без вопросов: редактор открывается на новом пустом вопросе. */
    private class FakeBundleRepository : QuestAuthoringRepository {
        private val bundle = MutableStateFlow(activeBundle())

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> = flowOf(emptyList())

        override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> = bundle.map { it }

        override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? = bundle.value

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle = bundle.value

        override suspend fun activateDraft(
            ownerUid: String,
            draftId: QuestDraftId,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> = Result.success(Unit)

        override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> = Result.success(Unit)

        override suspend fun queueArenaSubmission(submission: QuestArenaSubmission): Result<Unit> =
            Result.success(Unit)

        override fun observeDraftConflict(
            ownerUid: String,
            draftId: QuestDraftId,
        ): Flow<QuestDraftConflict?> = flowOf(null)

        override suspend fun resolveDraftConflict(
            ownerUid: String,
            draftId: QuestDraftId,
        ): Result<Unit> = Result.success(Unit)

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
        val LESSON_ID = DraftLessonId("lesson-1")

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
                sections =
                    listOf(
                        DraftSection(
                            id = DraftSectionId("section-1"),
                            draftId = DRAFT_ID,
                            title = "Раздел 1",
                            order = 0,
                        ),
                    ),
                themes =
                    listOf(
                        DraftTheme(
                            id = DraftThemeId("theme-1"),
                            draftId = DRAFT_ID,
                            sectionId = DraftSectionId("section-1"),
                            title = "Тема 1",
                            order = 0,
                        ),
                    ),
                lessons =
                    listOf(
                        DraftLesson(
                            id = LESSON_ID,
                            draftId = DRAFT_ID,
                            themeId = DraftThemeId("theme-1"),
                            title = "Урок 1",
                            order = 0,
                        ),
                    ),
                questions = emptyList(),
            )
    }
}
