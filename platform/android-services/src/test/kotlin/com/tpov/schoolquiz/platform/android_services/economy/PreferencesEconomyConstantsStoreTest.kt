package com.tpov.schoolquiz.platform.android_services.economy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Таблица настроек обязана пережить перезапуск целиком.
 *
 * Проверяется по полям, а не через `equals`: у `EconomyConstants` есть значения по умолчанию, и
 * сравнение целиком вернуло бы true даже если бы запись потеряла поле, чьё умолчание совпало с
 * записанным. Именно так теряются поля в маппере.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesEconomyConstantsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = PreferencesEconomyConstantsStore(context)

    @Test
    fun `given a table that arrived then every field survives a restart`() {
        val arrived =
            EconomyConstants.BOOTSTRAP.copy(
                version = 7L,
                standard = EconomyConstants.BOOTSTRAP.standard.copy(
                    maxOwned = 12,
                    regenMs = 1_800_000L,
                    priceLadder = listOf(500L, 900L),
                ),
                plasma = EconomyConstants.BOOTSTRAP.plasma.copy(maxOwned = 4, premiumRegenDivisor = 3),
                activityPrices = EconomyConstants.BOOTSTRAP_PRICES + (ActivityKind.TOURNAMENT to 750),
                clockSkewToleranceMs = 30_000L,
                auditEnabled = false,
            )

        store.write(arrived)
        val read = requireNotNull(PreferencesEconomyConstantsStore(context).read())

        assertEquals(7L, read.version)
        assertEquals(12, read.standard.maxOwned)
        assertEquals(1_800_000L, read.standard.regenMs)
        assertEquals(listOf(500L, 900L), read.standard.priceLadder)
        assertEquals(arrived.standard.currency, read.standard.currency)
        assertEquals(4, read.plasma.maxOwned)
        assertEquals(true, read.plasma.requiresSettledAccount)
        assertEquals(3, read.plasma.premiumRegenDivisor)
        assertEquals(1, read.standard.premiumRegenDivisor)
        assertEquals(750, read.priceOf(ActivityKind.TOURNAMENT))
        assertEquals(33, read.priceOf(ActivityKind.ORDINARY_LESSON))
        assertEquals(30_000L, read.clockSkewToleranceMs)
        assertEquals(false, read.auditEnabled)
    }

    @Test
    fun `given a corrupted record then it is discarded rather than half-read`() {
        // Половина записи с потолком ноль заперла бы аккаунт. Начальные значения честнее.
        store.write(EconomyConstants.BOOTSTRAP)
        context.getSharedPreferences("schoolquiz_economy", Context.MODE_PRIVATE)
            .edit()
            .putString("economy_constants", """{"version":""")
            .commit()

        assertNull(store.read())
    }
}
