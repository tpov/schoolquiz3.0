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
 * Instrumented DAO boundary tests for LessonAttemptDao (in-memory Room v4).
 * Source: docs/features/lesson-runner/plan/phase-02/tests.md §DAO-01..DAO-02
 *
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 *
 * Spec coverage:
 *   DAO-01: upsert + observeByLesson returns inserted entity
 *   DAO-02: observeAllByUser filters by userId
 */
@RunWith(AndroidJUnit4::class)
class LessonAttemptDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LessonAttemptDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        dao = db.lessonAttemptDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // DAO-01: GIVEN in-memory v4 WHEN upsert + observeByLesson THEN returns entity with attemptId="a1"
    @Test
    fun lessonAttemptDao_upsert_thenObserveByLesson() = runTest {
        val entity = LessonAttemptEntity(
            attemptId = "a1",
            userId = "u1",
            lessonId = "l1",
            lessonVersion = 1L,
            isHard = 0,
            codeAnswer = "1",
            percentScore = 50,
            completedAt = 1000L,
        )

        dao.upsert(entity)

        val result = dao.observeByLesson("u1", "l1").take(1).toList()
        assert(result.isNotEmpty()) { "Expected at least one emission from observeByLesson" }
        val items = result.first()
        assert(items.size == 1) { "Expected 1 attempt, got ${items.size}" }
        assert(items.first().attemptId == "a1") {
            "Expected attemptId='a1', got '${items.first().attemptId}'"
        }
    }

    // DAO-02: GIVEN 2 attempts for u1, 1 for u2 WHEN observeAllByUser("u1") THEN 2 results, no u2
    @Test
    fun lessonAttemptDao_observeAllByUser_filtersCorrectly() = runTest {
        dao.upsert(LessonAttemptEntity("att1", "u1", "l1", 1L, 0, "1", 50, 1000L))
        dao.upsert(LessonAttemptEntity("att2", "u1", "l2", 1L, 1, "9", 100, 2000L))
        dao.upsert(LessonAttemptEntity("att3", "u2", "l1", 1L, 0, "5", 60, 3000L))

        val result = dao.observeAllByUser("u1").take(1).toList()
        assert(result.isNotEmpty()) { "Expected at least one emission" }
        val items = result.first()
        assert(items.size == 2) { "Expected 2 attempts for u1, got ${items.size}" }
        assert(items.none { it.userId == "u2" }) { "Expected no entries from u2" }
    }
}
