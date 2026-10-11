package com.tpov.schoolquiz.platform.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Признак «есть премиум» — дата против часов (история 2.3), не сохранённый буlean.
 *
 * Контракт маппера статистики: живой хвост срока — премиум, граница строгая (срок, кончающийся
 * этим мгновением, — уже не премиум), пропавший или нулевой срок — премиума нет.
 */
class UserStatsPremiumSignTest {
    private val now = 1_800_000_000_000L

    @Test fun aLiveTermIsPremiumAndAnExpiredOneIsNot() {
        assertEquals(true, hasPremiumAt(now + 1) { now })
        // Граница строгая: срок, кончающийся этим мгновением, — уже не премиум.
        assertEquals(false, hasPremiumAt(now) { now })
        assertEquals(false, hasPremiumAt(now - 1) { now })
    }

    @Test fun aMissingTermIsNeverPremium() {
        assertEquals(false, hasPremiumAt(null) { now })
        assertEquals(false, hasPremiumAt(0L) { now })
    }
}
