package com.tpov.schoolquiz.platform.firebase.network

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MutationResponseJsonTest {
    @Test
    fun onlyInvalidArgumentAndOutOfRangeAreFatalForFinancialAttemptPayloads() {
        for (code in listOf("INVALID_ARGUMENT", "OUT_OF_RANGE")) {
            assertEquals(
                SyncError.Refused("payload error"),
                mutationErrorForCode(OutboxOperations.SUBMIT_ATTEMPT, code, "payload error", null),
            )
        }
        for (code in listOf(
            "UNAUTHENTICATED",
            "PERMISSION_DENIED",
            "NOT_FOUND",
            "UNIMPLEMENTED",
            "ALREADY_EXISTS",
            "FAILED_PRECONDITION",
        )) {
            assertEquals(
                SyncError.Disposition.RETRY,
                mutationErrorForCode(OutboxOperations.SUBMIT_ATTEMPT, code, "INVALID_ARGUMENT", null).disposition,
            )
            assertEquals(
                SyncError.Disposition.QUARANTINE,
                mutationErrorForCode(OutboxOperations.SUBMIT_RATING, code, "same error", null).disposition,
            )
        }
    }

    @Test
    fun pendingPreconditionsAndVersionConflictsKeepTheirDistinctTypedBranches() {
        assertEquals(
            SyncError.PreconditionNotMet,
            mutationErrorForCode(
                OutboxOperations.SUBMIT_ATTEMPT,
                "FAILED_PRECONDITION",
                "anything",
                PRECONDITION_PENDING_DETAIL,
            ),
        )
        assertEquals(
            SyncError.VersionConflict(7),
            mutationErrorForCode(
                OutboxOperations.SUBMIT_ATTEMPT,
                "ABORTED",
                null,
                mapOf("code" to VERSION_CONFLICT_DETAIL, "serverVersion" to 7),
            ),
        )
    }

    @Test
    fun canonicalResultPreservesNestedUnknownFieldsAndCodeAnswerLeadingZeroes() {
        val response =
            mapOf(
                "receipts" to listOf(mapOf("future" to null, "codeAnswer" to "0099", "score" to 100.0)),
                "accepted" to true,
                "large" to 9007199254740993L,
            )
        assertEquals(
            "{\"accepted\":true,\"large\":9007199254740993," +
                "\"receipts\":[{\"codeAnswer\":\"0099\",\"future\":null,\"score\":100}]}",
            mutationResponseJson(response),
        )
    }

    @Test
    fun keyOrderAndEquivalentNumberSdkTypesDoNotChangeReceipt() {
        assertEquals(
            mutationResponseJson(mapOf("b" to 1.0, "a" to "\"\n\\")),
            mutationResponseJson(mapOf("a" to "\"\n\\", "b" to 1L)),
        )
    }

    @Test
    fun malformedOrNonJsonResponseCannotBeAcknowledgedAsAnEmptyReceipt() {
        assertThrows(IllegalArgumentException::class.java) { mutationResponseJson(null) }
        assertThrows(IllegalArgumentException::class.java) { mutationResponseJson(mapOf(1 to "bad")) }
        assertThrows(NumberFormatException::class.java) { mutationResponseJson(mapOf("score" to Double.NaN)) }
    }
}
