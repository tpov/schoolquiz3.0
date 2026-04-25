package com.tpov.schoolquiz.shared.core.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Domain Test Scenarios QL-01..QL-14 for QualificationLevel.
 *
 * Spec: docs/features/menu-refactor/0-spec-qualification-levels.md § Domain Test Scenarios
 * MOVED from qualification:domain commonTest → core:foundation commonTest (ADR-HLA-01).
 */
class QualificationLevelTest {

    // ── QL-01 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_1 points equals 100`() {
        assertEquals(100, QualificationLevel.LEVEL_1.points)
    }

    // ── QL-02 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_2 points equals 200`() {
        assertEquals(200, QualificationLevel.LEVEL_2.points)
    }

    // ── QL-03 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_3 points equals 300`() {
        assertEquals(300, QualificationLevel.LEVEL_3.points)
    }

    // ── QL-04 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_1 isReachedBy 99 returns false`() {
        assertFalse(QualificationLevel.LEVEL_1.isReachedBy(99))
    }

    // ── QL-05 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_1 isReachedBy 100 returns true`() {
        assertTrue(QualificationLevel.LEVEL_1.isReachedBy(100))
    }

    // ── QL-06 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_1 isReachedBy 500 returns true`() {
        assertTrue(QualificationLevel.LEVEL_1.isReachedBy(500))
    }

    // ── QL-07 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_2 isReachedBy 100 returns false`() {
        assertFalse(QualificationLevel.LEVEL_2.isReachedBy(100))
    }

    // ── QL-08 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_2 isReachedBy 200 returns true`() {
        assertTrue(QualificationLevel.LEVEL_2.isReachedBy(200))
    }

    // ── QL-09 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_3 isReachedBy 200 returns false`() {
        assertFalse(QualificationLevel.LEVEL_3.isReachedBy(200))
    }

    // ── QL-10 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_3 isReachedBy 300 returns true`() {
        assertTrue(QualificationLevel.LEVEL_3.isReachedBy(300))
    }

    // ── QL-11 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_1 isReachedBy minus 1 returns false`() {
        assertFalse(QualificationLevel.LEVEL_1.isReachedBy(-1))
    }

    // ── QL-12 ────────────────────────────────────────────────────────────────
    @Test
    fun `LEVEL_1 isReachedBy 0 returns false`() {
        assertFalse(QualificationLevel.LEVEL_1.isReachedBy(0))
    }

    // ── QL-13 ────────────────────────────────────────────────────────────────
    @Test
    fun `enum has exactly 3 values`() {
        assertEquals(3, QualificationLevel.entries.size)
    }

    // ── QL-14 ────────────────────────────────────────────────────────────────
    @Test
    fun `enum values in declaration order map to points 100 200 300`() {
        val points = QualificationLevel.entries.map { it.points }
        assertEquals(listOf(100, 200, 300), points)
    }
}
