package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogLocalDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeSyncStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogRepositoryImplTest {

    private val fakeLocal = FakeCatalogLocalDataSource()
    private val fakeRemote = FakeCatalogRemoteDataSource()
    private val fakeUrlResolver = FakeCatalogUrlResolver()
    private val fakeSyncState = FakeSyncStateRepository()

    private val repository = CatalogRepositoryImpl(
        local = fakeLocal,
        remote = fakeRemote,
        storageUrlResolver = fakeUrlResolver.resolver,
        syncStateRepo = fakeSyncState,
    )

    // CF-11: observeAll reads from Room, never calls remote
    @Test
    fun `when observeAll called then remote is never invoked`() = runTest {
        fakeLocal.emit(emptyList())
        repository.observeAll().first()
        assertEquals(0, fakeRemote.fetchChangedSinceCalls)
    }

    // CF-12: refreshFromRemote upserts entity from remote into Room
    @Test
    fun `when refreshFromRemote succeeds then entity upserted with correct id`() = runTest {
        fakeRemote.result = Result.success(
            listOf(CatalogDto(id = "a", name = "A", picturePath = "a.jpg", version = 1L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false)),
        )

        val result = repository.refreshFromRemote()

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.upsertCallsFor["a"])
        assertNotNull(fakeLocal.findById("a"))
        assertEquals("a", fakeLocal.findById("a")?.id)
    }

    // CF-13: refreshFromRemote on remote error → Result.failure
    @Test
    fun `when remote throws then refreshFromRemote returns failure`() = runTest {
        fakeRemote.result = Result.failure(RuntimeException("network error"))

        val result = repository.refreshFromRemote()

        assertTrue(result.isFailure)
    }

    // CF-14: picturePath=null → urlResolver not called, pictureUrl=null
    @Test
    fun `when dto has null picturePath then urlResolver is not called and pictureUrl is null`() = runTest {
        fakeRemote.result = Result.success(
            listOf(CatalogDto(id = "b", name = "B", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false)),
        )

        repository.refreshFromRemote()

        assertEquals(0, fakeUrlResolver.callCount)
        assertNull(fakeLocal.findById("b")?.pictureUrl)
    }

    // CF-15: picturePath with catalog-pictures/ prefix → urlResolver called, url resolved
    @Test
    fun `when dto has catalog-pictures path then urlResolver called and pictureUrl resolved`() = runTest {
        fakeRemote.result = Result.success(
            listOf(CatalogDto(id = "c", name = "C", picturePath = "catalog-pictures/cat-42.jpg", version = 1L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false)),
        )

        repository.refreshFromRemote()

        assertEquals(1, fakeUrlResolver.callCount)
        assertEquals("https://fake.example.com/catalog-pictures/cat-42.jpg", fakeLocal.findById("c")?.pictureUrl)
    }

    // CF-15b: picturePath without catalog-pictures/ prefix → resolver NOT called, pictureUrl=null
    @Test
    fun `when dto has picturePath without catalog-pictures prefix then urlResolver is not called`() = runTest {
        fakeRemote.result = Result.success(
            listOf(CatalogDto(id = "c2", name = "C2", picturePath = "outside/folder/file.jpg", version = 1L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false)),
        )

        repository.refreshFromRemote()

        assertEquals(0, fakeUrlResolver.callCount)
        assertNull(fakeLocal.findById("c2")?.pictureUrl)
    }

    // CF-16: refreshFromRemote() upserts entity and returns success
    @Test
    fun `when refreshFromRemote called then upsert is performed`() = runTest {
        fakeRemote.result = Result.success(
            listOf(CatalogDto(id = "d", name = "D", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false)),
        )

        val result = repository.refreshFromRemote()

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.upsertCallsFor["d"])
    }

    // CF-17: observeAll sorts by id.value ASC regardless of Room order
    @Test
    fun `when room has unordered entities then observeAll emits list sorted by id ASC`() = runTest {
        fakeLocal.emit(
            listOf(
                CatalogEntity(id = "b", name = "B", picturePath = null, pictureUrl = null),
                CatalogEntity(id = "a", name = "A", picturePath = null, pictureUrl = null),
            ),
        )

        val catalogs = repository.observeAll().first()

        assertEquals(2, catalogs.size)
        assertEquals(CatalogId("a"), catalogs[0].id)
        assertEquals(CatalogId("b"), catalogs[1].id)
    }

    // CF-18: getById returns Catalog for existing ID
    @Test
    fun `when room has entity then getById returns catalog with matching id`() = runTest {
        fakeLocal.emit(
            listOf(
                CatalogEntity(id = "test-id", name = "Test", picturePath = null, pictureUrl = null),
            ),
        )

        val catalog = repository.getById(CatalogId("test-id"))

        assertNotNull(catalog)
        assertEquals(CatalogId("test-id"), catalog.id)
    }

    // getById returns null for missing ID
    @Test
    fun `when entity not found then getById returns null`() = runTest {
        fakeLocal.emit(emptyList())

        val catalog = repository.getById(CatalogId("missing"))

        assertNull(catalog)
    }

    // AC#7: cursor=0 → fetchChangedSince called with 0 (first sync)
    @Test
    fun `when cursor 0 then fetchChangedSince called with 0`() = runTest {
        fakeRemote.result = Result.success(emptyList())

        repository.refreshFromRemote()

        assertEquals(0L, fakeRemote.lastCursor)
    }

    // AC#7: 3 DTOs returned → all 3 upserted to local
    @Test
    fun `when 3 dtos returned then all upserted`() = runTest {
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(id = "c1", name = "C1", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false),
                CatalogDto(id = "c2", name = "C2", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 200L, archived = false),
                CatalogDto(id = "c3", name = "C3", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 300L, archived = false),
            ),
        )

        repository.refreshFromRemote()

        assertEquals(1, fakeLocal.upsertCallsFor["c1"] ?: 0)
        assertEquals(1, fakeLocal.upsertCallsFor["c2"] ?: 0)
        assertEquals(1, fakeLocal.upsertCallsFor["c3"] ?: 0)
    }

    // AC#8: dto.archived=true + local exists + newer version → deleted locally
    @Test
    fun `when dto archived true and version newer then deleted`() = runTest {
        fakeLocal.seed(
            listOf(CatalogEntity(id = "c1", name = "Original", picturePath = null, pictureUrl = null, version = 2L)),
        )
        fakeRemote.result = Result.success(
            listOf(CatalogDto(id = "c1", name = "Updated", picturePath = null, version = 3L, contentsVersion = 0L, lastModifiedAt = 100L, archived = true)),
        )

        repository.refreshFromRemote()

        assertTrue(fakeLocal.deletedIds.contains("c1"))
        assertNull(fakeLocal.findById("c1"))
    }

    // Matrix 1.2: dto.archived=false, absent locally → inserted
    @Test
    fun `when dto archived false absent locally then inserted`() = runTest {
        fakeRemote.result = Result.success(
            listOf(CatalogDto(id = "c-new", name = "New", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false)),
        )

        repository.refreshFromRemote()

        assertTrue((fakeLocal.upsertCallsFor["c-new"] ?: 0) >= 1)
        assertNotNull(fakeLocal.findById("c-new"))
    }

    // Matrix 1.5: dto.version == local.version and timestamp unchanged with equal payload → not overwritten.
    @Test
    fun `when dto version timestamp and payload equal then upsert called but entity not overwritten`() = runTest {
        fakeLocal.seed(
            listOf(CatalogEntity(id = "c1", name = "Original", picturePath = null, pictureUrl = null, version = 5L, lastModifiedAt = 100L)),
        )
        fakeRemote.result = Result.success(
            listOf(CatalogDto(id = "c1", name = "Original", picturePath = null, version = 5L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false)),
        )

        repository.refreshFromRemote()

        // RepositoryImpl calls upsert, but version guard in fake/DAO prevents overwrite
        assertEquals(1, fakeLocal.upsertCallsFor["c1"] ?: 0)
        assertEquals("Original", fakeLocal.findById("c1")?.name)
    }

    @Test
    fun `when dto version equal but timestamp newer then icon names are persisted`() = runTest {
        fakeLocal.seed(
            listOf(CatalogEntity(id = "c1", name = "Original", picturePath = null, pictureUrl = null, version = 5L, lastModifiedAt = 100L)),
        )
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(
                    id = "c1",
                    name = "Updated",
                    picturePath = null,
                    version = 5L,
                    contentsVersion = 0L,
                    lastModifiedAt = 200L,
                    archived = false,
                    iconNames = listOf("School", "Calculate"),
                ),
            ),
        )

        repository.refreshFromRemote()

        val catalog = repository.getById(CatalogId("c1"))
        assertEquals(listOf("School", "Calculate"), catalog?.iconNames)
    }

    @Test
    fun `when local catalog missed icon names then same version remote backfills them`() = runTest {
        fakeLocal.seed(
            listOf(
                CatalogEntity(
                    id = "c1",
                    name = "Original",
                    picturePath = null,
                    pictureUrl = null,
                    version = 5L,
                    lastModifiedAt = 100L,
                    iconNames = emptyList(),
                ),
            ),
        )
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(
                    id = "c1",
                    name = "Original",
                    picturePath = null,
                    version = 5L,
                    contentsVersion = 0L,
                    lastModifiedAt = 100L,
                    archived = false,
                    iconNames = listOf("School", "Calculate"),
                ),
            ),
        )

        repository.refreshFromRemote()

        val catalog = repository.getById(CatalogId("c1"))
        assertEquals(listOf("School", "Calculate"), catalog?.iconNames)
    }

    @Test
    fun `when local catalog missed picture url then same version remote backfills it`() = runTest {
        fakeLocal.seed(
            listOf(
                CatalogEntity(
                    id = "c1",
                    name = "Original",
                    picturePath = "catalog-pictures/c1.jpg",
                    pictureUrl = null,
                    version = 5L,
                    lastModifiedAt = 100L,
                ),
            ),
        )
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(
                    id = "c1",
                    name = "Original",
                    picturePath = "catalog-pictures/c1.jpg",
                    version = 5L,
                    contentsVersion = 0L,
                    lastModifiedAt = 100L,
                    archived = false,
                ),
            ),
        )

        repository.refreshFromRemote()

        val catalog = repository.getById(CatalogId("c1"))
        assertEquals("https://fake.example.com/catalog-pictures/c1.jpg", catalog?.pictureUrl)
    }

    @Test
    fun `when storage url resolution fails then existing picture url is retained`() = runTest {
        fakeLocal.seed(
            listOf(
                CatalogEntity(
                    id = "c1",
                    name = "Original",
                    picturePath = "catalog-pictures/c1.jpg",
                    pictureUrl = "https://cached.example.com/c1.jpg",
                    version = 5L,
                    lastModifiedAt = 100L,
                ),
            ),
        )
        fakeUrlResolver.shouldThrow = true
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(
                    id = "c1",
                    name = "Original",
                    picturePath = "catalog-pictures/c1.jpg",
                    version = 5L,
                    contentsVersion = 0L,
                    lastModifiedAt = 100L,
                    archived = false,
                    iconNames = listOf("School"),
                ),
            ),
        )

        repository.refreshFromRemote()

        val catalog = repository.getById(CatalogId("c1"))
        assertEquals("https://cached.example.com/c1.jpg", catalog?.pictureUrl)
    }

    // P4/AC#41-42: cursor is NOT advanced by repository (B2 fix) — orchestrator responsibility
    @Test
    fun `when success then repository does not advance cursor`() = runTest {
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(id = "x1", name = "X1", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 100L, archived = false),
                CatalogDto(id = "x2", name = "X2", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 300L, archived = false),
                CatalogDto(id = "x3", name = "X3", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 200L, archived = false),
            ),
        )

        val result = repository.refreshFromRemote()

        assertTrue(result.isSuccess)
        assertEquals(0L, fakeSyncState.getCursor("catalogs"))
        assertTrue(fakeSyncState.setCursorCalls.isEmpty())
    }

    // Cursor not advanced when remote returns empty list
    @Test
    fun `when dtos empty then cursor not advanced`() = runTest {
        fakeRemote.result = Result.success(emptyList())

        repository.refreshFromRemote()

        assertEquals(0L, fakeSyncState.getCursor("catalogs"))
        assertTrue(fakeSyncState.setCursorCalls.isEmpty())
    }

    // AC#54: cursor not advanced on remote failure
    @Test
    fun `when remote throws then cursor not advanced`() = runTest {
        fakeRemote.result = Result.failure(RuntimeException("network error"))

        val result = repository.refreshFromRemote()

        assertTrue(result.isFailure)
        assertEquals(0L, fakeSyncState.getCursor("catalogs"))
        assertTrue(fakeSyncState.setCursorCalls.isEmpty())
    }
}
