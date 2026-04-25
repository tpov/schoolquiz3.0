package com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode

import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Tests for [TapProgress] construction invariants.
 *
 * Spec: docs/features/menu-refactor/0-spec-dev-mode.md § Terms / TapProgress
 */
class TapProgressTest {

    @Test
    fun `initial factory returns count=0 and null lastTapAtMillis`() {
        val p = TapProgress.initial
        assertEquals(0, p.count)
        assertNull(p.lastTapAtMillis)
    }

    @Test
    fun `count=0 with null lastTapAtMillis is valid`() {
        val p = TapProgress(count = 0, lastTapAtMillis = null)
        assertEquals(0, p.count)
        assertNull(p.lastTapAtMillis)
    }

    @Test
    fun `count=1 with non-null lastTapAtMillis is valid`() {
        val p = TapProgress(count = 1, lastTapAtMillis = 1000L)
        assertEquals(1, p.count)
        assertEquals(1000L, p.lastTapAtMillis)
    }

    @Test
    fun `count=9 with non-null lastTapAtMillis is valid boundary`() {
        val p = TapProgress(count = 9, lastTapAtMillis = 9999L)
        assertEquals(9, p.count)
    }

    @Test
    fun `count=10 violates invariant`() {
        assertFailsWith<IllegalArgumentException> {
            TapProgress(count = 10, lastTapAtMillis = 1000L)
        }
    }

    @Test
    fun `negative count violates invariant`() {
        assertFailsWith<IllegalArgumentException> {
            TapProgress(count = -1, lastTapAtMillis = null)
        }
    }

    @Test
    fun `count greater than 0 with null lastTapAtMillis violates invariant`() {
        assertFailsWith<IllegalArgumentException> {
            TapProgress(count = 1, lastTapAtMillis = null)
        }
    }
}
