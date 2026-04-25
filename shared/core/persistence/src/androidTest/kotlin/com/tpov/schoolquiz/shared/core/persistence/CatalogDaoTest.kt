package com.tpov.schoolquiz.shared.core.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DAO boundary tests for [CatalogDao] — scenarios CF-06..CF-10 + phase-01 additions.
 *
 * Source: docs/features/menu-refactor/04-testing.md §3.4.2
 *         docs/features/home-and-my-quests/plan/phase-01/tests.md §4
 *
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class CatalogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CatalogDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .build()
        dao = db.catalogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String,
        name: String = "Name $id",
        archived: Boolean = false,
    ) = CatalogEntity(
        id = id,
        name = name,
        picturePath = null,
        pictureUrl = null,
        archived = archived,
    )

    private suspend fun upsert(
        id: String,
        name: String,
        version: Long = 1L,
        archived: Boolean = false,
    ) = dao.upsertByIdIfNewerVersion(
        CatalogEntity(
            id = id,
            name = name,
            picturePath = null,
            pictureUrl = null,
            version = version,
            contentsVersion = 0L,
            lastModifiedAt = 0L,
            archived = archived,
        ),
    )

    // CF-06: insertAll + observeAll returns all inserted entities
    @Test
    fun cf06_insertAll_and_observeAll_returns_all_entities() = runTest {
        dao.insertAll(listOf(entity("a"), entity("b")))

        val result = dao.observeAll().take(1).toList()

        assert(result.first().size == 2) {
            "Expected 2 entities, got: ${result.first().size}"
        }
    }

    // CF-07: replaceAll is atomic — final state correct after replace
    @Test
    fun cf07_replaceAll_atomically_replaces_all_entities() = runTest {
        dao.insertAll(listOf(entity("existing")))

        dao.replaceAll(listOf(entity("new")))

        val result = dao.observeAll().take(1).toList()
        val ids = result.first().map { it.id }
        assert(ids == listOf("new")) {
            "Expected only ['new'] after replaceAll, got: $ids"
        }
    }

    // CF-08: replaceAll removes old entities and inserts new ones
    @Test
    fun cf08_replaceAll_removes_old_entities() = runTest {
        dao.insertAll(listOf(entity("old1"), entity("old2")))

        dao.replaceAll(listOf(entity("new1")))

        val old = dao.findById("old1")
        val new = dao.findById("new1")
        assert(old == null) {
            "Expected old1 to be deleted after replaceAll, but found: $old"
        }
        assert(new != null) {
            "Expected new1 to exist after replaceAll, but was null"
        }
    }

    // CF-09: observeAll returns entities sorted by id ASC (Room ORDER BY clause)
    @Test
    fun cf09_observeAll_returns_entities_sorted_by_id_asc() = runTest {
        dao.insertAll(listOf(entity("surveys"), entity("courses"), entity("games"), entity("school")))

        val result = dao.observeAll().take(1).toList()

        val ids = result.first().map { it.id }
        assert(ids == listOf("courses", "games", "school", "surveys")) {
            "Expected alphabetical ASC order, got: $ids"
        }
    }

    // CF-10: findById returns null when table is empty / entity does not exist
    @Test
    fun cf10_findById_returns_null_for_nonexistent_id() = runTest {
        val result = dao.findById("nonexistent")

        assert(result == null) {
            "Expected null for nonexistent id, got: $result"
        }
    }

    // Phase-01 AC#13: upsertByIdIfNewerVersion skips when stored version >= incoming version
    @Test
    fun upsertByIdIfNewerVersion_skips_on_equal_version() = runTest {
        upsert(id = "c1", name = "Original", version = 3L)
        upsert(id = "c1", name = "Updated", version = 3L)

        val result = dao.findById("c1")

        assert(result?.name == "Original") {
            "Expected name='Original' (version guard), got '${result?.name}'"
        }
    }

    // Phase-01: observeAll excludes archived catalogs
    @Test
    fun observeAll_excludes_archived() = runTest {
        dao.insertAll(listOf(entity(id = "c1", archived = false), entity(id = "c2", archived = true)))

        val result = dao.observeAll().take(1).toList()

        val ids = result.first().map { it.id }
        assert(ids == listOf("c1")) {
            "Expected only ['c1'] (archived excluded), got: $ids"
        }
    }
}
