package com.tpov.schoolquiz.platform.firebase.network

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import org.junit.Assert.assertTrue
import org.junit.Test

class PermanentMutationErrorsTest {
    @Test fun everyPermanentOperationKeepsRecoverableSdkErrorsRetryable() {
        for (operation in OutboxOperations.permanentReceiptOperations) {
            for (code in listOf("UNAUTHENTICATED", "PERMISSION_DENIED", "NOT_FOUND", "UNIMPLEMENTED", "UNAVAILABLE")) {
                val error = mutationErrorForCode(operation, code, "INVALID_ARGUMENT in text is not a code", null)
                assertTrue("$operation / $code", error.disposition == SyncError.Disposition.RETRY)
            }
            for (code in listOf("INVALID_ARGUMENT", "OUT_OF_RANGE")) {
                assertTrue(mutationErrorForCode(operation, code, "temporarily unavailable", null) is SyncError.Refused)
            }
        }
    }
}
