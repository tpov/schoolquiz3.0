package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeQuestLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeQuestRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM tests for QuestRepositoryImpl.
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §2
 *
 * OPEN QUESTION: tests.md specifies refreshFromRemote should return Result<Set<QuestId>>
 * for cascade trigger, but QuestRepository.refreshFromRemote returns Result<Unit>.
 * Tests verify side-effects (upserts/deletes) instead of return value where applicable.
 * Track: waiting for domain interface clarification from lead.
 */
class QuestRepositoryImplTest {

    private val fakeLocal = FakeQuestLocalDataSource()
    private val noOpResolver: StorageUrlResolver = StorageUrlResolver { path ->
        "https://fake.example.com/$path"
    }

    private val availableShelves = setOf("home", "arena")
    private val catalogIds = setOf(CatalogId("c1"))

    private fun makeDto(
        id: String,
        version: Long = 1L,
        archived: Boolean = false,
        visibleOn: List<String> = listOf("home"),
        catalogId: String = "c1",
    ) = QuestDto(id, catalogId, "uid-a", "Title $id", null, visibleOn, null, 0, version, 100L, archived)

    private fun makeEntity(id: String, version: Long = 1L) = QuestEntity(
        id = id,
        catalogId = "c1",
        authorUid = "uid-a",
        title = "Title $id",
        picturePath = null,
        pictureUrl = null,
        visibleOn = setOf("home"),
        averageRating = null,
        averageRatingCount = 0,
        version = version,
        lastModifiedAt = 0L,
        archived = false,
    )

    private fun makeRepository(
        local: FakeQuestLocalDataSource = fakeLocal,
        remote: FakeQuestRemoteDataSource = FakeQuestRemoteDataSource(),
    ) = QuestRepositoryImpl(
        local = local,
        remote = remote,
        storageUrlResolver = noOpResolver,
    )

    // AC#9: currentUserUid not null → both queries A and B called
    @Test
    fun `when currentUserUid not null then both queries called`() = runTest {
        val fakeRemote = FakeQuestRemoteDataSource()
        val repo = makeRepository(remote = fakeRemote)

        val result = repo.refreshFromRemote("uid-a", availableShelves, catalogIds, 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeRemote.fetchOwnChangedCallCount)
        assertEquals(1, fakeRemote.fetchPublicChangedCallCount)
    }

    // Empty catalogIds → no-op, both queries skipped
    @Test
    fun `when catalogIdsToSync empty then no queries called and success returned`() = runTest {
        val fakeRemote = FakeQuestRemoteDataSource()
        val repo = makeRepository(remote = fakeRemote)

        val result = repo.refreshFromRemote("uid-a", availableShelves, emptySet(), 0L)

        assertTrue(result.isSuccess)
        assertEquals(0, fakeRemote.fetchOwnChangedCallCount)
        assertEquals(0, fakeRemote.fetchPublicChangedCallCount)
    }

    // Matrix 1 Quest dedupe: same quest id in A and B → upserted exactly once
    @Test
    fun `when same quest in A and B then exactly one upserted`() = runTest {
        val dto = makeDto("q1")
        val fakeRemote = FakeQuestRemoteDataSource(
            ownResults = listOf(dto),
            publicResults = listOf(dto),
        )
        val local = FakeQuestLocalDataSource()
        val repo = makeRepository(local = local, remote = fakeRemote)

        repo.refreshFromRemote("uid-a", availableShelves, catalogIds, 0L)

        assertEquals(1, local.upsertCallsFor["q1"] ?: 0)
    }

    // Matrix 1 Quest 1.3: archived=true → upserted as on-demand marker
    @Test
    fun `when quest archived true then upserted`() = runTest {
        val local = FakeQuestLocalDataSource()
        local.seed(listOf(makeEntity("q1")))
        val fakeRemote = FakeQuestRemoteDataSource(
            publicResults = listOf(makeDto("q1", version = 2L, archived = true)),
        )
        val repo = makeRepository(local = local, remote = fakeRemote)

        repo.refreshFromRemote("uid-a", availableShelves, catalogIds, 0L)

        assertEquals(1, local.upsertCallsFor["q1"] ?: 0)
        assertTrue(local.findById("q1")?.archived == true)
    }

    // EDGE 1.9 / AC#48: visibleOn=[] → deleted locally
    @Test
    fun `when quest visibleOn empty then deleted`() = runTest {
        val local = FakeQuestLocalDataSource()
        local.seed(listOf(makeEntity("q1")))
        val fakeRemote = FakeQuestRemoteDataSource(
            publicResults = listOf(makeDto("q1", version = 2L, visibleOn = emptyList())),
        )
        val repo = makeRepository(local = local, remote = fakeRemote)

        repo.refreshFromRemote("uid-a", availableShelves, catalogIds, 0L)

        assertTrue(local.deletedIds.contains("q1"))
    }

    // Changed quest upserted after refresh
    @Test
    fun `when refresh succeeds then upserted quest observable via local`() = runTest {
        val local = FakeQuestLocalDataSource()
        local.seed(listOf(makeEntity("q1")))
        val fakeRemote = FakeQuestRemoteDataSource(
            publicResults = listOf(makeDto("q1", version = 2L)),
        )
        val repo = makeRepository(local = local, remote = fakeRemote)

        val result = repo.refreshFromRemote("uid-a", availableShelves, catalogIds, 0L)

        assertTrue(result.isSuccess)
        assertTrue((local.upsertCallsFor["q1"] ?: 0) >= 1, "Expected q1 to be upserted after version bump")
        // OPEN QUESTION: tests.md expects result.getOrNull() contains QuestId("q1")
        // but QuestRepository.refreshFromRemote returns Result<Unit>.
        // Pending domain interface update per backend.md spec §6g.
    }

    @Test
    fun `when remote quest returned with unchanged version then id still cascades`() = runTest {
        val local = FakeQuestLocalDataSource()
        local.seed(listOf(makeEntity("q1", version = 5L)))
        val fakeRemote = FakeQuestRemoteDataSource(
            publicResults = listOf(makeDto("q1", version = 5L)),
        )
        val repo = makeRepository(local = local, remote = fakeRemote)

        val result = repo.refreshFromRemote("uid-a", availableShelves, catalogIds, 0L)

        assertEquals(setOf(QuestId("q1")), result.getOrThrow())
    }

    // Remote throws → Result.failure
    @Test
    fun `when refresh fails then returns failure`() = runTest {
        val fakeRemote = FakeQuestRemoteDataSource(shouldThrow = true)
        val repo = makeRepository(remote = fakeRemote)

        val result = repo.refreshFromRemote("uid-a", availableShelves, catalogIds, 0L)

        assertTrue(result.isFailure)
    }

    // Non-archived quest upserted normally
    @Test
    fun `when quest not archived then upserted to local`() = runTest {
        val local = FakeQuestLocalDataSource()
        val fakeRemote = FakeQuestRemoteDataSource(
            publicResults = listOf(makeDto("q2")),
        )
        val repo = makeRepository(local = local, remote = fakeRemote)

        repo.refreshFromRemote("uid-a", availableShelves, catalogIds, 0L)

        assertEquals(1, local.upsertCallsFor["q2"] ?: 0)
    }
}
