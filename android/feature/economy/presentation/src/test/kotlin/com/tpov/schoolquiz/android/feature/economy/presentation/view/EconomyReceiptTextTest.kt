package com.tpov.schoolquiz.android.feature.economy.presentation.view

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Текст квитанции собирается ключом и параметрами; незнакомый ключ деградирует до честного
 * «начислено», а не падает.
 */
class EconomyReceiptTextTest {

    private fun resolve(resId: Int, args: List<String>): String = "res$#${args.joinToString("|")}"

    private fun receipt(messageKey: String, params: Map<String, String> = emptyMap(), amount: Long = 5) =
        EconomyReceipt(
            id = "r", kind = "KIND", messageKey = messageKey, params = params,
            amount = amount, currency = "BOXES", createdAtMs = 0,
        )

    @Test
    fun `a known key resolves with its params in the translated word order`() {
        assertEquals(
            "res$#7|250",
            receiptText(
                receipt("receipt.season_leaderboard", mapOf("rank" to "7", "boxes" to "250")),
                ::resolve,
            ),
        )
        assertEquals(
            "res$#6|10|50",
            receiptText(
                receipt("receipt.referral_bonus", mapOf("boxes" to "50", "invitees" to "6", "each" to "10")),
                ::resolve,
            ),
        )
    }

    @Test
    fun `a missing param resolves as an empty word, not a crash`() {
        assertEquals("res$#|", receiptText(receipt("receipt.season_leaderboard"), ::resolve))
    }

    @Test
    fun `tournament keys resolve by prefix`() {
        assertEquals(
            "res$#5",
            receiptText(receipt("receipt.tournament_prize"), ::resolve),
        )
    }

    @Test
    fun `an unknown key degrades to the generic credited line`() {
        assertEquals(
            "res$#5|BOXES",
            receiptText(receipt("receipt.something_new"), ::resolve),
        )
    }
}
