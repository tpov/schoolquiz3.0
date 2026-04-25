package com.tpov.schoolquiz.shared.core.catalog.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.fake.FakeCatalogRepository
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Domain Test Scenarios for the [CatalogRepository] contract, driven through
 * [FakeCatalogRepository] and [ObserveCatalogsUseCase].
 *
 * Spec reference: docs/features/catalog-foundation/0-spec.md § "Domain Test Scenarios".
 *
 * Covered scenarios:
 *   1     : empty cache → empty list on first emission
 *   2     : cached 2 catalogs → list sorted by id ASC
 *   11    : fake with 3 catalogs → emits list of 3
 *   12    : refreshFromRemote success + new catalog → observeAll sees new entry
 *   13    : refreshFromRemote failure → observeAll keeps emitting cached list
 *   14-15 : getById for present / absent ids
 *   18    : ordering stability regardless of insertion order
 *   19    : client-side sort after non-deterministic remote order
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogRepositoryContractTest {

    // ── Scenario 1 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 1 empty cache emits empty list on first collection`() = runTest {
        val fake = FakeCatalogRepository(initial = emptyList())
        val useCase = ObserveCatalogsUseCase(fake)

        val first = useCase().first()

        assertEquals(emptyList(), first)
    }

    // ── Scenario 2 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 2 cached two catalogs emits list sorted by id ASC (courses, surveys)`() = runTest {
        val surveys = Catalog(CatalogId("surveys"), "Опросы", null)
        val courses = Catalog(CatalogId("courses"), "Курсы", "catalog-pictures/courses.jpg")
        // Note: insertion order is [surveys, courses] — spec expects sorted [courses, surveys].
        val fake = FakeCatalogRepository(initial = listOf(surveys, courses))
        val useCase = ObserveCatalogsUseCase(fake)

        val first = useCase().first()

        assertEquals(listOf(courses, surveys), first)
    }

    // ── Scenario 11 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 11 fake initialized with three catalogs emits list of three`() = runTest {
        val three = listOf(
            Catalog(CatalogId("a"), "Alpha", null),
            Catalog(CatalogId("b"), "Beta", null),
            Catalog(CatalogId("c"), "Gamma", null),
        )
        val fake = FakeCatalogRepository(initial = three)
        val useCase = ObserveCatalogsUseCase(fake)

        val first = useCase().first()

        assertEquals(3, first.size)
    }

    // ── Scenario 12 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 12 refreshFromRemote success with new catalog triggers new emit with added catalog`() = runTest {
        val existing = Catalog(CatalogId("surveys"), "Опросы", null)
        val fake = FakeCatalogRepository(initial = listOf(existing))
        val useCase = ObserveCatalogsUseCase(fake)
        val collected = mutableListOf<List<Catalog>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase().toList(collected)
        }

        val added = Catalog(CatalogId("courses"), "Курсы", "catalog-pictures/courses.jpg")
        fake.simulateRemoteCatalogs(listOf(existing, added))
        val refresh = fake.refreshFromRemote()

        job.cancel()

        assertTrue(refresh.isSuccess)
        // First emission was the initial [surveys]; after refresh a new emission contains both, sorted by id ASC.
        val last = collected.last()
        assertEquals(listOf(added, existing), last) // courses < surveys
    }

    // ── Scenario 13 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 13 refreshFromRemote failure does not alter cached observeAll emissions`() = runTest {
        val existing = listOf(
            Catalog(CatalogId("courses"), "Курсы", null),
            Catalog(CatalogId("surveys"), "Опросы", null),
        )
        val fake = FakeCatalogRepository(initial = existing)
        val useCase = ObserveCatalogsUseCase(fake)
        val collected = mutableListOf<List<Catalog>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase().toList(collected)
        }

        fake.setNextRefreshFailure(RuntimeException("network lost"))
        val refresh = fake.refreshFromRemote()

        job.cancel()

        assertTrue(refresh.isFailure)
        // Cache unchanged; all collected emissions equal the sorted existing list.
        val expected = existing.sortedBy { it.id.value }
        assertTrue(collected.isNotEmpty())
        assertTrue(collected.all { it == expected })
    }

    // ── Scenario 14 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 14 getById returns non-null Catalog for known id`() = runTest {
        val surveys = Catalog(CatalogId("surveys"), "Опросы", null)
        val courses = Catalog(CatalogId("courses"), "Курсы", null)
        val fake = FakeCatalogRepository(initial = listOf(surveys, courses))

        val resolved = fake.getById(CatalogId("surveys"))

        assertNotNull(resolved)
        assertEquals(surveys, resolved)
    }

    // ── Scenario 15 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 15 getById returns null for unknown id`() = runTest {
        val fake = FakeCatalogRepository(
            initial = listOf(
                Catalog(CatalogId("surveys"), "Опросы", null),
                Catalog(CatalogId("courses"), "Курсы", null),
            ),
        )

        val resolved = fake.getById(CatalogId("unknown"))

        assertNull(resolved)
    }

    // ── Scenario 18 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 18 observeAll emits catalogs sorted by id ASC regardless of insertion order`() = runTest {
        val surveys = Catalog(CatalogId("surveys"), "Опросы", null)
        val courses = Catalog(CatalogId("courses"), "Курсы", null)
        val school = Catalog(CatalogId("school"), "Школа", null)
        val games = Catalog(CatalogId("games"), "Игры", null)
        // Insert in the spec-provided scrambled order.
        val fake = FakeCatalogRepository(initial = listOf(surveys, courses, school, games))
        val useCase = ObserveCatalogsUseCase(fake)

        val first = useCase().first()

        assertEquals(listOf(courses, games, school, surveys), first)
    }

    // ── Scenario 19 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 19 non-deterministic remote order is normalized to id ASC after refreshFromRemote`() = runTest {
        val fake = FakeCatalogRepository(initial = emptyList())
        val useCase = ObserveCatalogsUseCase(fake)
        val collected = mutableListOf<List<Catalog>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase().toList(collected)
        }

        // Simulate server returning catalogs in arbitrary order.
        val surveys = Catalog(CatalogId("surveys"), "Опросы", null)
        val courses = Catalog(CatalogId("courses"), "Курсы", null)
        val school = Catalog(CatalogId("school"), "Школа", null)
        val games = Catalog(CatalogId("games"), "Игры", null)
        fake.simulateRemoteCatalogs(listOf(school, surveys, courses, games))
        val refresh = fake.refreshFromRemote()

        job.cancel()

        assertTrue(refresh.isSuccess)
        // Last emission must be sorted by id ASC — client-side stable sort.
        assertEquals(listOf(courses, games, school, surveys), collected.last())
    }

    // ── Scenario 21 (Delta-sync contract) ────────────────────────────────────
    // GIVEN FakeCatalogRepository empty AND pull returns [catalog(v=1)]
    // WHEN sync THEN observeAll emits [catalog]
    @Test
    fun `scenario 21 first sync with empty local inserts all returned catalogs`() = runTest {
        val fake = FakeCatalogRepository(initial = emptyList())
        val useCase = ObserveCatalogsUseCase(fake)

        val newCatalog = Catalog(
            id = CatalogId("courses"),
            name = "Курсы",
            picturePath = null,
            version = 1L,
            lastModifiedAt = 1000L,
        )
        fake.seedServerCatalogs(listOf(newCatalog))
        fake.refreshFromRemote()

        val emitted = useCase().first()
        assertEquals(1, emitted.size)
        assertEquals(newCatalog, emitted[0])
    }

    // ── Scenario 22 (Delta-sync version skip) ────────────────────────────────
    // GIVEN FakeCatalogRepository has catalog(v=2) AND pull returns catalog(v=2) (same version)
    // WHEN sync THEN no change (upsert skipped)
    @Test
    fun `scenario 22 same version dto is skipped and local catalog unchanged`() = runTest {
        val localCatalog = Catalog(
            id = CatalogId("courses"),
            name = "Курсы",
            picturePath = null,
            version = 2L,
            lastModifiedAt = 1000L,
        )
        val fake = FakeCatalogRepository(initial = listOf(localCatalog))
        val useCase = ObserveCatalogsUseCase(fake)

        // Server returns same version with a different name — should be ignored
        val serverDtoSameVersion = localCatalog.copy(name = "Курсы (edited on server)", version = 2L, lastModifiedAt = 1500L)
        fake.seedServerCatalogs(listOf(serverDtoSameVersion))
        fake.refreshFromRemote()

        val emitted = useCase().first()
        assertEquals(1, emitted.size)
        assertEquals("Курсы", emitted[0].name, "Name must not be overwritten when version is equal")
        assertEquals(2L, emitted[0].version)
    }

    // ── Scenario 23 (Delta-sync archived delete) ─────────────────────────────
    // GIVEN FakeCatalogRepository has catalog(v=1) AND pull returns catalog(v=3, archived=true)
    // WHEN sync THEN observeAll emits empty (local deleted)
    @Test
    fun `scenario 23 archived dto causes local deletion regardless of version`() = runTest {
        val localCatalog = Catalog(
            id = CatalogId("courses"),
            name = "Курсы",
            picturePath = null,
            version = 1L,
            lastModifiedAt = 500L,
        )
        val fake = FakeCatalogRepository(initial = listOf(localCatalog))
        val useCase = ObserveCatalogsUseCase(fake)

        val archivedDto = localCatalog.copy(version = 3L, archived = true, lastModifiedAt = 2000L)
        fake.seedServerCatalogs(listOf(archivedDto))
        fake.refreshFromRemote()

        val emitted = useCase().first()
        assertTrue(emitted.isEmpty(), "Archived catalog must be deleted locally; got: $emitted")
    }

    // ── Cursor advancement test ───────────────────────────────────────────────
    @Test
    fun `refreshFromRemote advances cursor to max lastModifiedAt of returned items`() = runTest {
        val fake = FakeCatalogRepository(initial = emptyList())

        val c1 = Catalog(CatalogId("a"), "Alpha", null, version = 1L, lastModifiedAt = 1000L)
        val c2 = Catalog(CatalogId("b"), "Beta", null, version = 1L, lastModifiedAt = 3000L)
        val c3 = Catalog(CatalogId("c"), "Gamma", null, version = 1L, lastModifiedAt = 2000L)
        fake.seedServerCatalogs(listOf(c1, c2, c3))
        fake.refreshFromRemote()

        assertEquals(3000L, fake.lastRefreshCursor, "Cursor must advance to max lastModifiedAt")
    }

    // ── Scenario 23b (version guard: stale tombstone SKIP) ───────────────────
    // GIVEN local catalog(v=5) AND server sends archived=true, version=3 (stale)
    // WHEN sync THEN local preserved (version guard fires before delete-marker)
    // Spec State Matrix 1: present + dto.v < local.v → SKIP regardless of archived
    @Test
    fun `scenario 23b stale tombstone archived=true version below local THEN local preserved (version guard)`() = runTest {
        val localCatalog = Catalog(
            id = CatalogId("courses"),
            name = "Курсы",
            picturePath = null,
            version = 5L,
            lastModifiedAt = 1000L,
        )
        val fake = FakeCatalogRepository(initial = listOf(localCatalog))
        val useCase = ObserveCatalogsUseCase(fake)

        // Server sends stale archived=true with version=3 (below local v=5)
        val staleTombstone = localCatalog.copy(version = 3L, archived = true, lastModifiedAt = 2000L)
        fake.seedServerCatalogs(listOf(staleTombstone))
        fake.refreshFromRemote()

        val emitted = useCase().first()
        assertEquals(1, emitted.size, "Local catalog must be preserved: stale tombstone ignored by version guard")
        assertEquals("Курсы", emitted[0].name)
        assertEquals(5L, emitted[0].version, "Version must remain 5 (local), not downgraded")
    }

    // ── Scenario 23c (version guard: equal version tombstone SKIP) ───────────
    // GIVEN local catalog(v=5) AND server sends archived=true, version=5 (equal)
    // WHEN sync THEN local preserved (dto.v == local.v → SKIP per spec)
    // Spec State Matrix 1: present + dto.v == local.v → SKIP regardless of archived
    @Test
    fun `scenario 23c equal version tombstone archived=true THEN local preserved (version guard)`() = runTest {
        val localCatalog = Catalog(
            id = CatalogId("courses"),
            name = "Курсы",
            picturePath = null,
            version = 5L,
            lastModifiedAt = 1000L,
        )
        val fake = FakeCatalogRepository(initial = listOf(localCatalog))
        val useCase = ObserveCatalogsUseCase(fake)

        // Server sends archived=true with same version=5
        val equalVersionTombstone = localCatalog.copy(archived = true, lastModifiedAt = 2000L)
        fake.seedServerCatalogs(listOf(equalVersionTombstone))
        fake.refreshFromRemote()

        val emitted = useCase().first()
        assertEquals(1, emitted.size, "Local catalog must be preserved: equal-version tombstone ignored by version guard")
        assertEquals(5L, emitted[0].version, "Version unchanged (equal version tombstone skipped)")
    }

    // ── Archived but absent locally is skipped (no tombstone) ────────────────
    @Test
    fun `refreshFromRemote skips archived dto when local is absent (no tombstone inserted)`() = runTest {
        val fake = FakeCatalogRepository(initial = emptyList())
        val useCase = ObserveCatalogsUseCase(fake)

        // Server returns archived catalog not present locally
        val ghostArchived = Catalog(CatalogId("ghost"), "Ghost", null, archived = true, lastModifiedAt = 500L)
        fake.seedServerCatalogs(listOf(ghostArchived))
        fake.refreshFromRemote()

        val emitted = useCase().first()
        assertTrue(emitted.isEmpty(), "No tombstone should be created for absent+archived dto")
        assertEquals(0, fake.snapshot().size)
    }
}
