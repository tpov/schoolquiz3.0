package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pure JVM unit tests for drill-item mapper extension functions.
 *
 * Spec: docs/features/quizzes-screen/plan/phase-04/tests.md (DrillItemMapperTest section)
 * Phase: 04 (TDD)
 *
 * Verifies: Section.toDrillItem(), Theme.toDrillItem(), Lesson.toDrillItem()
 * Coverage: MAP-01..06
 */
class DrillItemMapperTest {

    // ── Section fixtures ──────────────────────────────────────────────────────

    private fun sectionFixture(
        id: String = "s-1",
        title: String = "Section A",
        order: Int = 1,
    ) = Section(
        id = SectionId(id),
        questId = QuestId("q-1"),
        title = title,
        order = order,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
    )

    // ── Theme fixtures ────────────────────────────────────────────────────────

    private fun themeFixture(
        id: String = "t-1",
        title: String = "Theme B",
        order: Int = 1,
    ) = Theme(
        id = ThemeId(id),
        sectionId = SectionId("s-1"),
        title = title,
        order = order,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
    )

    // ── Lesson fixtures ───────────────────────────────────────────────────────

    private fun lessonFixture(
        id: String = "l-1",
        title: String = "Lesson C",
        order: Int = 1,
    ) = Lesson(
        id = LessonId(id),
        themeId = ThemeId("t-1"),
        title = title,
        order = order,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
    )

    // ── MAP-01: Section id ────────────────────────────────────────────────────

    /**
     * Spec: MAP-01 — Section.toDrillItem().id == section.id.value
     */
    @Test
    fun `Section toDrillItem maps id correctly`() {
        val section = sectionFixture(id = "s-1")
        assertEquals("s-1", section.toDrillItem().id)
    }

    // ── MAP-02: Section title ─────────────────────────────────────────────────

    /**
     * Spec: MAP-02 — Section.toDrillItem().title == section.title
     */
    @Test
    fun `Section toDrillItem maps title correctly`() {
        val section = sectionFixture(title = "Section A")
        assertEquals("Section A", section.toDrillItem().title)
    }

    // ── MAP-03: Section subtitleCount ─────────────────────────────────────────

    /**
     * Spec: MAP-03 — Section.toDrillItem().subtitleCount == null (no child count in MVP mapper)
     */
    @Test
    fun `Section toDrillItem subtitleCount is null`() {
        val section = sectionFixture()
        assertNull(section.toDrillItem().subtitleCount)
    }

    // ── MAP-04: Theme id and title ────────────────────────────────────────────

    /**
     * Spec: MAP-04 — Theme.toDrillItem() maps id and title correctly
     */
    @Test
    fun `Theme toDrillItem maps id correctly`() {
        val theme = themeFixture(id = "t-1")
        assertEquals("t-1", theme.toDrillItem().id)
    }

    @Test
    fun `Theme toDrillItem maps title correctly`() {
        val theme = themeFixture(title = "Theme B")
        assertEquals("Theme B", theme.toDrillItem().title)
    }

    @Test
    fun `Theme toDrillItem subtitleCount is null`() {
        val theme = themeFixture()
        assertNull(theme.toDrillItem().subtitleCount)
    }

    // ── MAP-05: Lesson id and title ───────────────────────────────────────────

    /**
     * Spec: MAP-05 — Lesson.toDrillItem() maps id and title correctly
     */
    @Test
    fun `Lesson toDrillItem maps id correctly`() {
        val lesson = lessonFixture(id = "l-1")
        assertEquals("l-1", lesson.toDrillItem().id)
    }

    @Test
    fun `Lesson toDrillItem maps title correctly`() {
        val lesson = lessonFixture(title = "Lesson C")
        assertEquals("Lesson C", lesson.toDrillItem().title)
    }

    // ── MAP-06: orderLabel from order field ───────────────────────────────────

    /**
     * Spec: MAP-06 — orderLabel = "${order + 1}." (1-based display, domain is 0-based).
     * User decision: domain stores 0-indexed order; UI shows 1-indexed labels.
     * Section(order=3) → UI displays as position 4 → orderLabel == "4."
     */
    @Test
    fun `Section toDrillItem orderLabel is 1-based from order`() {
        val section = sectionFixture(order = 3)
        assertEquals("4.", section.toDrillItem().orderLabel)
    }

    @Test
    fun `Theme toDrillItem orderLabel is 1-based from order`() {
        val theme = themeFixture(order = 2)
        assertEquals("3.", theme.toDrillItem().orderLabel)
    }

    @Test
    fun `Lesson toDrillItem orderLabel is 1-based from order`() {
        val lesson = lessonFixture(order = 1)
        assertEquals("2.", lesson.toDrillItem().orderLabel)
    }

    @Test
    fun `Section toDrillItem order 0 produces orderLabel 1 dot`() {
        val section = sectionFixture(order = 0)
        assertEquals("1.", section.toDrillItem().orderLabel)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    /**
     * Edge: domain model with empty-ish id — mapper passes through without throwing.
     * Domain invariant requires non-blank id, so "x" is the minimal non-blank value.
     */
    @Test
    fun `Section toDrillItem with minimal id does not throw`() {
        val section = sectionFixture(id = "x")
        assertNotNull(section.toDrillItem())
    }

    /**
     * Edge: long title — mapper passes through without truncation.
     */
    @Test
    fun `Section toDrillItem with long title passes through unchanged`() {
        val longTitle = "A".repeat(500)
        val section = sectionFixture(title = longTitle)
        assertEquals(longTitle, section.toDrillItem().title)
    }
}
