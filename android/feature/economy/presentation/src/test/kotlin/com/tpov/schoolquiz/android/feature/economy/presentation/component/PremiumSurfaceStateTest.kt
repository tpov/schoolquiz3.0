package com.tpov.schoolquiz.android.feature.economy.presentation.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Премиальная поверхность — типы поверх серверных слов.
 *
 * Границы здесь — ровно те, что проверяет сервер: срок и суточный слот. Ни календаря, ни местной
 * полуночи: сутки считаются от слова сервера, и истёкший на миллисекунду срок уже «не активен».
 */
class PremiumSurfaceStateTest {
    private val now = 1_000_000L

    @Test fun emptyWordMeansNoPremiumAndNoCard() {
        val state = PremiumSurfaceState()
        assertFalse(state.activeAt(now))
        assertFalse(state.dailyGiftAvailableAt(now))
    }

    @Test fun activeTermWithFreeSlotReadsAsAvailable() {
        val state = PremiumSurfaceState(activeUntilMs = now + 1, dailyGiftAvailableAtMs = 0L)
        assertTrue(state.activeAt(now))
        assertTrue(state.dailyGiftAvailableAt(now))
    }

    @Test fun occupiedSlotReadsAsClaimedUntilTheServerWordExpires() {
        val state = PremiumSurfaceState(activeUntilMs = now + DAY, dailyGiftAvailableAtMs = now + 1)
        assertTrue(state.activeAt(now))
        assertFalse(state.dailyGiftAvailableAt(now))

        val tomorrow = PremiumSurfaceState(activeUntilMs = now + DAY, dailyGiftAvailableAtMs = now)
        assertTrue(tomorrow.dailyGiftAvailableAt(now))
    }

    @Test fun expiryIsInactiveAtItsExactBoundary() {
        val state = PremiumSurfaceState(activeUntilMs = now, dailyGiftAvailableAtMs = 0L)
        assertFalse(state.activeAt(now))
        // Подарок не доступен без премиума: сервер ответит PREMIUM_REQUIRED, кнопка скрыта.
        assertFalse(state.dailyGiftAvailableAt(now))
    }

    @Test fun fieldsAreExactlyTheServerWords() {
        val state = PremiumSurfaceState(activeUntilMs = 7L, dailyGiftAvailableAtMs = 9L)
        assertEquals(7L, state.activeUntilMs)
        assertEquals(9L, state.dailyGiftAvailableAtMs)
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
