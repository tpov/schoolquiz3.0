package com.tpov.schoolquiz.shared.feature.question.data

import com.tpov.schoolquiz.shared.core.persistence.QuestionEntity
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto
import com.tpov.schoolquiz.shared.feature.question.data.fake.FakeQuestionLocalDataSource
import com.tpov.schoolquiz.shared.feature.question.data.fake.FakeQuestionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM tests for QuestionRepositoryImpl.
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §3 (Question — leaf node)
 *
 * Question is a LEAF node — nothing is pulled below it.
 * Returns Result<Unit> (not Result<Set<QuestionId>>).
 */
class QuestionRepositoryImplTest {

    private val fakeLocal = FakeQuestionLocalDataSource()
    private val fakeRemote = FakeQuestionRemoteDataSource()
    private val repository = QuestionRepositoryImpl(local = fakeLocal, remote = fakeRemote)

    private val lessonIds = setOf(LessonId("ls1"))

    private fun makeDto(
        id: String = "q1",
        lessonId: String = "ls1",
        text: String = "Question text",
        payload: String = "{}",
        language: String = "en",
        order: Int = 0,
        version: Long = 1L,
        lastModifiedAt: Long = 100L,
        archived: Boolean = false,
        languageLevel: Int = 1,
    ) = QuestionDto(id, lessonId, text, payload, language, order, version, lastModifiedAt, archived, languageLevel)

    private fun makeEntity(id: String, version: Long = 1L, text: String = "Question text") =
        QuestionEntity(id, "ls1", text, "{}", "en", 0, version, 0L, false)

    // Matrix 2 row: question absent + not archived → inserted
    @Test
    fun `when question absent and not archived then inserted`() = runTest {
        fakeRemote.result = listOf(makeDto("q1"))

        val result = repository.refreshByParents(lessonIds, 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.upsertCallsFor["q1"] ?: 0)
    }

    // Matrix 2 row EDGE: question archived + newer version → deleted
    @Test
    fun `when question archived and newer version then deleted`() = runTest {
        fakeLocal.seed(listOf(makeEntity("q1", version = 1L)))
        fakeRemote.result = listOf(makeDto("q1", version = 2L, archived = true))

        val result = repository.refreshByParents(lessonIds, 0L)

        assertTrue(result.isSuccess)
        assertTrue(fakeLocal.deletedIds.contains("q1"))
        assertNull(fakeLocal.findById("q1"))
    }

    // Matrix 2 row: version equal → upsert called but entity not overwritten
    @Test
    fun `when question version equal then upsert called but entity not overwritten`() = runTest {
        fakeLocal.seed(listOf(makeEntity("q1", version = 5L, text = "Original")))
        fakeRemote.result = listOf(makeDto("q1", version = 5L, text = "Updated"))

        repository.refreshByParents(lessonIds, 0L)

        assertEquals(1, fakeLocal.upsertCallsFor["q1"] ?: 0)
        assertEquals("Original", fakeLocal.findById("q1")?.text)
    }

    // refreshByParents succeeds → Result.success
    @Test
    fun `when refreshByParents succeeds then result is success`() = runTest {
        fakeRemote.result = listOf(makeDto("q1"))

        val result = repository.refreshByParents(lessonIds, 0L)

        assertTrue(result.isSuccess)
    }

    // Remote throws → Result.failure
    @Test
    fun `when refreshByParents fails then failure result`() = runTest {
        fakeRemote.shouldThrow = true

        val result = repository.refreshByParents(lessonIds, 0L)

        assertTrue(result.isFailure)
    }

    // Empty lessonIds → no-op, no remote call, success returned
    @Test
    fun `when lessonIds empty then no remote call and success returned`() = runTest {
        val result = repository.refreshByParents(emptySet(), 0L)

        assertTrue(result.isSuccess)
        assertEquals(0, fakeRemote.callCount)
    }

