package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for UserStats companion factory.
 *
 * Covers:
 * - Domain Test Scenario 14 (UserStats.guest() all field values — extended with currentSkill + qualification)
 * - Qualification.zero() factory
 * - Qualification init validation
 * - UserStats.currentSkill validation
 */
class UserStatsTest {

    // -----------------------------------------------------------------------
    // Domain Test Scenario 14 — UserStats.guest() field values
    // -----------------------------------------------------------------------

    @Test
    fun `given UserStats guest when reading nickname then returns Гость`() {
        assertEquals("Гость", UserStats.guest().nickname)
    }

    @Test
    fun `given UserStats guest when reading avatarUrl then returns null`() {
        assertNull(UserStats.guest().avatarUrl)
    }

    @Test
    fun `given UserStats guest when reading hasPremium then returns false`() {
        assertFalse(UserStats.guest().hasPremium)
    }

    @Test
    fun `given UserStats guest when reading streakDays then returns 0`() {
        assertEquals(0, UserStats.guest().streakDays)
    }

    @Test
    fun `given UserStats guest when reading stars then returns 0`() {
        assertEquals(0L, UserStats.guest().stars)
    }

    @Test
    fun `given UserStats guest when reading nolics then returns 0`() {
        assertEquals(0L, UserStats.guest().nolics)
    }

    @Test
    fun `given UserStats guest when reading standardHearts then returns 5`() {
        assertEquals(5, UserStats.guest().standardHearts)
    }

    @Test
    fun `given UserStats guest when reading goldHearts then returns 0`() {
        assertEquals(0, UserStats.guest().goldHearts)
    }

    @Test
    fun `given UserStats guest when reading gold then returns 0`() {
        assertEquals(0L, UserStats.guest().gold)
    }

    @Test
    fun `given UserStats guest when reading currentSkill then returns 0`() {
        assertEquals(0, UserStats.guest().currentSkill)
    }

    @Test
    fun `given UserStats guest when reading qualification then returns Qualification zero`() {
        assertEquals(Qualification.zero(), UserStats.guest().qualification)
    }

    // -----------------------------------------------------------------------
    // Qualification.zero() factory
    // -----------------------------------------------------------------------

    @Test
    fun `Qualification zero has all fields zero`() {
        val q = Qualification.zero()
        assertEquals(0, q.tester)
        assertEquals(0, q.moderator)
        assertEquals(0, q.sponsor)
        assertEquals(0, q.translator)
        assertEquals(0, q.admin)
        assertEquals(0, q.developer)
    }

    // -----------------------------------------------------------------------
    // Qualification init validation
    // -----------------------------------------------------------------------

    @Test
    fun `Qualification with negative tester throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            Qualification(tester = -1, moderator = 0, sponsor = 0, translator = 0, admin = 0, developer = 0)
        }
    }

    @Test
    fun `Qualification with negative developer throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            Qualification(tester = 0, moderator = 0, sponsor = 0, translator = 0, admin = 0, developer = -1)
        }
    }

    @Test
    fun `Qualification with all valid values succeeds`() {
        val q = Qualification(tester = 100, moderator = 50, sponsor = 0, translator = 200, admin = 1, developer = 99)
        assertEquals(100, q.tester)
        assertEquals(99, q.developer)
    }

    // -----------------------------------------------------------------------
    // UserStats init validation
    // -----------------------------------------------------------------------

    @Test
    fun `UserStats with negative currentSkill throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            UserStats.guest().copy(currentSkill = -1)
        }
    }
}
