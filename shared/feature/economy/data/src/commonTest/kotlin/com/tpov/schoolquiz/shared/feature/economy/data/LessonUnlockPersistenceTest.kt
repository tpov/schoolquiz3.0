package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.feature.economy.data.fake.FakeUserStatsDao
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.mergeWithBalance
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.toBalance
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.toNewUserStatsEntity
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A shop purchase must not cost the player the lessons they have already bought.
 *
 * `applyShopPurchase` answers with a balance, and the client writes that balance straight into the
 * stats row. When the balance travelled without its unlock set, every heart bought shut every
 * lesson bought — invisibly, until the next profile sync put them back.
 */
class LessonUnlockPersistenceTest {

    private val owned = setOf("lesson:l1", "hardMode:l1")

    private fun seededEntity(unlocks: Set<String> = owned) =
        UserStatsEntity(
            uid = UID,
            nickname = "player",
            avatarUrl = null,
            hasPremium = false,
            streakDays = 3,
            stars = 40L,
            nolics = 500L,
            standardHearts = 2,
            goldHearts = 0,
            gold = 10L,
            currentSkill = 7,
            testerLevel = 1,
            moderatorLevel = 0,
            sponsorLevel = 0,
            translatorLevel = 0,
            adminLevel = 0,
            developerLevel = 0,
            lessonUnlocks = unlocks,
        )

    /** The purchase that used to wipe the set: a heart bought, and no unlocks in the answer. */
    private val purchaseWithoutUnlocks =
        EconomyResourceBalance(
            streakDays = 3,
            stars = 40L,
            nolics = 400L,
            standardHearts = 3,
            gold = 10L,
        )

    @Test
    fun purchaseBalanceWithoutUnlocksLeavesTheLocalSetAlone() = runTest {
        val dao = FakeUserStatsDao(seededEntity())
        val local = RoomEconomyLocalDataSource(dao)

        local.upsert(UID, purchaseWithoutUnlocks)

        assertEquals(owned, assertNotNull(dao.current()).lessonUnlocks)
    }

    /** The guard must not swallow the purchase itself — the charge and the heart still land. */
    @Test
    fun purchaseBalanceWithoutUnlocksStillAppliesEveryOtherField() = runTest {
        val dao = FakeUserStatsDao(seededEntity())
        val local = RoomEconomyLocalDataSource(dao)

        local.upsert(UID, purchaseWithoutUnlocks)

        val stored = assertNotNull(dao.current())
        assertEquals(400L, stored.nolics)
        assertEquals(3, stored.standardHearts)
        // Fields outside the balance are the profile's, and a purchase does not touch them.
        assertEquals("player", stored.nickname)
        assertEquals(7, stored.currentSkill)
    }

    @Test
    fun unlockPurchaseCarriesTheKeyItJustGranted() = runTest {
        val dao = FakeUserStatsDao(seededEntity())
        val local = RoomEconomyLocalDataSource(dao)

        val bought = owned + "lesson:l2"
        local.upsert(UID, purchaseWithoutUnlocks.copy(lessonUnlocks = bought))

        assertEquals(bought, assertNotNull(dao.current()).lessonUnlocks)
        assertEquals(bought, local.find(UID)?.lessonUnlocks)
    }

    @Test
    fun firstBalanceForAnUnknownUserKeepsItsUnlocks() = runTest {
        val dao = FakeUserStatsDao()
        val local = RoomEconomyLocalDataSource(dao)

        local.upsert(UID, purchaseWithoutUnlocks.copy(lessonUnlocks = owned))

        assertEquals(owned, assertNotNull(dao.current()).lessonUnlocks)
    }

    /**
     * Field-by-field, per `.claude/rules/testing.md`: an `equals` on the whole row passes happily
     * when a dropped field happens to match its default, which is exactly how the unlock set went
     * missing in the first place.
     */
    @Test
    fun balanceRoundTripsThroughTheEntityFieldByField() {
        val balance =
            EconomyResourceBalance(
                hasPremium = true,
                streakDays = 9,
                stars = 120L,
                nolics = 640L,
                standardHearts = 4,
                goldHearts = 1,
                gold = 33L,
                lessonUnlocks = owned,
            )

        val back = balance.toNewUserStatsEntity(UID).toBalance()

        assertEquals(balance.hasPremium, back.hasPremium)
        assertEquals(balance.streakDays, back.streakDays)
        assertEquals(balance.stars, back.stars)
        assertEquals(balance.nolics, back.nolics)
        assertEquals(balance.standardHearts, back.standardHearts)
        assertEquals(balance.goldHearts, back.goldHearts)
        assertEquals(balance.gold, back.gold)
        assertEquals(balance.lessonUnlocks, back.lessonUnlocks)

        // And the same again through the merge path a purchase actually takes.
        val merged = seededEntity(unlocks = emptySet()).mergeWithBalance(balance).toBalance()
        assertEquals(balance.hasPremium, merged.hasPremium)
        assertEquals(balance.streakDays, merged.streakDays)
        assertEquals(balance.stars, merged.stars)
        assertEquals(balance.nolics, merged.nolics)
        assertEquals(balance.standardHearts, merged.standardHearts)
        assertEquals(balance.goldHearts, merged.goldHearts)
        assertEquals(balance.gold, merged.gold)
        assertEquals(balance.lessonUnlocks, merged.lessonUnlocks)
    }

    private companion object {
        const val UID = "uid-1"
    }
}
