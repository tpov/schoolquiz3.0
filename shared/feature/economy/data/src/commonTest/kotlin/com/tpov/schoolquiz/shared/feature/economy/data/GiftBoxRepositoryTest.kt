package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxReceipt
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxReceiptStatus
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpeningException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GiftBoxRepositoryTest {
    private val intents = BoxTestIntents()
    private val receipts = BoxTestReceipts()
    private val uid = MutableStateFlow<String?>("A")
    private var session: OutboxSession? = OutboxSession("A", 1)
    private var online = true
    private var ids = 0
    private var sends = 0
    private var refreshes = 0
    private var status = GiftBoxReceiptStatus.OPENED
    private var parseFailure = false
    private var sent: suspend () -> Unit = { storeReceipt() }

    @Test fun terminalDeliveryRefusalNeedsAttentionButDoesNotDiscardMarker() =
        runTest {
            sent = { intents.attention = true }
            val repo = repository()
            val failure = repo.openGiftBox().exceptionOrNull() as GiftBoxOpeningException
            assertEquals(GiftBoxFailureReason.REQUIRES_ATTENTION, failure.reason)
            assertEquals(false, repo.acknowledgeOpening(failure.openingId!!).getOrThrow())
            assertNotNull(intents.active("A"))
        }

    private val profile = UserProfile.offline().copy(uid = "A", boxCount = 17)

    private fun repository() =
        GiftBoxRepositoryImpl(
            remote =
                object : GiftBoxRemoteDataSource {
                    override fun openingFromReceipt(
                        payloadJson: String,
                        ownerUid: String,
                        openingId: String,
                    ): GiftBoxReceipt {
                        check(!parseFailure)
                        return GiftBoxReceipt(
                            openingId,
                            ownerUid,
                            status,
                            if (status == GiftBoxReceiptStatus.OPENED) GiftBoxReward.Nolics(500) else null,
                        )
                    }
                },
            profileRepository =
                object : ProfileRepository {
                    override fun observeCurrentProfile(): Flow<UserProfile> = MutableStateFlow(profile)

                    override suspend fun currentProfile() = profile

                    override suspend fun ensureCurrentProfile(): Result<UserProfile> {
                        refreshes++
                        return Result.success(profile)
                    }

                    override suspend fun updateNickname(nickname: String) = Result.success(profile)
                },
            intents = intents,
            receipts = receipts,
            engine =
                object : GiftBoxDelivery {
                    override suspend fun <T> withoutPasses(block: suspend () -> T) = block()

                    override suspend fun drain(ownerUid: String) {
                        sends++
                        sent()
                    }
                },
            sessions = OutboxSessionProvider { session },
            network =
                object : NetworkMonitor {
                    override fun observeOnline(): Flow<Boolean> = MutableStateFlow(online)

                    override suspend fun isOnline() = online
                },
            scheduler =
                object : SyncScheduler {
                    override fun enqueueManualSync() = Unit

                    override fun enqueueManualProfileSync() = Unit

                    override fun applyFrequency(frequency: SyncFrequency) = Unit

                    override fun applyProfileFrequency(frequency: SyncFrequency) = Unit
                },
            currentUidFlow = { uid }, newOpeningId = { "opening-${++ids}" }, clock = { 1L },
        )

    private suspend fun storeReceipt() {
        val intent = checkNotNull(intents.active("A"))
        receipts.persist(
            MutationReceipt(
                "A",
                intent.mutationId,
                OutboxOperations.OPEN_GIFT_BOX,
                intent.entityRef,
                "full-opaque-wire",
                2,
            ),
        )
    }

    @Test fun offlineNewClickDoesNotCreateAnIntentOrSendAnything() =
        runTest {
            online = false
            val error = repository().openGiftBox().exceptionOrNull() as GiftBoxOpeningException
            assertEquals(GiftBoxFailureReason.OFFLINE, error.reason)
            assertEquals(0, ids)
            assertEquals(0, sends)
            assertNull(intents.active("A"))
        }

    @Test fun uncertainReplyAndRestartAlwaysReuseFirstOpeningId() =
        runTest {
            sent = {}
            val first = repository().openGiftBox()
            val id = (first.exceptionOrNull() as GiftBoxOpeningException).openingId
            sent = { storeReceipt() }
            val second = repository().resumePendingOpening()!!.getOrThrow()
            assertEquals(id, second.openingId)
            assertEquals(1, ids)
            assertEquals(2, sends)
            assertNotNull(intents.active("A"))
        }

    @Test fun durableReceiptResumesWithoutSendingAndFreshProfileCountWins() =
        runTest {
            val repo = repository()
            val first = repo.openGiftBox().getOrThrow()
            val replay = repository().resumePendingOpening()!!.getOrThrow()
            assertEquals(first.openingId, replay.openingId)
            assertEquals(1, sends)
            assertEquals(17, replay.remainingBoxCount)
            assertEquals(true, repo.observePendingOpening().first()!!.receiptAvailable)
        }

    @Test fun onlyExplicitMatchingDismissalReleasesMarkerAndNextClickCreatesNewId() =
        runTest {
            val repo = repository()
            val first = repo.openGiftBox().getOrThrow()
            assertEquals(false, repo.acknowledgeOpening("stale-id").getOrThrow())
            assertEquals(true, repo.acknowledgeOpening(first.openingId!!).getOrThrow())
            assertNull(repo.resumePendingOpening())
            assertEquals("opening-2", repo.openGiftBox().getOrThrow().openingId)
            assertEquals(2, sends)
        }

    @Test fun noBoxesIsFinalAndDismissibleWithoutInventingAReward() =
        runTest {
            status = GiftBoxReceiptStatus.NO_BOXES
            val repo = repository()
            val failure = repo.openGiftBox().exceptionOrNull() as GiftBoxOpeningException
            assertEquals(GiftBoxFailureReason.NO_BOXES, failure.reason)
            assertEquals(0, refreshes)
            assertEquals(true, repo.acknowledgeOpening(failure.openingId!!).getOrThrow())
        }

    @Test fun cancellationPreservesIntentAndPropagates() =
        runTest {
            sent = { throw CancellationException("cancel") }
            assertFailsWith<CancellationException> { repository().openGiftBox() }
            assertNotNull(intents.active("A"))
            assertEquals(1, ids)
        }

    @Test fun accountGenerationChangeDoesNotRefreshOrReturnPreviousOwnerReward() =
        runTest {
            sent = {
                storeReceipt()
                session = OutboxSession("A", 2)
            }
            val error = repository().openGiftBox().exceptionOrNull() as GiftBoxOpeningException
            assertEquals(GiftBoxFailureReason.AUTH_CHANGED, error.reason)
            assertEquals(0, refreshes)
            assertNotNull(intents.active("A"))
        }

    @Test fun invalidReceiptRemainsAttachedAndCannotBeAcknowledgedAsNewOpening() =
        runTest {
            parseFailure = true
            val repo = repository()
            val failure = repo.openGiftBox().exceptionOrNull() as GiftBoxOpeningException
            assertEquals(GiftBoxFailureReason.RECEIPT_INVALID, failure.reason)
            assertEquals(true, repo.acknowledgeOpening(failure.openingId!!).isFailure)
            assertNotNull(intents.active("A"))
            assertEquals(0, refreshes)
        }

    @Test fun uncertainDismissalKeepsSameIntentForLaterOfflineRecovery() =
        runTest {
            sent = {}
            val repo = repository()
            val failure = repo.openGiftBox().exceptionOrNull() as GiftBoxOpeningException
            assertEquals(false, repo.acknowledgeOpening(failure.openingId!!).getOrThrow())
            online = false
            assertEquals(
                failure.openingId,
                (repo.resumePendingOpening()!!.exceptionOrNull() as GiftBoxOpeningException).openingId,
            )
            assertEquals(1, ids)
            assertEquals(1, sends)
        }
}

