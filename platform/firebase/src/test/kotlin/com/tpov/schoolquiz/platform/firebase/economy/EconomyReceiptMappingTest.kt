package com.tpov.schoolquiz.platform.firebase.economy

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Квитанцию пишет сервер, и кривой документ — серверный баг: отображение падает громко, а не
 * молча пропускает строку (тот же выбор, что у приватных отчётов во вью inbox).
 */
class EconomyReceiptMappingTest {

    private val seasonReceipt = mapOf(
        "kind" to "SEASON_LEADERBOARD",
        "messageKey" to "receipt.season_leaderboard",
        "params" to mapOf("seasonId" to "2026-Q1", "rank" to 1L, "boxes" to 500L, "trophy" to true),
        "amount" to 500L,
        "currency" to "BOXES",
        "createdAtMs" to 1_800_000_000_000L,
    )

    @Test
    fun `a receipt maps key, string params and amount`() {
        assertEquals(
            EconomyReceipt(
                id = "season_leaderboard_2026-Q1",
                kind = "SEASON_LEADERBOARD",
                messageKey = "receipt.season_leaderboard",
                params = mapOf("seasonId" to "2026-Q1", "rank" to "1", "boxes" to "500", "trophy" to "true"),
                amount = 500,
                currency = "BOXES",
                createdAtMs = 1_800_000_000_000L,
            ),
            receiptFrom("season_leaderboard_2026-Q1", seasonReceipt),
        )
    }

    @Test
    fun `int and long amounts both map`() {
        assertEquals(7, receiptFrom("r", seasonReceipt + mapOf("amount" to 7)).amount)
    }

    @Test
    fun `a malformed field is a loud break, not a skipped row`() {
        assertThrows(RuntimeException::class.java) { receiptFrom("r", seasonReceipt - "messageKey") }
        assertThrows(RuntimeException::class.java) {
            receiptFrom("r", seasonReceipt + mapOf("amount" to "500"))
        }
        assertThrows(RuntimeException::class.java) {
            receiptFrom("r", seasonReceipt + mapOf("params" to mapOf("rank" to listOf(1))))
        }
        assertThrows(RuntimeException::class.java) { receiptFrom("r", null) }
    }
}
