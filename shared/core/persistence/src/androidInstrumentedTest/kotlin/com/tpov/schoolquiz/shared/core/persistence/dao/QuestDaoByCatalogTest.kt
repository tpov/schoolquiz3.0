package com.tpov.schoolquiz.shared.core.persistence.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DAO tests for [QuestDao.observeByCatalog].
 *
 * Source: docs/features/quizzes-screen/plan/phase-01/tests.md §QuestDaoByCatalogTest
 *         docs/features/quizzes-screen/04-testing.md §11.2
 *
 * Covers: DAO-01..04, delimiter exact match, malformed visibleOn edge case.
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class QuestDaoByCatalogTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: QuestDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        dao = db.questDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertCatalog(id: String) {
        db.catalogDao().insertAll(
            listOf(
                CatalogEntity(
                    id = id,
                    name = "Catalog $id",
                    picturePath = null,
                    pictureUrl = null,
                ),
            ),
        )
    }

    private fun questEntity(
        id: String,
        catalogId: String,
        visibleOn: Set<String> = setOf("home"),
        archived: Boolean = false,
        lastModifiedAt: Long = 0L,
    ) = QuestEntity(
        id = id,
        catalogId = catalogId,
        authorUid = "uid-test",
        title = "Quest $id",
        picturePath = null,
        pictureUrl = null,
        visibleOn = visibleOn,
        averageRating = null,
        averageRatingCount = 0,
        version = 1L,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )

    private suspend fun insertHierarchy(
        questId: String,
        withQuestion: Boolean,
    ) {
        val sectionId = "section-$questId"
        val themeId = "theme-$questId"
        val lessonId = "lesson-$questId"
        db.sectionDao().insertOrReplace(
            SectionEntity(
                id = sectionId,
                questId = questId,
                title = "Section $questId",
                order = 0,
                version = 1L,
                lastModifiedAt = 0L,
                archived = false,
            ),
        )
        db.themeDao().insertOrReplace(
            ThemeEntity(
                id = themeId,
                sectionId = sectionId,
                title = "Theme $questId",
                order = 0,
                version = 1L,
                lastModifiedAt = 0L,
                archived = false,
            ),
        )
        db.lessonDao().insertOrReplace(
            LessonEntity(
                id = lessonId,
                themeId = themeId,
                title = "Lesson $questId",
                order = 0,
                version = 1L,
                lastModifiedAt = 0L,
                archived = false,
            ),
        )
        if (withQuestion) {
            db.questionDao().insertOrReplace(
                QuestionEntity(
                    id = "question-$questId",
                    lessonId = lessonId,
                    text = "Question $questId",
                    payload = "{}",
                    language = "ru",
                    order = 0,
                    version = 1L,
                    lastModifiedAt = 0L,
                    archived = false,
                ),
            )
        }
    }

    // DAO-01: quests with matching catalogId returned
    @Test
    fun questsWithMatchingCatalogIdReturned() = runTest {
        insertCatalog("catA")
        insertCatalog("catB")
        dao.insertOrReplace(questEntity(id = "q-a", catalogId = "catA", visibleOn = setOf("home")))
        dao.insertOrReplace(questEntity(id = "q-b", catalogId = "catB", visibleOn = setOf("home")))

        val result = dao.observeByCatalog("catA", "home").take(1).toList()

        val ids = result.first().map { it.id }
        assert(ids == listOf("q-a")) {
            "Expected only [q-a] (catA quests), got: $ids"
        }
    }

    // DAO-02: archived=1 quests remain visible as on-demand roots
    @Test
    fun archivedQuestsIncludedInResult() = runTest {
        insertCatalog("catA")
        dao.insertOrReplace(questEntity(id = "active", catalogId = "catA", archived = false))
        dao.insertOrReplace(questEntity(id = "archived", catalogId = "catA", archived = true))

        val result = dao.observeByCatalog("catA", "home").take(1).toList()

        val ids = result.first().map { it.id }
        assert("archived" in ids) { "Archived quest must remain visible, got: $ids" }
        assert("active" in ids) { "Active quest must appear, got: $ids" }
    }

    // DAO-03: empty table emits empty list, no error
    @Test
    fun emptyTableEmitsEmptyListNoError() = runTest {
        val result = dao.observeByCatalog("nonexistent-cat", "home").first()

        assert(result.isEmpty()) {
            "Expected empty list from empty table, got: $result"
        }
    }

    // DAO-04: insert new quest re-emits via Flow (reactive Room invalidation)
    // UnconfinedTestDispatcher starts the coroutine eagerly — avoids deadlock with StandardTestDispatcher
    // where CountDownLatch.await() blocks the scheduler from dispatching the collect coroutine.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun insertNewQuestReEmitsViaFlow() = runTest {
        insertCatalog("catA")

        val emissions = mutableListOf<List<QuestEntity>>()

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            dao.observeByCatalog("catA", "home").take(2).toList(emissions)
        }

        // Insert matching quest — triggers Room invalidation → second emission
        dao.insertOrReplace(
            questEntity(id = "q1", catalogId = "catA", visibleOn = setOf("home")),
        )

        collectJob.join()

        assert(emissions.size >= 2) {
            "Expected ≥2 emissions (initial empty + after insert), got ${emissions.size}"
        }
        assert(emissions.last().any { it.id == "q1" }) {
            "Last emission must contain inserted quest q1, got: ${emissions.last()}"
        }
    }

    // Delimiter exact match: visibleOn="home" matches shelf="home" → returned
    @Test
    fun delimiterWrappedLikeExactMatchIncludesQuestWithExactShelfName() = runTest {
        insertCatalog("catA")
        dao.insertOrReplace(
            questEntity(id = "exact-match", catalogId = "catA", visibleOn = setOf("home")),
        )

        val result = dao.observeByCatalog("catA", "home").first()

        assert(result.any { it.id == "exact-match" }) {
            "Quest with visibleOn=setOf(\"home\") must be returned for shelf=\"home\""
        }
    }

    // Delimiter exact match: visibleOn="homeExtra" does NOT match shelf="home" — not substring
    @Test
    fun delimiterWrappedLikeExactMatchExcludesQuestWhoseVisibleOnContainsShelfAsSubstring() = runTest {
        insertCatalog("catA")
        dao.insertOrReplace(
            questEntity(id = "substring-only", catalogId = "catA", visibleOn = setOf("homeExtra")),
        )

        val result = dao.observeByCatalog("catA", "home").first()

        assert(result.none { it.id == "substring-only" }) {
            "Quest with visibleOn=setOf(\"homeExtra\") must NOT be returned for shelf=\"home\" " +
                "(CHAR(31) delimiter must prevent substring false positives)"
        }
    }

    // Edge case: empty visibleOn set — does not crash, not returned for any shelf
    @Test
    fun questWithEmptyVisibleOnDoesNotCrashAndIsNotReturned() = runTest {
        insertCatalog("catA")
        dao.insertOrReplace(
            questEntity(id = "no-shelf", catalogId = "catA", visibleOn = emptySet()),
        )

        val result = dao.observeByCatalog("catA", "home").first()

        assert(result.none { it.id == "no-shelf" }) {
            "Quest with empty visibleOn must not match any shelf"
        }
    }

    @Test
    fun downloadedArchivedByCatalogIncludesOnlyArchiveQuestsWithLocalQuestions() = runTest {
        insertCatalog("courses")
        dao.insertOrReplace(
            questEntity(
                id = "downloaded",
                catalogId = "courses",
                visibleOn = setOf("archive"),
                archived = true,
            ),
        )
        dao.insertOrReplace(
            questEntity(
                id = "not-downloaded",
                catalogId = "courses",
                visibleOn = setOf("archive"),
                archived = true,
            ),
        )
        dao.insertOrReplace(
            questEntity(
                id = "home-only",
                catalogId = "courses",
                visibleOn = setOf("home"),
                archived = true,
            ),
        )
        insertHierarchy("downloaded", withQuestion = true)
        insertHierarchy("not-downloaded", withQuestion = false)
        insertHierarchy("home-only", withQuestion = true)

        val result = dao.observeDownloadedArchivedByCatalog("courses", "archive").first()

        val ids = result.map { it.id }
        assert(ids == listOf("downloaded")) {
            "Expected only downloaded archive quest, got: $ids"
        }
    }
}
