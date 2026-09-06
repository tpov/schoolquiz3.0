package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermanentReceiptPolicyTest {
    @Test fun allPermanentOperationsKeepRetryingWithoutAgeOrAttemptQuarantine() {
        val policy = OutboxPolicy()
        val now = 365L * 86_400_000
        for (operation in OutboxOperations.permanentReceiptOperations) {
            val record =
                OutboxRecord(
                    1, "id", "owner", operation, "{}", OutboxState.WAITING, 1,
                    attemptCount = 100, nextRetryAtMs = now + 1,
                )
            assertFalse(policy.isExpired(record, now))
            assertFalse(policy.isDue(record, now))
            assertTrue(policy.isDue(record, now + 1))
            assertEquals(OutboxState.WAITING, policy.onFailure(record, SyncError.NoNetwork, now).state)
            assertEquals(OutboxState.QUARANTINED, policy.onFailure(record, SyncError.Refused("bad payload"), now).state)
        }
    }
}
