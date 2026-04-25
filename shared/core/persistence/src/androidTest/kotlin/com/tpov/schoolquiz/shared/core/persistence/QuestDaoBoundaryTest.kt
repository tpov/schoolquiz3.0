package com.tpov.schoolquiz.shared.core.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DAO boundary tests for [QuestDao] — 9 scenarios.
 * Source: docs/features/home-and-my-quests/plan/phase-01/tests.md §2
 * Covers: AC#13 (version guard), AC#14 (archived exclusion), AC#15 (catalog filter)
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class QuestDaoBoundaryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: QuestDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .build()
        dao = db.questDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun questEntity(
        id: String,
        authorUid: String = "uid-a",
        catalogId: String = "cat1",
        title: String = "Test Quest",
        visibleOn: Set<String> = setOf("home"),
        version: Long = 1L,
        archived: Boolean = false,
    ) = QuestEntity(
        id = id,
        catalogId = catalogId,
        authorUid = authorUid,
        title = title,
        picturePath = null,
        pictureUrl = null,
        visibleOn = visibleOn,
        averageRating = null,
        averageRatingCount = 0,
        version = version,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
        archived = archived,
    )

    private suspend fun upsert(entity: QuestEntity) = dao.upsertByIdIfNewerVersion(entity)

    @Test
    fun `observeMyQuests returns only matching authorUid`() = runTest {
        upsert(questEntity(id = "q1", authorUid = "uid-a"))
        upsert(questEntity(id = "q2", authorUid = "uid-b"))

        val result = dao.observeMyQuests("uid-a").first()

        assert(result.size == 1) { "Expected 1 quest for uid-a, got ${result.size}" }
        assert(result[0].authorUid == "uid-a") { "Expected uid-a, got ${result[0].authorUid}" }
    }

    @Test
    fun `observeMyQuests excludes archived`() = runTest {
        upsert(questEntity(id = "q1", authorUid = "uid-a", archived = false))
        upsert(questEntity(id = "q2", authorUid = "uid-a", archived = true))

        val result = dao.observeMyQuests("uid-a").first()

        assert(result.size == 1) { "Expected 1 non-archived quest, got ${result.size}" }
        assert(!result[0].archived) { "Expected archived=false, got true" }
    }

    @Test
    fun `observeMyQuestsInCatalog filters by catalogId`() = runTest {
        upsert(questEntity(id = "q1", authorUid = "uid-a", catalogId = "cat1"))
        upsert(questEntity(id = "q2", authorUid = "uid-a", catalogId = "cat2"))

        val result = dao.observeMyQuestsInCatalog("uid-a", "cat1").first()

        assert(result.size == 1) { "Expected 1 quest in cat1, got ${result.size}" }
        assert(result[0].catalogId == "cat1") { "Expected catalogId=cat1, got ${result[0].catalogId}" }
    }

    @Test
    fun `upsertByIdIfNewerVersion skips on equal version`() = runTest {
        upsert(questEntity(id = "q1", version = 5L, title = "original"))
        upsert(questEntity(id = "q1", version = 5L, title = "updated"))

        val result = dao.findById("q1")

        assert(result?.title == "original") { "Expected title='original', got '${result?.title}'" }
    }

    @Test
    fun `upsertByIdIfNewerVersion skips on older version`() = runTest {
        upsert(questEntity(id = "q1", version = 5L, title = "original"))
        upsert(questEntity(id = "q1", version = 3L, title = "older"))

        val result = dao.findById("q1")

        assert(result?.title == "original") { "Expected title='original', got '${result?.title}'" }
    }

    @Test
    fun `upsertByIdIfNewerVersion updates on higher version`() = runTest {
        upsert(questEntity(id = "q1", version = 3L, title = "old"))
        upsert(questEntity(id = "q1", version = 5L, title = "new"))

        val result = dao.findById("q1")

        assert(result?.title == "new") { "Expected title='new', got '${result?.title}'" }
    }

    @Test
    fun `deleteById removes entity`() = runTest {
        upsert(questEntity(id = "q1"))

        dao.deleteById("q1")

        assert(dao.findById("q1") == null) { "Expected null after deleteById, found entity" }
    }

    @Test
    fun `visibleOn set survives roundtrip`() = runTest {
        val expectedSet = setOf("home", "arena")
        upsert(questEntity(id = "q1", visibleOn = expectedSet))

        val result = dao.findById("q1")?.visibleOn

        assert(result == expectedSet) { "Expected $expectedSet, got $result" }
    }

    @Test
    fun `visibleOn empty set survives roundtrip`() = runTest {
        upsert(questEntity(id = "q1", visibleOn = emptySet()))

        val result = dao.findById("q1")?.visibleOn

        assert(result != null) { "Expected non-null Set, got null" }
        assert(result == emptySet<String>()) { "Expected emptySet, got $result" }
    }
}
