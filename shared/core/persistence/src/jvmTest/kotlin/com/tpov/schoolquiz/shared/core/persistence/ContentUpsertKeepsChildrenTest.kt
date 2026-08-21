package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Regression guard for downloaded content being wiped by an ordinary metadata update.
 *
 * The content chain is catalogs → quests → sections → themes → lessons → questions, and every
 * link declares ON DELETE CASCADE. SQLite implements `INSERT OR REPLACE` as delete + insert, so
 * re-inserting a parent used to delete its whole subtree. The sync list only re-fetches the nodes
 * the server marked as changed, so the children never came back: a user who downloaded a course
 * lost its contents as soon as the author renamed the quest.
 *
 * These DAOs use @Upsert instead, which updates the row in place and never fires the cascade.
 */
class ContentUpsertKeepsChildrenTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            // Both converters are @ProvidedTypeConverter, so they must be handed over explicitly
            // (the production builder in PersistenceModule does the same).
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun catalog(id: String) = CatalogEntity(
        id = id,
        name = "Catalog $id",
        picturePath = null,
        pictureUrl = null,
    )

    private fun quest(
        id: String,
        catalogId: String,
        title: String = "Quest $id",
        version: Long = 1L,
        lastModifiedAt: Long = 0L,
    ) = QuestEntity(
        id = id,
        catalogId = catalogId,
        authorUid = "uid-test",
        title = title,
        picturePath = null,
        pictureUrl = null,
        visibleOn = setOf("archive"),
        averageRating = null,
        averageRatingCount = 0,
        version = version,
        contentsVersion = 0L,
        lastModifiedAt = lastModifiedAt,
        archived = false,
    )

    /** Builds catalog → quest → section → theme → lesson → question, as a downloaded course looks. */
    private suspend fun seedDownloadedCourse() {
        db.catalogDao().insertAll(listOf(catalog("courses")))
        db.questDao().insertOrReplace(quest(id = "q1", catalogId = "courses"))
        db.sectionDao().insertOrReplace(
            SectionEntity(
                id = "s1",
                questId = "q1",
                title = "Section",
                order = 0,
                version = 1L,
                contentsVersion = 0L,
                lastModifiedAt = 0L,
                archived = false,
            ),
        )
        db.themeDao().insertOrReplace(
            ThemeEntity(
                id = "t1",
                sectionId = "s1",
                title = "Theme",
                order = 0,
                version = 1L,
                contentsVersion = 0L,
                lastModifiedAt = 0L,
                archived = false,
            ),
        )
        db.lessonDao().insertOrReplace(
            LessonEntity(
                id = "l1",
                themeId = "t1",
                title = "Lesson",
                order = 0,
                version = 1L,
                contentsVersion = 0L,
                lastModifiedAt = 0L,
                archived = false,
            ),
        )
        db.questionDao().insertOrReplace(
            QuestionEntity(
                id = "qn1",
                lessonId = "l1",
                text = "Question",
                payload = "{}",
                language = "ru",
                order = 0,
                version = 1L,
                lastModifiedAt = 0L,
                archived = false,
            ),
        )
    }

    private suspend fun assertCourseIntact(message: String) {
        assertNotNull("$message: section", db.sectionDao().findById("s1"))
        assertNotNull("$message: theme", db.themeDao().findById("t1"))
        assertNotNull("$message: lesson", db.lessonDao().findById("l1"))
        assertNotNull("$message: question", db.questionDao().findById("qn1"))
    }

    @Test
    fun questMetadataUpdateKeepsDownloadedSubtree() = runTest {
        seedDownloadedCourse()

        // The author renamed the quest: same id, newer version — exactly what the sync list sends.
        db.questDao().insertOrReplace(
            quest(id = "q1", catalogId = "courses", title = "Renamed", version = 2L, lastModifiedAt = 100L),
        )

        assertEquals("Renamed", db.questDao().findById("q1")?.title)
        assertCourseIntact("after quest update")
    }

    @Test
    fun catalogUpdateKeepsEverythingBelowIt() = runTest {
        seedDownloadedCourse()

        db.catalogDao().insertOrReplace(catalog("courses").copy(name = "Renamed catalog"))

        assertNotNull("quest survives catalog update", db.questDao().findById("q1"))
        assertCourseIntact("after catalog update")
    }

    @Test
    fun sectionThemeAndLessonUpdatesKeepTheirOwnChildren() = runTest {
        seedDownloadedCourse()

        db.sectionDao().insertOrReplace(
            SectionEntity("s1", "q1", "Renamed section", 0, 2L, 0L, 100L, false),
        )
        db.themeDao().insertOrReplace(
            ThemeEntity("t1", "s1", "Renamed theme", 0, 2L, 0L, 100L, false),
        )
        db.lessonDao().insertOrReplace(
            LessonEntity("l1", "t1", "Renamed lesson", 0, 2L, 0L, 100L, false),
        )

        assertCourseIntact("after section/theme/lesson updates")
    }

    @Test
    fun deletingQuestStillCascades() = runTest {
        seedDownloadedCourse()

        // The cascade itself must stay intact — it is how real removals clean up.
        db.questDao().deleteById("q1")

        assertEquals(null, db.sectionDao().findById("s1"))
        assertEquals(null, db.themeDao().findById("t1"))
        assertEquals(null, db.lessonDao().findById("l1"))
        assertEquals(null, db.questionDao().findById("qn1"))
    }
}
