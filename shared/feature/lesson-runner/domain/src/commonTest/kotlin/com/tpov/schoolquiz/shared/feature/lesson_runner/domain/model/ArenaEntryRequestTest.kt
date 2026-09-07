@file:Suppress("MagicNumber") // Exact request boundary cases.

package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArenaEntryRequestTest {
    @Test
    fun explicitConsentAndOriginalIdentityAreRetained() {
        val request = ArenaPrepareRequest.Selected("owner", "persisted-once", Difficulty.HARD, "lesson", 37)
        assertEquals("owner", request.ownerUid)
        assertEquals("persisted-once", request.requestId)
        assertEquals(Difficulty.HARD, request.difficulty)
        assertEquals("lesson", request.lessonId)
        assertEquals(37, request.maxPriceNolics)
        assertEquals(request, request.copy())
    }

    @Test
    fun invalidIdsAndUnboundedCeilingsFailBeforeTransport() {
        for (id in listOf("", " ", ".", "..", "a/b", "x".repeat(1_501))) {
            assertFailsWith<IllegalArgumentException> { ArenaPrepareRequest.Random(id, "r", Difficulty.EASY) }
            assertFailsWith<IllegalArgumentException> { ArenaPrepareRequest.Random("u", id, Difficulty.EASY) }
            assertFailsWith<IllegalArgumentException> {
                ArenaPrepareRequest.Selected(
                    "u",
                    "r",
                    Difficulty.EASY,
                    id,
                    10,
                )
            }
        }
        for (price in listOf(-1, 101, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> {
                ArenaPrepareRequest.Selected(
                    "u",
                    "r",
                    Difficulty.EASY,
                    "l",
                    price,
                )
            }
        }
    }

    @Test
    fun recoveryHasAnExclusiveHexCursorAndBoundedPage() {
        val id = "a".repeat(64)
        assertEquals(id, ArenaTicketListRequest("u", id, 20).afterTicketId)
        assertEquals(20, ArenaTicketListRequest("u").pageSize)
        assertEquals(id, ArenaTicketGetRequest("u", id).ticketId)
        for (size in listOf(0, 21)) {
            assertFailsWith<IllegalArgumentException> { ArenaTicketListRequest("u", pageSize = size) }
        }
        for (bad in listOf("A".repeat(64), "a".repeat(63), "a/b")) {
            assertFailsWith<IllegalArgumentException> { ArenaTicketGetRequest("u", bad) }
            assertFailsWith<IllegalArgumentException> { ArenaTicketListRequest("u", bad) }
        }
    }
}
