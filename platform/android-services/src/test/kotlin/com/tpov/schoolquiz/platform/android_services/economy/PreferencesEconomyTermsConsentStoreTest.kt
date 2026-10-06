package com.tpov.schoolquiz.platform.android_services.economy

import android.content.Context
import android.content.SharedPreferences
import com.tpov.schoolquiz.shared.feature.economy.data.PayableTermsCodec
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Хранилище согласия обязано пережить перезапуск: потерянное согласие — это переспрос игроку, а
 * потерянная половина согласия — платная операция по непонятным условиям.
 *
 * Без Robolectric: песочница Robolectric 4.11.1 не поднимается на JVM 25 этого дерева (падают все
 * Robolectric-тесты модуля, включая пре-существующие). Хранилищу после выноса отображения в
 * [PayableTermsCodec] остаётся делегация в SharedPreferences — она проверяется подменой.
 */
class PreferencesEconomyTermsConsentStoreTest {

    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val context =
        mockk<Context> { every { getSharedPreferences(any(), any()) } returns prefs }

    private lateinit var store: PreferencesEconomyTermsConsentStore

    @Before
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        store = PreferencesEconomyTermsConsentStore(context)
    }

    @Test
    fun `given terms the player accepted then they are read back unchanged`() {
        val accepted =
            PayableTerms.BOOTSTRAP.copy(
                version = 7L,
                standard = PayableTerms.BOOTSTRAP.standard.copy(maxOwned = 12, priceLadder = listOf(500L, 900L)),
                plasma = PayableTerms.BOOTSTRAP.plasma.copy(maxOwned = 4, premiumRegenDivisor = 3),
                activityPrices = PayableTerms.BOOTSTRAP.activityPrices + (ActivityKind.TOURNAMENT to 750),
            )
        every { prefs.getString(any(), null) } returns PayableTermsCodec.toJson(accepted)

        assertEquals(accepted, store.read())
    }

    @Test
    fun `given no consent has ever been captured then nothing is read`() {
        every { prefs.getString(any(), null) } returns null

        assertNull(store.read())
    }

    @Test
    fun `given a corrupted record then it is discarded rather than half-read`() {
        // Половина согласия хуже отсутствия: безопасное направление — спросить игрока заново.
        every { prefs.getString(any(), null) } returns """{"version":"""

        assertNull(store.read())
    }

    @Test
    fun `given the player accepts then the encoded terms are written for the restart`() {
        store.write(PayableTerms.BOOTSTRAP)

        verify(exactly = 1) { editor.putString(any(), PayableTermsCodec.toJson(PayableTerms.BOOTSTRAP)) }
        verify(exactly = 1) { editor.apply() }
    }
}