    // Cursor passed to remote
    @Test
    fun `when cursor 500 then remote receives cursor 500`() = runTest {
        fakeRemote.result = emptyList()

        repository.refreshByParents(lessonIds, 500L)

        assertEquals(500L, fakeRemote.lastCursor)
    }

    // Non-archived question upserted normally
    @Test
    fun `when question not archived then upserted`() = runTest {
        fakeRemote.result = listOf(makeDto("q2", archived = false))

        repository.refreshByParents(setOf(LessonId("ls1")), 0L)

        assertEquals(1, fakeLocal.upsertCallsFor["q2"] ?: 0)
    }

    // lessonIds mapped correctly to remote call
    @Test
    fun `when lessonIds provided then remote receives lesson id strings`() = runTest {
        fakeRemote.result = emptyList()

        repository.refreshByParents(setOf(LessonId("ls1"), LessonId("ls2")), 0L)

        assertTrue(fakeRemote.lastLessonIds.contains("ls1"))
        assertTrue(fakeRemote.lastLessonIds.contains("ls2"))
        assertEquals(2, fakeRemote.lastLessonIds.size)
    }

    // QuestionEntity fields: id, lessonId, text, payload, language, order, version, lastModifiedAt, archived
    @Test
    fun `when multiple questions returned then all processed`() = runTest {
        fakeRemote.result = listOf(
            makeDto("q1"),
            makeDto("q2", lessonId = "ls2"),
            makeDto("q3"),
        )

        repository.refreshByParents(setOf(LessonId("ls1"), LessonId("ls2")), 0L)

        assertEquals(1, fakeLocal.upsertCallsFor["q1"] ?: 0)
        assertEquals(1, fakeLocal.upsertCallsFor["q2"] ?: 0)
        assertEquals(1, fakeLocal.upsertCallsFor["q3"] ?: 0)
    }

    @Test
    fun `when question has languageLevel then persisted and mapped`() = runTest {
        fakeRemote.result = listOf(makeDto("q-level", languageLevel = 12))

        val result = repository.refreshByParents(lessonIds, 0L)

        assertTrue(result.isSuccess)
        assertEquals(12, fakeLocal.findById("q-level")?.languageLevel)
        assertEquals(12, repository.getById(QuestionId("q-level"))?.languageLevel)
    }

    @Test
    fun `refreshByIds preserves imageUrl payload from concrete question`() = runTest {
        val payload = """{"imageUrl":"https://example.com/question.jpg"}"""
        fakeRemote.fetchByIdsResult = listOf(makeDto(id = "q-image", payload = payload, version = 2L))

        val result = repository.refreshByIds(setOf(QuestionId("q-image")))

        assertTrue(result.isSuccess)
        assertEquals(setOf("q-image"), fakeRemote.lastFetchByIds)
        assertEquals(payload, fakeLocal.findById("q-image")?.payload)
    }

    @Test
    fun `refreshByIds deletes local question when requested id is missing remotely`() = runTest {
        fakeLocal.seed(listOf(makeEntity("q-missing", version = 1L)))
        fakeRemote.fetchByIdsResult = emptyList()

        val result = repository.refreshByIds(setOf(QuestionId("q-missing")))

        assertTrue(result.isSuccess)
        assertTrue(fakeLocal.deletedIds.contains("q-missing"))
        assertNull(fakeLocal.findById("q-missing"))
    }

    @Test
    fun `refreshByIds deletes archived concrete question`() = runTest {
        fakeLocal.seed(listOf(makeEntity("q-archived", version = 1L)))
        fakeRemote.fetchByIdsResult = listOf(makeDto(id = "q-archived", version = 2L, archived = true))

        val result = repository.refreshByIds(setOf(QuestionId("q-archived")))

        assertTrue(result.isSuccess)
        assertTrue(fakeLocal.deletedIds.contains("q-archived"))
        assertNull(fakeLocal.findById("q-archived"))
    }
}
