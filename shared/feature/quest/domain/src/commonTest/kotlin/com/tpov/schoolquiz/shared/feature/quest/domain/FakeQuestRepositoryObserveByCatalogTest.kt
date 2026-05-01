package com.tpov.schoolquiz.shared.feature.quest.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies FakeQuestRepository.observeByCatalog in-memory filter semantics.
 *
 * Scenarios: RX-01..04 from 04-testing.md §11.1 plus shelf exact-match edge cases.
 * Spec: docs/features/quizzes-screen/plan/phase-01/tests.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeQuestRepositoryObserveByCatalogTest {

    private val catA = CatalogId("A")
    private val catB = CatalogId("B")

    // ── RX-01: unknown catalogId → emits empty list ──────────────────────────
    @Test
    fun `when unknown catalogId then emits empty list`() = runTest {
        val repo = FakeQuestRepository(
            initial = listOf(makeQuest(id = "q1", catalogId = catA, visibleOn = setOf("home"))),
        )

        val result = repo.observeByCatalog(catB, "home").first()

        assertEquals(emptyList(), result)
    }

    // ── RX-02: known catalogId → only matching quests emitted ────────────────
    @Test
    fun `when known catalogId then emits matching quests only`() = runTest {
        val questA = makeQuest(id = "a1", catalogId = catA, visibleOn = setOf("home"))
        val questB = makeQuest(id = "b1", catalogId = catB, visibleOn = setOf("home"))
        val repo = FakeQuestRepository(initial = listOf(questA, questB))

        val result = repo.observeByCatalog(catA, "home").first()

        assertEquals(1, result.size)
        assertEquals(QuestId("a1"), result[0].id)
    }

    // ── RX-03: backing data changes → Flow re-emits ──────────────────────────
    @Test
    fun `when backing data changes then flow re-emits`() = runTest {
        val initial = makeQuest(id = "a1", catalogId = catA, visibleOn = setOf("home"))
        val repo = FakeQuestRepository(initial = listOf(initial))

        val emissions = mutableListOf<List<Quest>>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.observeByCatalog(catA, "home").take(2).toList(emissions)
        }

        val added = makeQuest(id = "a2", catalogId = catA, visibleOn = setOf("home"))
        repo.seed(listOf(initial, added))

        collectJob.join()

        assertEquals(2, emissions.size, "Flow must re-emit after backing store change")
        assertEquals(2, emissions[1].size, "Second emission includes both quests")
    }

    // ── RX-04: archived quest → included as on-demand result ─────────────────
    @Test
    fun `when quest is archived then included`() = runTest {
        val archivedQuest = makeQuest(id = "archived", catalogId = catA, visibleOn = setOf("home"), archived = true)
        val activeQuest = makeQuest(id = "active", catalogId = catA, visibleOn = setOf("home"), archived = false)
        val repo = FakeQuestRepository(initial = listOf(archivedQuest, activeQuest))

        val result = repo.observeByCatalog(catA, "home").first()

        assertEquals(2, result.size)
        assertEquals(setOf(QuestId("active"), QuestId("archived")), result.map { it.id }.toSet())
    }

    // ── Shelf exact match: "tournament" must NOT match "tournamentFinal" ───────
    @Test
    fun `when shelf is tournament then quest with visibleOn tournamentFinal is not returned`() = runTest {
        val repo = FakeQuestRepository(
            initial = listOf(makeQuest(id = "extra", catalogId = catA, visibleOn = setOf("tournamentFinal"))),
        )

        val result = repo.observeByCatalog(catA, "tournament").first()

        assertTrue(result.isEmpty(), "tournamentFinal must not match shelf=tournament (exact element match, not prefix)")
    }

    @Test
    fun `when shelf is home then quest with visibleOn home is returned`() = runTest {
        val repo = FakeQuestRepository(
            initial = listOf(makeQuest(id = "q1", catalogId = catA, visibleOn = setOf("home"))),
        )

        val result = repo.observeByCatalog(catA, "home").first()

        assertEquals(1, result.size)
        assertEquals(QuestId("q1"), result[0].id)
    }

    // ── Edge: all archived → emits on-demand roots ──────────────────────────
    @Test
    fun `when all quests in catalog are archived then emits them`() = runTest {
        val repo = FakeQuestRepository(
            initial = listOf(
                makeQuest(id = "q1", catalogId = catA, visibleOn = setOf("home"), archived = true),
                makeQuest(id = "q2", catalogId = catA, visibleOn = setOf("home"), archived = true),
            ),
        )

        val result = repo.observeByCatalog(catA, "home").first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.archived })
    }

    // ── Edge: empty backing store → emits empty list ─────────────────────────
    @Test
    fun `when no quests in backing store then emits empty list`() = runTest {
        val result = FakeQuestRepository().observeByCatalog(catA, "home").first()

        assertTrue(result.isEmpty())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeQuest(
        id: String = "q1",
        catalogId: CatalogId = catA,
        visibleOn: Set<String> = setOf("home"),
        archived: Boolean = false,
        lastModifiedAt: Long = 0L,
    ) = Quest(
        id = QuestId(id),
        catalogId = catalogId,
        authorUid = "uid-42",
        title = "Test Quest",
        picturePath = null,
        visibleOn = visibleOn,
        averageRating = null,
        averageRatingCount = 0,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
