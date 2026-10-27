package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultMyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeCatalogRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeNavigator
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestAuthoringRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeReviewFeeQuoteRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildCatalog
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildQuest
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLesson
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
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
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewFeeQuoteRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.OpenQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.QuoteQuestReviewFeeUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitQuestDraftToArenaUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [DefaultMyQuestsComponent].
 *
 * Tests are traced to spec ACs: AC#45 (guest/auth state), AC#25 (catalog filter),
 * AC#29 (create quest), AC#47 (archived exclusion).
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 * Plan: docs/features/home-and-my-quests/plan/phase-05/tests.md §1
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMyQuestsComponentTest {

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

    private fun testCtx(): DefaultComponentContext {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }

    private fun buildComponent(
        authRepo: FakeAuthRepository = FakeAuthRepository(),
        questRepo: FakeQuestRepository = FakeQuestRepository(),
        authoringRepo: FakeQuestAuthoringRepository = FakeQuestAuthoringRepository(),
        catalogRepo: FakeCatalogRepository = FakeCatalogRepository(),
        navigator: FakeNavigator = FakeNavigator(),
        onQuestDrillDown: (QuestDisplayItem) -> Unit = { },
        quoteRepo: ReviewFeeQuoteRepository = FakeReviewFeeQuoteRepository(),
        submitRepo: QuestAuthoringRepository = authoringRepo,
    ) = DefaultMyQuestsComponent(
        componentContext = testCtx(),
        authRepo = authRepo,
        observeMyQuests = ObserveMyQuestsUseCase(questRepo),
        observeDraftSummaries = ObserveQuestDraftSummariesUseCase(authoringRepo),
        openQuestDraft = OpenQuestDraftUseCase(authoringRepo),
        observeCatalogs = ObserveCatalogsUseCase(catalogRepo),
        quoteReviewFee = QuoteQuestReviewFeeUseCase(quoteRepo),
        submitQuestDraftToArena = SubmitQuestDraftToArenaUseCase(
            submitRepo,
            QuestAuthoringIdProvider { prefix -> "$prefix-1" },
            QuestAuthoringTimestampProvider { 1_000L },
        ),
        navigator = navigator,
        onQuestDrillDown = onQuestDrillDown,
        mainContext = testDispatcher,
    )

    // ── AC#45a — guest path: uid=null → isGuest=true, no DB query ─────────────

    @Test
    fun `when uid is null then state is empty and isGuest is true and repo not called`() = runTest {
        val questRepo = FakeQuestRepository()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = null),
            questRepo = questRepo,
        )

        val state = component.state.value

        assertTrue(state.quests.isEmpty(), "quests must be empty for guest")
        assertTrue(state.isGuest, "isGuest must be true when uid=null")
        assertEquals(0, questRepo.observeMyQuestsCallCount, "repository must NOT be queried for guest")
    }

    // ── AC#45b — mid-session login: uid changes → switches to quest list ───────

    @Test
    fun `when uid changes from null to user then state switches to quest list`() = runTest {
        val authRepo = FakeAuthRepository(initialUid = null)
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(
            buildQuest(id = "q1", authorUid = "user1"),
            buildQuest(id = "q2", authorUid = "user1"),
        ))

        val component = buildComponent(authRepo = authRepo, questRepo = questRepo)

        assertTrue(component.state.value.isGuest, "initial state must be guest")

        authRepo.setUid("user1")

        val state = component.state.value
        assertFalse(state.isGuest, "isGuest must be false after sign-in")
        assertEquals(2, state.quests.size, "must show user quests after sign-in")
    }

    // ── AC#45c — sign-out: uid→null → returns to guest state ─────────────────

    @Test
    fun `when uid changes from user to null then state returns to guest`() = runTest {
        val authRepo = FakeAuthRepository(initialUid = "user1")
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(buildQuest(id = "q1", authorUid = "user1")))

        val component = buildComponent(authRepo = authRepo, questRepo = questRepo)

        assertEquals(1, component.state.value.quests.size, "must have quest before sign-out")

        authRepo.setUid(null)

        val state = component.state.value
        assertTrue(state.isGuest, "isGuest must be true after sign-out")
        assertTrue(state.quests.isEmpty(), "quest list must be empty after sign-out")
    }

    // ── AC#25 / Journey 7 — catalog spinner filter ────────────────────────────

    @Test
    fun `when catalog selected then state shows only quests for that catalog`() = runTest {
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(
            buildQuest(id = "q1", authorUid = "user1", catalogId = "cat1"),
            buildQuest(id = "q2", authorUid = "user1", catalogId = "cat2"),
        ))

        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            questRepo = questRepo,
        )

        assertEquals(2, component.state.value.quests.size, "must show all quests initially")

        component.onCatalogSelected(CatalogId("cat1"))

        val filtered = component.state.value.quests
        assertEquals(1, filtered.size, "must filter to catalog1 only")
        assertEquals("q1", filtered.first().id.value, "filtered quest must match catalogId cat1")
    }

    // ── AC#29 / Journey 8 — create quest FAB ─────────────────────────────────

    @Test
    fun `when onCreateQuestClick then navigator receives OpenQuestCreate`() = runTest {
        val navigator = FakeNavigator()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            navigator = navigator,
        )

        component.onCreateQuestClick()

        assertEquals(1, navigator.destinations.size, "exactly one navigation event expected")
        assertEquals(Destination.OpenQuestCreate, navigator.destinations.first())
    }

    @Test
    fun `when draft summaries emitted then state shows local drafts`() = runTest {
        val authoringRepo = FakeQuestAuthoringRepository()
        authoringRepo.emit(
            listOf(
                QuestDraftSummary(
                    id = QuestDraftId("draft-1"),
                    catalogId = CatalogId("cat1"),
                    title = "Draft quest",
                    status = QuestDraftStatus.DRAFT,
                    questionCount = 3,
                    updatedAtMs = 10L,
                    isActive = true,
                ),
            ),
        )

        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            authoringRepo = authoringRepo,
        )

        val drafts = component.state.value.drafts
        assertEquals(1, drafts.size)
        assertEquals("Draft quest", drafts.first().title)
        assertEquals(3, drafts.first().questionCount)
    }

    // ── AC#47 — archived quests visible as on-demand roots ───────────────────

    @Test
    fun `when store has archived quest then archived quest is in state`() = runTest {
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(
            buildQuest(id = "active", authorUid = "user1", archived = false),
            buildQuest(id = "archived", authorUid = "user1", archived = true),
        ))

        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            questRepo = questRepo,
        )

        val quests = component.state.value.quests
        assertEquals(2, quests.size, "archived quest must remain visible as on-demand root")
        assertEquals(setOf("active", "archived"), quests.map { it.id.value }.toSet())
    }

    // ── selectedCatalogId reset on sign-out ───────────────────────────────────

    @Test
    fun `when sign-out after catalog selection then selectedCatalogId is reset`() = runTest {
        val authRepo = FakeAuthRepository(initialUid = "user1")
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(buildQuest(id = "q1", authorUid = "user1", catalogId = "cat1")))

        val component = buildComponent(authRepo = authRepo, questRepo = questRepo)
        component.onCatalogSelected(CatalogId("cat1"))

        assertEquals(CatalogId("cat1"), component.state.value.selectedCatalogId)

        authRepo.setUid(null)

        val state = component.state.value
        assertNull(state.selectedCatalogId, "selectedCatalogId must be null after sign-out")
        assertTrue(state.isGuest)
    }

    // ── AC#46 / Journey 11 — guest clicks FAB ────────────────────────────────

    @Test
    fun `when guest clicks FAB then navigator receives OpenQuestCreate`() = runTest {
        val navigator = FakeNavigator()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = null),
            navigator = navigator,
        )

        assertTrue(component.state.value.isGuest, "precondition: must be guest")

        component.onCreateQuestClick()

        assertEquals(Destination.OpenQuestCreate, navigator.lastDestination(),
            "guest FAB must navigate to OpenQuestCreate")
    }

    // ── catalog list populated from catalogRepo ───────────────────────────────

    @Test
    fun `when catalogs emitted then state catalogs reflects non-archived catalogs`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(
            buildCatalog(id = "cat1", archived = false),
            buildCatalog(id = "cat2", archived = true),
        ))

        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            catalogRepo = catalogRepo,
        )

        val catalogs = component.state.value.catalogs
        assertEquals(1, catalogs.size, "archived catalogs must not appear in state")
        assertEquals(CatalogId("cat1"), catalogs.first().id)
    }

    // ── MC-U-01 — onQuestClick invokes onQuestDrillDown lambda with quest ──────
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md MC-U-01
    // GIVEN onQuestDrillDown lambda recorder; quest = QuestDisplayItem(id="q-1", catalogId="cat-1")
    // WHEN component.onQuestClick(quest)
    // THEN capturedQuest == quest

    @Test
    fun `when onQuestClick called then onQuestDrillDown lambda invoked with quest`() = runTest {
        var capturedQuest: QuestDisplayItem? = null
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            onQuestDrillDown = { quest -> capturedQuest = quest },
        )
        val quest = QuestDisplayItem(
            id = QuestId("q-1"),
            catalogId = CatalogId("cat-1"),
            title = "Quest A",
            pictureUrl = null,
            averageRating = null,
        )

        component.onQuestClick(quest)

        assertEquals(quest, capturedQuest, "lambda must receive the exact quest instance")
    }

    // ── MC-U-02 — lambda receives entire QuestDisplayItem including catalogId ──
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md MC-U-02
    // GIVEN quest with catalogId = CatalogId("cat-2")
    // WHEN component.onQuestClick(quest)
    // THEN capturedQuest?.catalogId == CatalogId("cat-2")

    @Test
    fun `when onQuestClick called then catalogId is preserved in captured quest`() = runTest {
        var capturedQuest: QuestDisplayItem? = null
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            onQuestDrillDown = { quest -> capturedQuest = quest },
        )
        val quest = QuestDisplayItem(
            id = QuestId("q-2"),
            catalogId = CatalogId("cat-2"),
            title = "Quest B",
            pictureUrl = null,
            averageRating = null,
        )

        component.onQuestClick(quest)

        assertEquals(
            CatalogId("cat-2"), capturedQuest?.catalogId,
            "lambda must preserve catalogId for breadcrumb resolution in DefaultRootComponent",
        )
    }

    // ── 4.11 — submit from the draft card: quote dialog → confirm → submission ───

    private val submitDraft =
        DraftQuestDisplayItem(
            id = QuestDraftId("draft-1"),
            catalogId = CatalogId("cat1"),
            title = "Draft quest",
            status = QuestDraftStatus.SAVED,
            questionCount = 3,
            isActive = true,
        )

    @Test
    fun `submit click fetches the fee and shows it before anything is sent`() = runTest {
        val quoteRepo = FakeReviewFeeQuoteRepository(
            ReviewFeeQuote(priorCycle = false, tariff = "initial", feeNolics = 1000, tariffRevision = 1),
        )
        val submitRepo = SubmittableAuthoringRepo()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            quoteRepo = quoteRepo,
            submitRepo = submitRepo,
        )

        component.onDraftSubmitClick(submitDraft)

        val ready = component.submitState.value
        assertEquals(submitDraft.id, ready?.draft?.id)
        assertEquals(1000, ready?.quote?.feeNolics, "fee must be on screen before the confirm")
        assertEquals(0, submitRepo.queued.size, "no submission may leave before confirm")

        component.onDraftSubmitConfirm()

        assertTrue(component.submitState.value == null, "success closes the dialog")
        assertEquals(1, submitRepo.queued.size, "confirm enqueues exactly one submission")
        assertEquals("draft-1", submitRepo.queued.single().draftId.value)
    }

    @Test
    fun `recheck quote carries per-question fee from the server`() = runTest {
        val quoteRepo = FakeReviewFeeQuoteRepository(
            ReviewFeeQuote(priorCycle = true, tariff = "recheck", feeNolics = 300, tariffRevision = 2),
        )
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            quoteRepo = quoteRepo,
        )
        val draft = submitDraft.copy(questionCount = 3)

        component.onDraftSubmitClick(draft)

        assertEquals(300, component.submitState.value?.quote?.feeNolics)
        assertEquals("recheck", component.submitState.value?.quote?.tariff)
    }

    @Test
    fun `quote failure keeps the dialog without a confirm and without sending`() = runTest {
        val quoteRepo = FakeReviewFeeQuoteRepository(
            error = SyncFailure(SyncError.NoNetwork, IllegalStateException("offline")),
        )
        val submitRepo = SubmittableAuthoringRepo()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            quoteRepo = quoteRepo,
            submitRepo = submitRepo,
        )

        component.onDraftSubmitClick(submitDraft)

        val state = component.submitState.value
        assertTrue(state?.isQuoteFailed == true, "failure must be visible")
        assertNull(state?.quote, "no quote — no fee shown")
        assertEquals(0, submitRepo.queued.size)

        component.onDraftSubmitConfirm()
        assertEquals(0, submitRepo.queued.size, "confirm without a quote must not submit")
    }

    @Test
    fun `submit rejection surfaces as a failed dialog state, app stays operable`() = runTest {
        val quoteRepo = FakeReviewFeeQuoteRepository(
            ReviewFeeQuote(priorCycle = false, tariff = "initial", feeNolics = 1000, tariffRevision = 1),
        )
        // Репозиторий без черновика: use case отклонит подачу (DraftNotFound) — честный отказ.
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            quoteRepo = quoteRepo,
            submitRepo = FakeQuestAuthoringRepository(),
        )

        component.onDraftSubmitClick(submitDraft)
        component.onDraftSubmitConfirm()

        val state = component.submitState.value
        assertTrue(state?.isSubmitFailed == true, "rejection must be visible in the dialog")
        assertTrue(state?.isSubmitting == false, "spinner must stop on failure")
    }

    @Test
    fun `dismiss closes the dialog without sending`() = runTest {
        val quoteRepo = FakeReviewFeeQuoteRepository(
            ReviewFeeQuote(priorCycle = false, tariff = "initial", feeNolics = 1000, tariffRevision = 1),
        )
        val submitRepo = SubmittableAuthoringRepo()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            quoteRepo = quoteRepo,
            submitRepo = submitRepo,
        )

        component.onDraftSubmitClick(submitDraft)
        component.onDraftSubmitDismiss()

        assertNull(component.submitState.value)
        assertEquals(0, submitRepo.queued.size)
    }

    @Test
    fun `stale dialog state is not overwritten by a quote for another draft`() = runTest {
        val quoteRepo = FakeReviewFeeQuoteRepository(
            ReviewFeeQuote(priorCycle = false, tariff = "initial", feeNolics = 1000, tariffRevision = 1),
        )
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            quoteRepo = quoteRepo,
        )
        val other = submitDraft.copy(id = QuestDraftId("draft-2"))

        component.onDraftSubmitClick(submitDraft)
        component.onDraftSubmitDismiss()
        component.onDraftSubmitClick(other)

        assertEquals(QuestDraftId("draft-2"), component.submitState.value?.draft?.id)
    }

    /** Репозиторий с подающим черновиком: урок готов — вопросы обеих сложностей сохранены. */
    private class SubmittableAuthoringRepo : QuestAuthoringRepository {
        val queued = mutableListOf<QuestArenaSubmission>()

        private fun question(
            id: String,
            difficulty: Difficulty,
        ) = DraftQuestion(
            id = DraftQuestionId(id),
            draftId = QuestDraftId("draft-1"),
            lessonId = DraftLessonId("l1"),
            type = DraftQuestionType.SINGLE_CHOICE,
            language = "ru",
            difficulty = difficulty,
            order = 0,
            text = "2+2?",
            imagePath = null,
            payload = "{\"options\":[\"4\"],\"correct\":0}",
            validationState = DraftQuestionValidationState.SAVED,
            updatedAtMs = 1_000L,
        )

        private val bundle by lazy {
            QuestAuthoringBundle(
                draft =
                    QuestDraft(
                        id = QuestDraftId("draft-1"),
                        ownerUid = "user1",
                        catalogId = CatalogId("cat1"),
                        title = "Draft quest",
                        description = null,
                        defaultLanguage = "ru",
                        defaultDifficulty = Difficulty.EASY,
                        status = QuestDraftStatus.SAVED,
                        localRevision = 1L,
                        serverRevision = null,
                        publicQuestId = null,
                        createdAtMs = 1_000L,
                        updatedAtMs = 1_000L,
                        isActive = true,
                    ),
                sections = listOf(DraftSection(DraftSectionId("s1"), QuestDraftId("draft-1"), "Section", 0)),
                themes =
                    listOf(
                        DraftTheme(DraftThemeId("t1"), QuestDraftId("draft-1"), DraftSectionId("s1"), "Theme", 0),
                    ),
                lessons =
                    listOf(
                        DraftLesson(DraftLessonId("l1"), QuestDraftId("draft-1"), DraftThemeId("t1"), "Lesson", 0),
                    ),
                questions =
                    listOf(
                        question("q-easy", Difficulty.EASY),
                        question("q-hard", Difficulty.HARD),
                    ),
            )
        }

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> = flowOf(emptyList())

        override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> = flowOf(bundle)

        override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? = bundle

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle? = bundle

        override suspend fun activateDraft(
            ownerUid: String,
            draftId: QuestDraftId,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> = Result.success(Unit)

        override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> =
            Result.success(Unit)

        override suspend fun queueArenaSubmission(submission: QuestArenaSubmission): Result<Unit> {
            queued += submission
            return Result.success(Unit)
        }

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
}
