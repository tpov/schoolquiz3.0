package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Плазма — вместимость и заряд, а не одно число.
 *
 * До сих пор `goldHearts` означал сразу и то и другое, и «плазма восстанавливается за сутки»
 * существовало только на бумаге: восстанавливать было нечего.
 */
class PlasmaChargeTest {

    private fun profile(
        goldHearts: Int,
        plasmaPoints: Int = goldHearts * UserProfile.LIFE_POINTS_PER_HEART,
    ) = UserProfile.offline().copy(goldHearts = goldHearts, plasmaPoints = plasmaPoints)

    @Test
    fun `slots set the ceiling and points set what can be spent`() {
        val half = profile(goldHearts = 3, plasmaPoints = 150)

        assertEquals(300, half.maxPlasmaPoints)
        assertEquals(1, half.plasmaCharges, "полтора заряда — это один; дробную плазму не потратить")
    }

    @Test
    fun `an account with no slots holds nothing, whatever the points say`() {
        assertEquals(0, profile(goldHearts = 0).maxPlasmaPoints)
        assertEquals(0, profile(goldHearts = 0).plasmaCharges)
    }

    @Test
    fun `a profile that never carried the field starts full, not empty`() {
        // Поле появилось позже аккаунта — наказывать за это сутками ожидания не за что.
        assertEquals(3, profile(goldHearts = 3).plasmaCharges)
    }

    @Test
    fun `negative plasma is refused rather than shown as zero`() {
        assertFailsWith<IllegalArgumentException> { profile(goldHearts = 1, plasmaPoints = -1) }
    }

    @Test
    fun `the affordability check now takes the price it is asked about`() {
        // Плоской цены попытки нет: урок, арена, контрольная, экзамен и турнир стоят по-разному.
        val profile = UserProfile.offline().copy(lifePoints = 50)

        assertTrue(profile.canAfford(33))
        assertTrue(!profile.canAfford(100))
    }
}
