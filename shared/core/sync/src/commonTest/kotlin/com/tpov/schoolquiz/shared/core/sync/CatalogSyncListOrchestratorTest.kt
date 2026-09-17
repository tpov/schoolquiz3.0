package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.sync.fake.FakeCatalogRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSectionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeThemeRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogSyncListOrchestratorTest {

    private lateinit var catalogRepo: FakeCatalogRepository
    private lateinit var questRepo: FakeQuestRepository
    private lateinit var sectionRepo: FakeSectionRepository
    private lateinit var themeRepo: FakeThemeRepository
    private lateinit var lessonRepo: FakeLessonRepository
    private lateinit var questionRepo: FakeQuestionRepository
    private lateinit var syncState: FakeSyncStateRepository
    private lateinit var journal: FakeCatalogJournal
    private lateinit var status: RecordingSyncStatus
    private lateinit var orchestrator: CatalogSyncListOrchestrator

    @BeforeTest
    fun setUp() {
        catalogRepo = FakeCatalogRepository()
        questRepo = FakeQuestRepository()
        sectionRepo = FakeSectionRepository()
        themeRepo = FakeThemeRepository()
        lessonRepo = FakeLessonRepository()
        questionRepo = FakeQuestionRepository()
        syncState = FakeSyncStateRepository()
        journal = FakeCatalogJournal()
        status = RecordingSyncStatus()
        orchestrator = CatalogSyncListOrchestrator(
            catalogRepo = catalogRepo,
            questRepo = questRepo,
            sectionRepo = sectionRepo,
            themeRepo = themeRepo,
            lessonRepo = lessonRepo,
            questionRepo = questionRepo,
            syncStateRepo = syncState,
            syncChangeRemote = journal,
            status = status,
        )
    }

    @Test
    fun `given the journal held records the reader could not parse then the run says how many`() = runTest {
        // Пропуск не оставлял следа, и «backfill закончен» не следовало ни из чего (AD-11).
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        journal.docs = mapOf(
            catalogId to listOf(
                unreadable(10L, "legacy-1"),
                unreadable(11L, "legacy-2"),
                unreadable(12L, "legacy-3"),
            ),
        )

        orchestrator.sync()

        assertEquals(3, status.unreadable)
        // Непонятое не пропало молча и не осталось перед курсором: иначе оно читалось бы вечно.
        assertEquals(SyncCursor(12L, "legacy-3"), syncState.getCursor(catalogSyncCursorId(catalogId)))
    }

    @Test
    fun `given a change record with no node id then it counts as unreadable too`() = runTest {
        // Такая запись столь же бесполезна и по той же причине: узел, который надо перечитать,
        // не назван. Раньше она отсеивалась молча.
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        journal.docs = mapOf(catalogId to listOf(change(catalogId, CatalogSyncNodeType.Quest, "", 10L, docId = "quest_")))

        orchestrator.sync()

        assertEquals(1, status.unreadable)
    }

    @Test
    fun `given a clean journal then the count is reported as zero, not left behind`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)

        orchestrator.sync()

        assertEquals(0, status.unreadable, "ноль обязан быть сказан: иначе прошлое число живёт вечно")
    }

    @Test
    fun `sync fetches structure nodes from catalog change list and advances catalog cursor`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        syncState.setCursor(catalogSyncCursorId(catalogId), SyncCursor(50L, "quest_old"))
        journal.docs = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Quest, "quest-1", 100L),
                change(catalogId, CatalogSyncNodeType.Quest, "quest-1", 110L, docId = "quest_quest-1_again"),
                change(catalogId, CatalogSyncNodeType.Section, "section-1", 120L),
                change(catalogId, CatalogSyncNodeType.Theme, "theme-1", 130L),
                change(catalogId, CatalogSyncNodeType.Lesson, "lesson-1", 140L),
                change(catalogId, CatalogSyncNodeType.Question, "question-1", 150L),
                change(catalogId, CatalogSyncNodeType.Catalog, "cat-1", 160L),
                change(catalogId, CatalogSyncNodeType.Question, "", 170L, docId = "question_"),
            ),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(catalogId to SyncCursor(50L, "quest_old"), CatalogId("courses") to SyncCursor.BEGINNING),
            journal.requests,
        )
        assertTrue(setOf(catalogId) in catalogRepo.refreshByIdsRequests)
        assertEquals(setOf(QuestId("quest-1")), questRepo.lastRefreshByIds)
        assertEquals(setOf(SectionId("section-1")), sectionRepo.lastRefreshByIds)
        assertEquals(setOf(ThemeId("theme-1")), themeRepo.lastRefreshByIds)
        assertEquals(setOf(LessonId("lesson-1")), lessonRepo.lastRefreshByIds)
        assertEquals(1, questionRepo.refreshByIdsCallCount)
        assertEquals(setOf(QuestionId("question-1")), questionRepo.lastRefreshByIds)
        // 170, а не 160: запись с пустым id прочитана и применить её нельзя никогда, поэтому
        // курсор проходит и её. Оставаться перед ней значит перечитывать её каждый проход.
        assertEquals(SyncCursor(170L, "question_"), syncState.getCursor(catalogSyncCursorId(catalogId)))
        // Курсор списка — такая же пара, как у журналов: время и id каталога (AD-31). Прежде сюда
        // писалось одно время с пустым id, и курс, ставший видимым в ту же миллисекунду, терялся.
        assertEquals(SyncCursor(1_000L, "cat-1"), syncState.getCursor(CATALOG_LIST_CURSOR_ID))
    }

    @Test
    fun `given two catalogs in one millisecond then the list cursor names which one was read last`() = runTest {
        // Одно число не различало их вовсе: курсор вставал на миллисекунду, следующий проход просил
        // «строго больше», и второй каталог не читался никогда — а вместе с ним и его журнал.
        catalogRepo.nextChangedIds = setOf(CatalogId("cat-a"), CatalogId("cat-b"))

        orchestrator.sync()

        assertEquals(SyncCursor(1_000L, "cat-b"), syncState.getCursor(CATALOG_LIST_CURSOR_ID))
    }

    @Test
    fun `sync does not advance catalog change cursor when concrete node refresh fails`() = runTest {
        val catalogId = CatalogId("cat-1")
        val cursorId = catalogSyncCursorId(catalogId)
        catalogRepo.nextChangedIds = setOf(catalogId)
        syncState.setCursor(cursorId, SyncCursor(50L, "quest_old"))
        journal.docs = mapOf(catalogId to listOf(change(catalogId, CatalogSyncNodeType.Lesson, "lesson-1", 150L)))
        lessonRepo.setNextRefreshByIdsFailure(IllegalStateException("lesson refresh failed"))

        val result = orchestrator.sync()

        assertTrue(result.isFailure)
        assertEquals(SyncCursor(50L, "quest_old"), syncState.getCursor(cursorId))
    }

    @Test
    fun `sync scans every catalog returned by catalog refresh`() = runTest {
        val firstCatalog = CatalogId("cat-1")
        val secondCatalog = CatalogId("cat-2")
        catalogRepo.nextChangedIds = setOf(firstCatalog, secondCatalog)
        journal.docs = mapOf(
            firstCatalog to listOf(change(firstCatalog, CatalogSyncNodeType.Question, "question-1", 100L)),
            secondCatalog to listOf(change(secondCatalog, CatalogSyncNodeType.Question, "question-2", 200L)),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(firstCatalog to SyncCursor.BEGINNING, secondCatalog to SyncCursor.BEGINNING, CatalogId("courses") to SyncCursor.BEGINNING),
            journal.requests,
        )
        assertEquals(2, questionRepo.refreshByIdsCallCount)
        assertEquals(SyncCursor(100L, "question_question-1"), syncState.getCursor(catalogSyncCursorId(firstCatalog)))
        assertEquals(SyncCursor(200L, "question_question-2"), syncState.getCursor(catalogSyncCursorId(secondCatalog)))
    }

    @Test
    fun `sync skips questions for courses catalog`() = runTest {
        val catalogId = CatalogId("courses")
        catalogRepo.nextChangedIds = setOf(catalogId)
        journal.docs = mapOf(catalogId to listOf(change(catalogId, CatalogSyncNodeType.Question, "question-1", 100L)))

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(0, questionRepo.refreshByIdsCallCount)
        assertEquals(SyncCursor(100L, "question_question-1"), syncState.getCursor(catalogSyncCursorId(catalogId)))
    }

    @Test
    fun `sync scans courses even when local catalog cache is empty`() = runTest {
        val courses = CatalogId("courses")
        catalogRepo.nextChangedIds = emptySet()
        journal.docs = mapOf(
            courses to listOf(
                change(courses, CatalogSyncNodeType.Catalog, "courses", 90L),
                change(courses, CatalogSyncNodeType.Quest, "quest-course", 100L),
            ),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(courses to SyncCursor.BEGINNING), journal.requests)
        assertEquals(setOf(QuestId("quest-course")), questRepo.lastRefreshByIds)
        assertEquals(SyncCursor(100L, "quest_quest-course"), syncState.getCursor(catalogSyncCursorId(courses)))
    }

    @Test
    fun `sync reads courses from the saved cursor even when the local archive shelf is empty`() = runTest {
        // Форса «с нуля» в обычном проходе нет: он существует только как явная операция ресинка
        // (AD-30). Прежний хардкод для каталога курсов перечитывал журнал целиком при каждой
        // пустой полке архива.
        val courses = CatalogId("courses")
        catalogRepo.nextChangedIds = setOf(courses)
        syncState.setCursor(catalogSyncCursorId(courses), SyncCursor(500L, "quest_seen"))
        journal.docs = mapOf(courses to listOf(change(courses, CatalogSyncNodeType.Quest, "quest-course", 100L)))

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(courses to SyncCursor(500L, "quest_seen")), journal.requests)
        assertEquals(0, questRepo.refreshByIdsCallCount)
    }

    @Test
    fun `sync catalog structure applies hierarchy changes but skips question refresh`() = runTest {
        val catalogId = CatalogId("cat-1")
        journal.docs = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Lesson, "lesson-1", 100L),
                change(catalogId, CatalogSyncNodeType.Question, "question-1", 200L),
            ),
        )

        val result = orchestrator.syncCatalogStructure(catalogId)

        assertTrue(result.isSuccess)
        assertEquals(setOf(LessonId("lesson-1")), lessonRepo.lastRefreshByIds)
        assertEquals(0, questionRepo.refreshByIdsCallCount)
        assertEquals(SyncCursor(200L, "question_question-1"), syncState.getCursor(catalogSyncCursorId(catalogId)))
    }

    // --- Курсор — пара (AD-31, история 7.3) ---

    @Test
    fun `given two records in one millisecond and both applied then the next run returns neither`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        journal.docs = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Quest, "quest-a", 1_000L),
                change(catalogId, CatalogSyncNodeType.Quest, "quest-b", 1_000L),
            ),
        )

        orchestrator.sync()
        assertEquals(setOf(QuestId("quest-a"), QuestId("quest-b")), questRepo.lastRefreshByIds)
        assertEquals(SyncCursor(1_000L, "quest_quest-b"), syncState.getCursor(catalogSyncCursorId(catalogId)))
        questRepo.resetAll()

        orchestrator.sync()

        assertEquals(0, questRepo.refreshByIdsCallCount, "ни одна из двух не вернулась повторно")
    }

    @Test
    fun `given only the first of two same-millisecond records was applied then the second still comes`() = runTest {
        // Сегодня по одному числу вторая запись пропускалась без следа.
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        journal.docs = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Quest, "quest-a", 1_000L),
                change(catalogId, CatalogSyncNodeType.Quest, "quest-b", 1_000L),
            ),
        )
        syncState.setCursor(catalogSyncCursorId(catalogId), SyncCursor(1_000L, "quest_quest-a"))

        orchestrator.sync()

        assertEquals(setOf(QuestId("quest-b")), questRepo.lastRefreshByIds)
        assertEquals(SyncCursor(1_000L, "quest_quest-b"), syncState.getCursor(catalogSyncCursorId(catalogId)))
    }

    @Test
    fun `given a cursor migrated from a bare number then its millisecond is reread and reapplying changes nothing`() = runTest {
        // Пустая вторая половина — граница не позже прежней: записи не теряются, а повтор
        // применения идемпотентен.
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        val quest = quest("quest-a", catalogId)
        questRepo.seed(listOf(quest))
        questRepo.seedServerQuests(listOf(quest))
        journal.docs = mapOf(catalogId to listOf(change(catalogId, CatalogSyncNodeType.Quest, "quest-a", 1_000L)))
        syncState.setCursor(catalogSyncCursorId(catalogId), SyncCursor(1_000L, ""))
        val before = questRepo.snapshot()

        orchestrator.sync()

        assertEquals(setOf(QuestId("quest-a")), questRepo.lastRefreshByIds, "миллисекунда курсора перечитана")
        assertEquals(before, questRepo.snapshot(), "повторное применение не меняет ни одной строки")
        assertEquals(SyncCursor(1_000L, "quest_quest-a"), syncState.getCursor(catalogSyncCursorId(catalogId)))
    }

    @Test
    fun `given the cursor is already ahead then a smaller page does not move it back`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        syncState.setCursor(catalogSyncCursorId(catalogId), SyncCursor(5_000L, "quest_z"))
        journal.ignoreCursor = true
        journal.docs = mapOf(catalogId to listOf(change(catalogId, CatalogSyncNodeType.Quest, "quest-a", 100L)))

        orchestrator.sync()

        assertEquals(SyncCursor(5_000L, "quest_z"), syncState.getCursor(catalogSyncCursorId(catalogId)))
    }

    @Test
    fun `given a journal longer than a page then it is read page by page and the cursor follows each page`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        journal.pageSize = 2
        journal.docs = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Quest, "quest-1", 100L),
                change(catalogId, CatalogSyncNodeType.Quest, "quest-2", 100L),
                change(catalogId, CatalogSyncNodeType.Quest, "quest-3", 200L),
            ),
        )

        orchestrator.sync()

        val cursorId = catalogSyncCursorId(catalogId)
        assertEquals(
            listOf(catalogId to SyncCursor.BEGINNING, catalogId to SyncCursor(100L, "quest_quest-2"), CatalogId("courses") to SyncCursor.BEGINNING),
            journal.requests,
        )
        assertEquals(
            listOf(cursorId to SyncCursor(100L, "quest_quest-2"), cursorId to SyncCursor(200L, "quest_quest-3")),
            syncState.setCursorCalls.filter { it.first == cursorId },
        )
    }

    // --- Удаление тремя способами и защитный fallback читателя (AD-26, история 7.6) ---

    @Test
    fun `given the journal names a node the server no longer returns then the local row is removed`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        questRepo.seed(listOf(quest("quest-gone", catalogId), quest("quest-kept", catalogId)))
        questRepo.seedServerQuests(listOf(quest("quest-kept", catalogId)))
        journal.docs = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Quest, "quest-gone", 100L),
                change(catalogId, CatalogSyncNodeType.Quest, "quest-kept", 101L),
            ),
        )

        orchestrator.sync()

        assertEquals(listOf("quest-kept"), questRepo.snapshot().map { it.id.value })
    }

    @Test
    fun `given a node with an empty visibleOn then the local row is removed`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        questRepo.seed(listOf(quest("quest-1", catalogId)))
        questRepo.seedServerQuests(listOf(quest("quest-1", catalogId, visibleOn = emptySet())))
        journal.docs = mapOf(catalogId to listOf(change(catalogId, CatalogSyncNodeType.Quest, "quest-1", 100L)))

        orchestrator.sync()

        assertEquals(emptyList(), questRepo.snapshot())
    }

    @Test
    fun `given an archived node then the local row stays and is only hidden`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        questRepo.seed(listOf(quest("quest-1", catalogId)))
        questRepo.seedServerQuests(listOf(quest("quest-1", catalogId, archived = true)))
        journal.docs = mapOf(catalogId to listOf(change(catalogId, CatalogSyncNodeType.Quest, "quest-1", 100L)))

        orchestrator.sync()

        assertEquals(listOf(true), questRepo.snapshot().map { it.archived })
    }

    @Test
    fun `given the reader could not parse a record then the run still applies the rest and reports the skip`() = runTest {
        // Защитный fallback: непонятная запись не рушит проход и не пропадает молча (AD-11).
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        journal.docs = mapOf(
            catalogId to listOf(
                unreadable(100L, "1700-quest-legacy"),
                change(catalogId, CatalogSyncNodeType.Quest, "quest-1", 100L),
            ),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(setOf(QuestId("quest-1")), questRepo.lastRefreshByIds)
        assertEquals(1, status.unreadable)
    }

    private fun change(
        catalogId: CatalogId,
        type: CatalogSyncNodeType,
        nodeId: String,
        changedAtMs: Long,
        docId: String = "${type.name.lowercase()}_$nodeId",
    ): JournalDoc =
        JournalDoc(
            cursor = SyncCursor(changedAtMs, docId),
            change = CatalogSyncChange(catalogId = catalogId, type = type, nodeId = nodeId, changedAtMs = changedAtMs),
        )

    private fun unreadable(changedAtMs: Long, docId: String): JournalDoc = JournalDoc(SyncCursor(changedAtMs, docId), change = null)

    private fun quest(id: String, catalogId: CatalogId, visibleOn: Set<String> = setOf("home"), archived: Boolean = false): Quest =
        Quest(
            id = QuestId(id),
            catalogId = catalogId,
            authorUid = "author",
            title = id,
            picturePath = null,
            visibleOn = visibleOn,
            version = 1L,
            lastModifiedAt = 1L,
            archived = archived,
        )
}

