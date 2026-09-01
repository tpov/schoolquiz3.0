package com.tpov.schoolquiz.shared.feature.lesson.domain

import com.tpov.schoolquiz.shared.feature.lesson.domain.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.use_case.SyncLessonsUseCase
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Domain tests for Lesson value objects, invariants, and SyncLessonsUseCase.
 *
 * Covers:
 *   scenario 5 (LessonId invariants)
 *   scenario 18 (Lesson.order < 0 throws)
 *   State Matrix rows for lesson sync
 *   scenario 43 (archived lesson → local delete)
 */
class LessonDomainTest {

    // ── LessonId invariants ───────────────────────────────────────────────────
    @Test
    fun `LessonId blank throws`() {
        assertFailsWith<IllegalArgumentException> { LessonId("") }
    }

    @Test
    fun `LessonId valid constructs`() {
        assertEquals("l1", LessonId("l1").value)
    }

    // ── Lesson invariants (scenario 18) ───────────────────────────────────────
    @Test
    fun `Lesson with order -1 throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            makeLesson(order = -1)
        }
        assertEquals(true, error.message?.contains("order"))
    }

    @Test
    fun `Lesson with blank title throws`() {
        assertFailsWith<IllegalArgumentException> { makeLesson(title = "") }
    }

    @Test
    fun `Lesson with version 0 throws`() {
        assertFailsWith<IllegalArgumentException> { makeLesson(version = 0L) }
    }

    @Test
    fun `Lesson with negative lastModifiedAt throws`() {
        assertFailsWith<IllegalArgumentException> { makeLesson(lastModifiedAt = -1L) }
    }

    @Test
    fun `Lesson order 0 constructs`() {
        val l = makeLesson(order = 0)
        assertEquals(0, l.order)
    }

    @Test
    fun `Lesson archived false by default`() {
        val l = makeLesson()
        assertEquals(false, l.archived)
    }

    @Test
    fun `Lesson archived true constructs`() {
        val l = makeLesson(archived = true)
        assertEquals(true, l.archived)
    }

    // ── SyncLessonsUseCase ────────────────────────────────────────────────────
    @Test
    fun `SyncLessonsUseCase inserts new lesson for known themeId`() = runTest {
        val fake = FakeLessonRepository()
        val l1 = makeLesson(id = "l1", themeId = "t1", lastModifiedAt = 1000L)
        fake.simulateRemoteLessons(listOf(l1))

        val result = SyncLessonsUseCase(fake).invoke(setOf(ThemeId("t1")), cursor = 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, fake.snapshot().size)
    }

    @Test
    fun `SyncLessonsUseCase ignores lessons for unknown themeIds`() = runTest {
        val fake = FakeLessonRepository()
        val l1 = makeLesson(id = "l1", themeId = "t99", lastModifiedAt = 1000L)
        fake.simulateRemoteLessons(listOf(l1))

        SyncLessonsUseCase(fake).invoke(setOf(ThemeId("t1")), cursor = 0L)

        assertTrue(fake.snapshot().isEmpty())
    }

    @Test
    fun `SyncLessonsUseCase upserts when incoming version is higher`() = runTest {
        val existing = makeLesson(id = "l1", version = 1L, title = "Old", lastModifiedAt = 500L)
        val fake = FakeLessonRepository(initial = listOf(existing))
        fake.simulateRemoteLessons(listOf(existing.copy(title = "New", version = 2L, lastModifiedAt = 1000L)))

        SyncLessonsUseCase(fake).invoke(setOf(ThemeId("t1")), cursor = 0L)

        assertEquals("New", fake.snapshot().first().title)
    }

    @Test
    fun `SyncLessonsUseCase skips when incoming version is same`() = runTest {
        val existing = makeLesson(id = "l1", version = 2L, title = "Original", lastModifiedAt = 500L)
        val fake = FakeLessonRepository(initial = listOf(existing))
        fake.simulateRemoteLessons(listOf(existing.copy(title = "Server", version = 2L, lastModifiedAt = 1000L)))

        SyncLessonsUseCase(fake).invoke(setOf(ThemeId("t1")), cursor = 0L)

        assertEquals("Original", fake.snapshot().first().title)
    }

    @Test
    fun `SyncLessonsUseCase returns failure on network error`() = runTest {
        val fake = FakeLessonRepository()
        fake.setNextRefreshFailure(RuntimeException("fail"))

        val result = SyncLessonsUseCase(fake).invoke(setOf(ThemeId("t1")), cursor = 0L)

        assertTrue(result.isFailure)
    }

    @Test
    fun `observeByTheme returns lessons sorted by order`() = runTest {
        val l2 = makeLesson(id = "l2", themeId = "t1", order = 2)
        val l0 = makeLesson(id = "l0", themeId = "t1", order = 0)
        val l1 = makeLesson(id = "l1", themeId = "t1", order = 1)
        val fake = FakeLessonRepository(initial = listOf(l2, l0, l1))

        val result = fake.observeByTheme(ThemeId("t1")).first()

        assertEquals(listOf(0, 1, 2), result.map { it.order })
    }

    // ── Scenario 43 : archived lesson → local delete ─────────────────────────
    @Test
    fun `scenario 43 FakeLessonRepository with existing lesson server returns archived lesson with higher lastModifiedAt THEN local deleted`() = runTest {
        val existing = makeLesson(id = "l1", themeId = "t1", version = 1L, title = "Active Lesson", lastModifiedAt = 500L)
        val fake = FakeLessonRepository(initial = listOf(existing))

        val archived = existing.copy(version = 2L, archived = true, lastModifiedAt = 2000L)
        fake.simulateRemoteLessons(listOf(archived))

        val result = SyncLessonsUseCase(fake).invoke(setOf(ThemeId("t1")), cursor = 0L)

        assertTrue(result.isSuccess)
        assertTrue(fake.snapshot().isEmpty(), "Archived lesson should be deleted locally; got: ${fake.snapshot()}")
    }

    // ── Scenario 43b : archived lesson at cursor boundary → skip ─────────────
    @Test
    fun `scenario 43b archived lesson with lastModifiedAt at cursor is NOT processed`() = runTest {
        val existing = makeLesson(id = "l1", themeId = "t1", version = 1L, lastModifiedAt = 500L)
        val fake = FakeLessonRepository(initial = listOf(existing))

        val archived = existing.copy(version = 2L, archived = true, lastModifiedAt = 1000L)
        fake.simulateRemoteLessons(listOf(archived))

        // Cursor equals lastModifiedAt → cursor guard skips this item
        SyncLessonsUseCase(fake).invoke(setOf(ThemeId("t1")), cursor = 1000L)

        // Local record should remain unchanged (cursor guard prevented processing)
        assertEquals(1, fake.snapshot().size, "Item at cursor boundary skipped, local should remain")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeLesson(
        id: String = "l1",
        themeId: String = "t1",
        title: String = "Lesson Title",
        order: Int = 0,
        version: Long = 1L,
        lastModifiedAt: Long = 0L,
        archived: Boolean = false,
    ) = Lesson(
        id = LessonId(id),
        themeId = ThemeId(themeId),
        title = title,
        order = order,
        version = version,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
