package com.tpov.schoolquiz.shared.feature.question.domain

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.use_case.SyncQuestionsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Domain tests for Question value objects, invariants, and SyncQuestionsUseCase.
 *
 * Covers:
 *   scenario 5 (QuestionId invariants)
 *   scenario 18 (Question.order < 0 throws)
 *   scenario 19 (language="" throws)
 *   scenario 20 (payload="" throws)
 *   State Matrix rows for question sync (leaf node)
 *   scenario 44 (FakeQuestionRepository: updated + new + cursor advance)
 *   Primary User Journey 5: admin adds question → question reaches the client
 */
class QuestionDomainTest {

    // ── QuestionId invariants ─────────────────────────────────────────────────
    @Test
    fun `scenario 5 QuestionId blank throws`() {
        assertFailsWith<IllegalArgumentException> { QuestionId("") }
    }

    @Test
    fun `QuestionId valid constructs`() {
        assertEquals("qn1", QuestionId("qn1").value)
    }

    // ── Question invariants ────────────────────────────────────────────────────

    @Test
    fun `scenario 18 Question order -1 throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            makeQuestion(order = -1)
        }
        assertEquals(true, error.message?.contains("order"))
    }

    // ── Scenario 19 : language="" throws ─────────────────────────────────────
    @Test
    fun `scenario 19 Question with empty language throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            makeQuestion(language = "")
        }
        assertEquals(true, error.message?.contains("language"))
    }

    @Test
    fun `Question with negative languageLevel throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            makeQuestion(languageLevel = -1)
        }
        assertEquals(true, error.message?.contains("languageLevel"))
    }

    @Test
    fun `Question accepts high languageLevel`() {
        val q = makeQuestion(languageLevel = 250)
        assertEquals(250, q.languageLevel)
    }

    // ── Scenario 20 : payload="" throws ──────────────────────────────────────
    @Test
    fun `scenario 20 Question with empty payload throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            makeQuestion(payload = "")
        }
        assertEquals(true, error.message?.contains("payload"))
    }

    @Test
    fun `Question with blank text throws`() {
        assertFailsWith<IllegalArgumentException> { makeQuestion(text = "") }
    }

    @Test
    fun `Question with version 0 throws`() {
        assertFailsWith<IllegalArgumentException> { makeQuestion(version = 0L) }
    }

    @Test
    fun `Question with negative lastModifiedAt throws`() {
        assertFailsWith<IllegalArgumentException> { makeQuestion(lastModifiedAt = -1L) }
    }

    @Test
    fun `Question with valid fields constructs`() {
        val q = makeQuestion()
        assertEquals("qn1", q.id.value)
        assertEquals("ru", q.language)
        assertEquals(1, q.languageLevel)
        assertEquals(0L, q.lastModifiedAt)
    }

    @Test
    fun `Question archived false by default`() {
        val q = makeQuestion()
        assertEquals(false, q.archived)
    }

    @Test
    fun `Question archived true constructs`() {
        val q = makeQuestion(archived = true)
        assertEquals(true, q.archived)
    }

    // ── SyncQuestionsUseCase tests (leaf pull — PUJ 5) ───────────────────────
    @Test
    fun `SyncQuestionsUseCase inserts new question for known lessonId`() = runTest {
        val fake = FakeQuestionRepository()
        val q1 = makeQuestion(id = "qn1", lessonId = "l1", lastModifiedAt = 1000L)
        fake.simulateRemoteQuestions(listOf(q1))

        val result = SyncQuestionsUseCase(fake).invoke(setOf(LessonId("l1")), cursor = 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, fake.snapshot().size)
    }

    @Test
    fun `SyncQuestionsUseCase ignores questions for unknown lessonIds`() = runTest {
        val fake = FakeQuestionRepository()
        val q1 = makeQuestion(id = "qn1", lessonId = "l99", lastModifiedAt = 1000L)
        fake.simulateRemoteQuestions(listOf(q1))

        SyncQuestionsUseCase(fake).invoke(setOf(LessonId("l1")), cursor = 0L)

        assertTrue(fake.snapshot().isEmpty())
    }

    @Test
    fun `SyncQuestionsUseCase upserts question when incoming version is higher`() = runTest {
        val existing = makeQuestion(id = "qn1", version = 1L, text = "Old?", lastModifiedAt = 500L)
        val fake = FakeQuestionRepository(initial = listOf(existing))
        fake.simulateRemoteQuestions(listOf(existing.copy(text = "New?", version = 2L, lastModifiedAt = 1000L)))

        SyncQuestionsUseCase(fake).invoke(setOf(LessonId("l1")), cursor = 0L)

        assertEquals("New?", fake.snapshot().first().text)
    }

    @Test
    fun `SyncQuestionsUseCase skips question when incoming version is same`() = runTest {
        val existing = makeQuestion(id = "qn1", version = 2L, text = "Original?", lastModifiedAt = 500L)
        val fake = FakeQuestionRepository(initial = listOf(existing))
        fake.simulateRemoteQuestions(listOf(existing.copy(text = "Server?", version = 2L, lastModifiedAt = 1000L)))

        SyncQuestionsUseCase(fake).invoke(setOf(LessonId("l1")), cursor = 0L)

        assertEquals("Original?", fake.snapshot().first().text)
    }

    @Test
    fun `SyncQuestionsUseCase returns failure on network error`() = runTest {
        val fake = FakeQuestionRepository()
        fake.setNextRefreshFailure(RuntimeException("network fail"))

        val result = SyncQuestionsUseCase(fake).invoke(setOf(LessonId("l1")), cursor = 0L)

        assertTrue(result.isFailure)
    }

    @Test
    fun `observeByLesson returns questions sorted by order`() = runTest {
        val q2 = makeQuestion(id = "qn2", lessonId = "l1", order = 2)
        val q0 = makeQuestion(id = "qn0", lessonId = "l1", order = 0)
        val q1 = makeQuestion(id = "qn1", lessonId = "l1", order = 1)
        val fake = FakeQuestionRepository(initial = listOf(q2, q0, q1))

        val result = fake.observeByLesson(LessonId("l1")).first()

        assertEquals(listOf(0, 1, 2), result.map { it.order })
    }

    // ── PUJ 5: admin adds question → question in Room ────────────────────────
    @Test
    fun `journey 5 admin adds question to lesson - appears in local cache after sync`() = runTest {
        val fake = FakeQuestionRepository()
        val newQuestion = makeQuestion(id = "new-qn", lessonId = "l1", version = 1L, lastModifiedAt = 1000L)
        fake.simulateRemoteQuestions(listOf(newQuestion))

        val result = SyncQuestionsUseCase(fake).invoke(setOf(LessonId("l1")), cursor = 0L)

        assertTrue(result.isSuccess)
        val byLesson = fake.observeByLesson(LessonId("l1")).first()
        assertEquals(1, byLesson.size)
        assertEquals(QuestionId("new-qn"), byLesson[0].id)
    }

    // ── Scenario 44 : FakeQuestionRepository 3 questions, 1 updated + 1 new, cursor advances ──
    @Test
    fun `scenario 44 FakeQuestionRepository with 3 questions server returns 1 updated and 1 new THEN existing updated new inserted cursor advances`() = runTest {
        val q1 = makeQuestion(id = "qn1", lessonId = "l1", version = 1L, text = "Q1 original", lastModifiedAt = 500L)
        val q2 = makeQuestion(id = "qn2", lessonId = "l1", version = 1L, text = "Q2 original", lastModifiedAt = 600L)
        val q3 = makeQuestion(id = "qn3", lessonId = "l1", version = 1L, text = "Q3 original", lastModifiedAt = 700L)
        val fake = FakeQuestionRepository(initial = listOf(q1, q2, q3))

        // Server returns: q1 updated (higher lastModifiedAt and version), qn4 new
        val q1Updated = q1.copy(text = "Q1 updated", version = 2L, lastModifiedAt = 2000L)
        val qn4New = makeQuestion(id = "qn4", lessonId = "l1", version = 1L, text = "Q4 new", lastModifiedAt = 2100L)
        fake.simulateRemoteQuestions(listOf(q1Updated, qn4New))

        val result = SyncQuestionsUseCase(fake).invoke(setOf(LessonId("l1")), cursor = 1000L)

        assertTrue(result.isSuccess)
        val snapshot = fake.snapshot()
        assertEquals(4, snapshot.size, "Should have q1 (updated), q2, q3, q4 (new)")
        assertEquals("Q1 updated", snapshot.first { it.id == QuestionId("qn1") }.text)
        assertTrue(snapshot.any { it.id == QuestionId("qn4") })
        assertEquals(2100L, fake.lastCursor, "Cursor should advance to max lastModifiedAt seen")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeQuestion(
        id: String = "qn1",
        lessonId: String = "l1",
        text: String = "What is 2+2?",
        payload: String = """{"type":"SingleChoice","options":["3","4","5"],"correctIndex":1}""",
        language: String = "ru",
        languageLevel: Int = 1,
        order: Int = 0,
        version: Long = 1L,
        lastModifiedAt: Long = 0L,
        archived: Boolean = false,
    ) = Question(
        id = QuestionId(id),
        lessonId = LessonId(lessonId),
        text = text,
        payload = payload,
        language = language,
        languageLevel = languageLevel,
        order = order,
        version = version,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