private class BoxTestIntents : GiftBoxIntentStore {
    var attention = false

    override suspend fun requiresAttention(intent: GiftBoxIntent) = attention

    private val rows = MutableStateFlow<Map<String, GiftBoxIntent>>(emptyMap())

    override suspend fun active(ownerUid: String) = rows.value[ownerUid]

    override fun observeActive(ownerUid: String): Flow<GiftBoxIntent?> = rows.map { it[ownerUid] }

    override suspend fun reserve(
        ownerUid: String,
        openingId: String,
        nowMs: Long,
    ): GiftBoxIntent {
        active(ownerUid)?.let { return it }
        val intent = GiftBoxIntent(1, ownerUid, openingId, "mutation-$openingId", "economy.BOX_OPENING:$openingId")
        rows.value += ownerUid to intent
        return intent
    }

    override suspend fun acknowledge(intent: GiftBoxIntent): Boolean {
        if (active(intent.ownerUid) != intent) return false
        rows.value -= intent.ownerUid
        return true
    }
}

private class BoxTestReceipts : MutationReceiptStore {
    private val rows = MutableStateFlow<List<MutationReceipt>>(emptyList())

    override suspend fun persist(receipt: MutationReceipt) {
        rows.value += receipt
    }

    override suspend fun find(
        ownerUid: String,
        mutationId: String,
    ) = rows.value.firstOrNull {
        it.ownerUid == ownerUid && it.mutationId == mutationId
    }

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ) = rows.map { list ->
        list.filter {
            it.ownerUid == ownerUid && it.entityRef == entityRef
        }
    }
}
