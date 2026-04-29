package com.tpov.schoolquiz.shared.core.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DAO boundary tests for [SectionDao] — 4 scenarios.
 * Source: docs/features/home-and-my-quests/plan/phase-01/tests.md §3
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SectionDaoBoundaryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SectionDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        dao = db.sectionDao()
        runBlocking {
            db.catalogDao().insertAll(
                listOf(CatalogEntity(id = "cat1", name = "Cat", picturePath = null, pictureUrl = null))
            )
            db.questDao().upsertByIdIfNewerVersion(
                QuestEntity(
                    id = "q1",
                    catalogId = "cat1",
                    authorUid = "uid-a",
                    title = "Quest",
                    picturePath = null,
                    pictureUrl = null,
                    visibleOn = emptySet(),
                    averageRating = null,
                    averageRatingCount = 0,
                    version = 1L,
                    contentsVersion = 0L,
                    lastModifiedAt = 0L,
                    archived = false,
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sectionEntity(
        id: String,
        questId: String = "q1",
        title: String = "Test Section",
        order: Int = 0,
        version: Long = 1L,
        archived: Boolean = false,
    ) = SectionEntity(
        id = id,
        questId = questId,
        title = title,
        order = order,
        version = version,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
        archived = archived,
    )

    private suspend fun upsert(entity: SectionEntity) = dao.upsertByIdIfNewerVersion(entity)

    @Test
    fun observeByQuestExcludesArchived() = runTest {
        upsert(sectionEntity(id = "s1", questId = "q1", archived = false))
        upsert(sectionEntity(id = "s2", questId = "q1", archived = true))

        val result = dao.observeByQuest("q1").first()

        assert(result.size == 1) { "Expected 1 non-archived section, got ${result.size}" }
        assert(!result[0].archived) { "Expected archived=false, got true" }
    }

    @Test
    fun observeByQuestOrdersByOrderAsc() = runTest {
        upsert(sectionEntity(id = "s1", questId = "q1", order = 2))
        upsert(sectionEntity(id = "s2", questId = "q1", order = 0))
        upsert(sectionEntity(id = "s3", questId = "q1", order = 1))

        val result = dao.observeByQuest("q1").first()

        assert(result.size == 3) { "Expected 3 sections, got ${result.size}" }
        assert(result[0].order == 0) { "Expected order=0 at [0], got ${result[0].order}" }
        assert(result[1].order == 1) { "Expected order=1 at [1], got ${result[1].order}" }
        assert(result[2].order == 2) { "Expected order=2 at [2], got ${result[2].order}" }
    }

    @Test
    fun upsertByIdIfNewerVersionSkipsOnEqualVersion() = runTest {
        upsert(sectionEntity(id = "s1", version = 5L, title = "original"))
        upsert(sectionEntity(id = "s1", version = 5L, title = "updated"))

        val result = dao.findById("s1")

        assert(result?.title == "original") { "Expected title='original', got '${result?.title}'" }
    }

    @Test
    fun upsertByIdIfNewerVersionUpdatesOnHigherVersion() = runTest {
        upsert(sectionEntity(id = "s1", version = 3L, title = "old"))
        upsert(sectionEntity(id = "s1", version = 5L, title = "new"))

        val result = dao.findById("s1")

        assert(result?.title == "new") { "Expected title='new', got '${result?.title}'" }
    }
}
