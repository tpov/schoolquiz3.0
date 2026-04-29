package com.tpov.schoolquiz.shared.core.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DAO boundary tests for LessonRatingLocalDao (in-memory Room v4).
 * Source: docs/features/lesson-runner/plan/phase-02/tests.md §DAO-03..DAO-05
 *
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 *
 * Spec coverage:
 *   DAO-03: upsert + hasSubmitted("u1","l1") returns true
 *   DAO-04: hasSubmitted for different user returns false
 *   DAO-05: second upsert with same PK is idempotent (no exception, still true)
 */
@RunWith(AndroidJUnit4::class)
class LessonRatingLocalDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LessonRatingLocalDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        dao = db.lessonRatingLocalDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // DAO-03: GIVEN entity (u1, l1, 1000L) WHEN upsert + hasSubmitted("u1","l1") THEN true
    @Test
    fun lessonRatingLocalDao_upsert_hasSubmitted() = runTest {
        val entity = LessonRatingSubmittedLocalEntity(userId = "u1", lessonId = "l1", submittedAt = 1000L)

        dao.upsert(entity)

        val result = dao.hasSubmitted("u1", "l1").take(1).first()
        assert(result) { "Expected hasSubmitted='true' after upsert, got false" }
    }

    // DAO-04: GIVEN entity (u1, l1) WHEN hasSubmitted("u2","l1") THEN false
    @Test
    fun lessonRatingLocalDao_hasSubmitted_differentUser() = runTest {
        dao.upsert(LessonRatingSubmittedLocalEntity(userId = "u1", lessonId = "l1", submittedAt = 1000L))

        val result = dao.hasSubmitted("u2", "l1").take(1).first()
        assert(!result) { "Expected hasSubmitted='false' for different user, got true" }
    }

    // DAO-05: GIVEN same entity inserted twice (OnConflictStrategy.REPLACE) WHEN second upsert THEN no exception, still true
    @Test
    fun lessonRatingLocalDao_upsert_idempotent() = runTest {
        val entity = LessonRatingSubmittedLocalEntity(userId = "u1", lessonId = "l1", submittedAt = 1000L)

        dao.upsert(entity)
        dao.upsert(entity.copy(submittedAt = 2000L))

        val result = dao.hasSubmitted("u1", "l1").take(1).first()
        assert(result) { "Expected hasSubmitted='true' after second upsert, got false" }
    }
}
