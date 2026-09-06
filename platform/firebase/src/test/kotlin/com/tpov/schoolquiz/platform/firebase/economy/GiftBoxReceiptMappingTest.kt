package com.tpov.schoolquiz.platform.firebase.economy

import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxReceiptStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GiftBoxReceiptMappingTest {
    private val parser = FirebaseGiftBoxRemoteDataSource()

    private fun assertFails(block: () -> Unit) = assertThrows(RuntimeException::class.java) { block() }

    private fun wire(
        reward: String = """{"type":"addNolics","amount":500}""",
        extra: String = "",
        status: String = "OPENED",
    ) = """{"replayed":false,"result":{"schemaVersion":1,"ownerUid":"owner",
            "operationId":"opening-00000001","kind":"OPEN","status":"$status",
            "boxCount":0,"boxStreakDays":2,"nextBoxAtMs":3,"nextBonusBoxAtMs":0,"earnedBoxes":0,
            "openedBoxes":${if (status == "OPENED") 1 else 0},"premiumUntilMs":0
            ${if (reward.isNotEmpty()) ",\"reward\":$reward" else ""}$extra}}
        """

    private fun parse(json: String) = parser.openingFromReceipt(json, "owner", "opening-00000001")

    @Test fun fullEnvelopeAndReplayHaveTheSameTypedReward() {
        assertEquals(GiftBoxReward.Nolics(500), parse(wire()).reward)
        assertEquals(parse(wire()), parse(wire().replace("\"replayed\":false", "\"replayed\":true")))
    }

    @Test fun noBoxesIsAFinalResultWithoutFabricatedReward() {
        val result = parse(wire(reward = "", status = "NO_BOXES"))
        assertEquals(GiftBoxReceiptStatus.NO_BOXES, result.status)
        assertNull(result.reward)
        assertFails { parse(wire(status = "NO_BOXES")) }
    }

    @Test fun identityKindSchemaAndStatusAreExact() {
        for ((before, after) in listOf(
            "\"owner\"" to "\"other\"",
            "opening-00000001" to "opening-00000002",
            "\"OPEN\"" to "\"VISIT\"",
            "\"schemaVersion\":1" to "\"schemaVersion\":2",
            "OPENED" to "CLAIMED",
        )) {
            assertFails { parse(wire().replace(before, after)) }
        }
    }

    @Test fun oldTopLevelRewardAndWrongEnvelopeAreNotAccepted() {
        assertFails { parse("""{"type":"addNolics","amount":500}""") }
        assertFails { parse(wire().replace("\"replayed\":false", "\"replayed\":\"false\"")) }
        assertFails { parse(wire().replace("\"result\"", "\"data\"")) }
    }

    @Test fun rewardNumbersMustBePositiveExactSafeNumbers() {
        for (amount in listOf("-1", "0", "1.5", "\"500\"", "9007199254740992", "null")) {
            assertFails { parse(wire("""{"type":"addNolics","amount":$amount}""")) }
        }
    }

    @Test fun everyShippedRewardKindIsParsedWithoutChangingItsUnits() {
        assertEquals(GiftBoxReward.Premium(86400), parse(wire("""{"type":"datePremium","amount":86400}""")).reward)
        assertEquals(
            GiftBoxReward.Logo("Crown", 1),
            parse(wire("""{"type":"logo","amount":1,"itemName":"Crown"}""")).reward,
        )
        assertEquals(
            GiftBoxReward.Trophy(1),
            parse(wire("""{"type":"trophy","amount":1,"itemName":"champion"}""")).reward,
        )
        assertFails { parse(wire("""{"type":"unknown","amount":1}""")) }
        assertFails { parse(wire("""{"type":"logo","amount":1}""")) }
    }

    @Test fun debtFirstGoldDisplaysOnlySpendableNetCredit() {
        val credit = ""","goldCredit":{"gross":10,"net":3,"shortfallCleared":7,
            "goldBefore":5,"goldAfter":8,"shortfallBefore":7,"shortfallAfter":0}
        """
        assertEquals(GiftBoxReward.Gold(3), parse(wire("""{"type":"addGold","amount":10}""", credit)).reward)
        assertFails { parse(wire("""{"type":"addGold","amount":10}""")) }
        assertFails { parse(wire("""{"type":"addGold","amount":10}""", credit.replace("\"net\":3", "\"net\":4"))) }
    }
}
