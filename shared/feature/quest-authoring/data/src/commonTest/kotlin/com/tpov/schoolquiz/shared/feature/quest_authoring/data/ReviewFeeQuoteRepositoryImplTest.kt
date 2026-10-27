package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.ReviewFeeQuoteWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewFeeQuoteRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Репозиторий цитаты платы проецирует успех строгим декодером и оборачивает отказ в
 * [SyncFailure]; транспортный отказ проходит насквозь без перепревязки (история 4.11).
 */
class ReviewFeeQuoteRepositoryImplTest {
    private class FakeRemote(
        private val reply: Any?,
        private val error: Exception? = null,
    ) : ReviewFeeQuoteRemoteDataSource {
        var requestedQuestId: String? = null
        var requestedCount: Int? = null

        override suspend fun quoteReviewFee(
            questId: String,
            questionCount: Int,
        ): ReviewFeeQuote {
            requestedQuestId = questId
            requestedCount = questionCount
            error?.let { throw it }
            // Платформа декодирует конверт внутри транспорта и любой его отказ несёт как
            // SyncFailure — фейк повторяет ровно это поведение.
            return try {
                ReviewFeeQuoteWireParser.reviewFeeQuote(reply)
            } catch (error: IllegalStateException) {
                throw SyncFailure(SyncError.Unknown(error), error)
            }
        }
    }

    private val goodReply =
        mapOf(
            "view" to "review-fee-quote-v1",
            "priorCycle" to true,
            "tariff" to "recheck",
            "feeNolics" to 700L,
            "tariffRevision" to 3L,
        )

    @Test
    fun `happy path parses the envelope into the domain quote`() = runTest {
        val remote = FakeRemote(goodReply)
        val result = ReviewFeeQuoteRepositoryImpl(remote).quote(QuestDraftId("quest-1"), 7)
        assertEquals("quest-1", remote.requestedQuestId)
        assertEquals(7, remote.requestedCount)
        assertEquals(
            ReviewFeeQuote(priorCycle = true, tariff = "recheck", feeNolics = 700, tariffRevision = 3),
            result.getOrThrow(),
        )
    }

    @Test
    fun `malformed envelope is a SyncFailure refusal, not a crash`() = runTest {
        val badEnvelopes: List<Any?> =
            listOf(
                null,
                "string",
                mapOf<String, Any?>(),
                goodReply + ("view" to "review-fee-quote-v2"),
                goodReply - "feeNolics",
                goodReply + ("feeNolics" to "700"),
                goodReply + ("tariff" to "mystery"),
                goodReply + ("priorCycle" to 1),
            )
        for (envelope in badEnvelopes) {
            val result = ReviewFeeQuoteRepositoryImpl(FakeRemote(envelope)).quote(QuestDraftId("q"), 1)
            assertTrue(result.isFailure, "expected failure for $envelope")
            val error = result.exceptionOrNull()
            assertTrue(
                error is SyncFailure || error?.syncErrorOrNull() != null,
                "failure must be SyncFailure for $envelope",
            )
        }
    }

    @Test
    fun `transport SyncFailure passes through without rebinding`() = runTest {
        val transport = SyncFailure(SyncError.NoNetwork, IllegalStateException("offline"))
        val result = ReviewFeeQuoteRepositoryImpl(FakeRemote(null, transport)).quote(QuestDraftId("q"), 1)
        assertEquals(transport, result.exceptionOrNull())
    }
}
