package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.persistence.ArenaFrozenRatingIntentEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultEntityRef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaFrozenRatingReceiptTest {
    private val intent =
        ArenaFrozenRatingIntentEntity(
            ownerUid = "player", ticketId = "ticket", attemptId = "attempt", lessonId = "lesson",
            ratingId = "rating", mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, "rating"),
            operation = OutboxOperations.SUBMIT_RATING, payload = "{\"rating\":2}",
            entityRef = LessonResultEntityRef.rating("rating"), createdAtMs = 10,
        )

    @Test
    fun wrappedAppliedArenaServiceReceipt_isAccepted() {
        assertTrue(
            receipt(
                listOf(
                    "{\"replayed\":false,\"result\":{\"accepted\":1,\"receipts\":[{",
                    "\"schemaVersion\":1,\"mutationId\":\"rating\",\"uid\":\"player\",",
                    "\"operation\":\"RATE_LESSON\",\"status\":\"APPLIED\",\"result\":{",
                    "\"lessonId\":\"lesson\",\"ratingId\":\"arena-rating:server-key\",\"acceptedAtMs\":11}}]}}",
                ).joinToString(""),
            ).isAcceptedArenaRating(intent),
        )
    }

    @Test
    fun replayedWrappedArenaServiceReceipt_isAccepted() {
        assertTrue(
            receipt(
                listOf(
                    "{\"replayed\":true,\"result\":{\"accepted\":1,\"receipts\":[{",
                    "\"schemaVersion\":1,\"mutationId\":\"rating\",\"uid\":\"player\",",
                    "\"operation\":\"RATE_LESSON\",\"status\":\"APPLIED\",\"result\":{",
                    "\"lessonId\":\"lesson\",\"ratingId\":\"arena-rating:server-key\",\"acceptedAtMs\":11}}]}}",
                ).joinToString(""),
            ).isAcceptedArenaRating(intent),
        )
    }

    @Test
    fun queueAckGenericOrLegacyReceipt_isNotAccepted() {
        assertFalse(receipt("{\"accepted\":true}").isAcceptedArenaRating(intent))
        assertFalse(receipt("{\"status\":\"APPLIED\",\"operation\":\"RATE_LESSON\"}").isAcceptedArenaRating(intent))
        assertFalse(
            receipt(
                "{\"replayed\":false,\"result\":{\"accepted\":1,\"receipts\":[" +
                    "{\"status\":\"LEGACY_RECORDED\",\"ratingId\":\"rating\"}]}}",
            ).isAcceptedArenaRating(intent),
        )
    }

    @Test
    fun wrappedReceipt_requiresOneMatchingOwnerAndRating() {
        val valid =
            listOf(
                "{\"schemaVersion\":1,\"mutationId\":\"rating\",\"uid\":\"player\",",
                "\"operation\":\"RATE_LESSON\",\"status\":\"APPLIED\",\"result\":{",
                "\"lessonId\":\"lesson\",\"ratingId\":\"arena-rating:key\",\"acceptedAtMs\":11}}",
            ).joinToString("")
        assertFalse(
            receipt("{\"replayed\":false,\"result\":{\"accepted\":1,\"receipts\":[$valid,$valid]}}")
                .isAcceptedArenaRating(intent),
        )
        assertFalse(
            receipt(
                "{\"replayed\":false,\"result\":{\"accepted\":1,\"receipts\":[" +
                    valid.replace("\"player\"", "\"another\"") + "]}}",
            ).isAcceptedArenaRating(intent),
        )
        assertFalse(
            receipt(
                "{\"replayed\":false,\"result\":{\"accepted\":1,\"receipts\":[" +
                    valid.replace("\"mutationId\":\"rating\"", "\"mutationId\":\"other\"") + "]}}",
            ).isAcceptedArenaRating(intent),
        )
    }

    @Test
    fun wrappedReceipt_requiresAJsonBooleanReplayFlag() {
        val nested = validNestedReceipt()
        listOf(null, "\"false\"", "0", "[]", "null").forEach { replayed ->
            assertFalse(receipt(wrappedReceipt(replayed, nested)).isAcceptedArenaRating(intent))
        }
    }

    @Test
    fun receipt_requiresThePersistedOuterIdentity() {
        val body = wrappedReceipt("false", validNestedReceipt())

        assertFalse(receipt(body, owner = "another").isAcceptedArenaRating(intent))
        assertFalse(receipt(body, mutationId = "another-mutation").isAcceptedArenaRating(intent))
        assertFalse(receipt(body, operation = "another-operation").isAcceptedArenaRating(intent))
        assertFalse(receipt(body, entityRef = "another-entity").isAcceptedArenaRating(intent))
    }

    private fun validNestedReceipt() =
        listOf(
            "{\"schemaVersion\":1,\"mutationId\":\"rating\",\"uid\":\"player\",",
            "\"operation\":\"RATE_LESSON\",\"status\":\"APPLIED\",\"result\":{",
            "\"lessonId\":\"lesson\",\"ratingId\":\"arena-rating:key\",\"acceptedAtMs\":11}}",
        ).joinToString("")

    private fun wrappedReceipt(
        replayed: String?,
        nested: String,
    ): String {
        val replay = replayed?.let { "\"replayed\":$it," }.orEmpty()
        return "{$replay\"result\":{\"accepted\":1,\"receipts\":[$nested]}}"
    }

    private fun receipt(
        body: String,
        owner: String = "player",
        mutationId: String = intent.mutationId,
        operation: String = OutboxOperations.SUBMIT_RATING,
        entityRef: String? = intent.entityRef,
    ) = MutationReceipt(owner, mutationId, operation, entityRef, body, 12)
}
