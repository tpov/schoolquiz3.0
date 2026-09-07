package com.tpov.schoolquiz.shared.core.outbox

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArenaOwnerCleanupReasonTest {
    @Test
    fun ordinarySwitchExplicitlySelectsRetentionExceptionWithoutChangingQueueDeletion() =
        runTest {
            val store = FakeOutboxStore()
            val queued =
                OutboxRecord(
                    1,
                    "arena-result",
                    "A",
                    OutboxOperations.SUBMIT_ATTEMPT,
                    "{}",
                    OutboxState.WAITING,
                    0,
                )
            store.seed(queued)
            val reasons = mutableListOf<Pair<String, OwnerForgetReason>>()
            val traces =
                object : OwnerTraces {
                    override val namedLosses: List<OwnerDataKind> = emptyList()

                    override suspend fun forget(uid: String) = error("Switch must declare its cleanup reason")

                    override suspend fun forget(
                        uid: String,
                        reason: OwnerForgetReason,
                    ) {
                        reasons += uid to reason
                    }

                    override suspend fun ownersWithTraces(): List<String> = emptyList()

                    override suspend fun lossesOf(uid: String): List<OwnerDataKind> = emptyList()
                }
            val engine =
                OutboxEngine(
                    store = store,
                    transport = { Result.success(MutationResponse("{}")) },
                    receipts = FakeMutationReceiptStore(),
                    sessions = { OutboxSession("A", 0) },
                    clock = { 0 },
                    onQuarantined = NoLocalEffect(),
                )
            val guard = AccountSwitchGuard(engine, store, store, traces)
            guard.forgetAfterSwitch(listOf("A"))
            assertTrue(store.all.isEmpty())
            store.seed(queued)
            guard.switchAndForget(listOf("A", "B")) { "B" }
            assertTrue(store.all.isEmpty())
            assertEquals(listOf("A" to OwnerForgetReason.SWITCH, "A" to OwnerForgetReason.SWITCH), reasons)
        }
}