/** Документ журнала, как его видит читатель: пара курсора и разобранная запись — или ничего. */
internal data class JournalDoc(val cursor: SyncCursor, val change: CatalogSyncChange?)

/** Журнал каталога в памяти: страница строго после курсора по паре, как в Firestore. */
internal class FakeCatalogJournal : CatalogSyncChangeRemoteDataSource {
    var docs: Map<CatalogId, List<JournalDoc>> = emptyMap()
    var pageSize: Int = SYNC_PAGE_SIZE

    /** Отдавать всё, не глядя на курсор, — чтобы проверить, что курсор назад не откатывается. */
    var ignoreCursor: Boolean = false
    val requests = mutableListOf<Pair<CatalogId, SyncCursor>>()

    override suspend fun fetchPage(
        catalogId: CatalogId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<CatalogSyncChange> {
        requests.add(catalogId to cursor)
        val size = minOf(limit, pageSize)
        val slice = docs[catalogId].orEmpty()
            .sortedBy { it.cursor }
            .filter { ignoreCursor || it.cursor > cursor }
            .take(size)
        return journalPage(slice, size, { it.cursor }, { it.change })
    }
}

internal class RecordingSyncStatus : SyncStatusRepository {
    var unreadable: Int? = null
    val byJournal = mutableMapOf<String, Int>()

    override fun observeStatus() = throw UnsupportedOperationException("не нужен этому тесту")

    override suspend fun recordSuccess(kind: SyncRunKind, atMs: Long) = Unit

    override suspend fun recordFailure(
        kind: SyncRunKind,
        error: com.tpov.schoolquiz.shared.core.network.SyncError,
        atMs: Long,
    ) = Unit

    override suspend fun recordUnreadableChanges(journal: String, count: Int) {
        unreadable = count
        byJournal[journal] = count
    }
}
