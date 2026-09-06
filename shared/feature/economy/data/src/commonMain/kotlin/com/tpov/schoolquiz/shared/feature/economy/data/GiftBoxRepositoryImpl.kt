package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxReceiptStatus
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpeningException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PendingGiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class GiftBoxRepositoryImpl(
    private val remote: GiftBoxRemoteDataSource,
    private val profileRepository: ProfileRepository,
    private val intents: GiftBoxIntentStore,
    private val receipts: MutationReceiptStore,
    private val engine: GiftBoxDelivery,
    private val sessions: OutboxSessionProvider,
    private val network: NetworkMonitor,
    private val scheduler: SyncScheduler,
    private val currentUidFlow: () -> Flow<String?>,
    private val newOpeningId: () -> String,
    private val clock: () -> Long,
) : GiftBoxRepository {
    private val opening = Mutex()

    override fun observePendingOpening(): Flow<PendingGiftBoxOpening?> =
        currentUidFlow().distinctUntilChanged().flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(null)
            } else {
                intents.observeActive(uid).flatMapLatest { intent ->
                    if (intent == null) {
                        flowOf(null)
                    } else {
                        receipts.observeEntity(uid, intent.entityRef).map { saved ->
                            PendingGiftBoxOpening(
                                intent.openingId,
                                uid,
                                saved.any { it.mutationId == intent.mutationId },
                            )
                        }
                    }
                }
            }
        }.distinctUntilChanged()

    override suspend fun openGiftBox(): Result<GiftBoxOpening> =
        capture {
            opening.withLock {
                val session = currentSession()
                val intent =
                    engine.withoutPasses {
                        requireSession(session)
                        intents.active(session.ownerUid) ?: run {
                            if (!network.isOnline()) fail(GiftBoxFailureReason.OFFLINE)
                            requireSession(session)
                            intents.reserve(session.ownerUid, newOpeningId(), clock())
                        }
                    }
                resolve(intent, session)
            }
        }

    override suspend fun resumePendingOpening(): Result<GiftBoxOpening>? {
        val result =
            capture {
                opening.withLock {
                    val session = sessions.current() ?: return@withLock null
                    val intent = intents.active(session.ownerUid) ?: return@withLock null
                    resolve(intent, session)
                }
            }
        return if (result.isSuccess && result.getOrNull() == null) null else result.map { checkNotNull(it) }
    }

    override suspend fun acknowledgeOpening(openingId: String): Result<Boolean> =
        capture {
            opening.withLock {
                val session = currentSession()
                engine.withoutPasses {
                    requireSession(session)
                    val intent = intents.active(session.ownerUid) ?: return@withoutPasses false
                    if (intent.openingId != openingId) return@withoutPasses false
                    val receipt = receipts.find(intent.ownerUid, intent.mutationId) ?: return@withoutPasses false
                    readReceipt(intent, receipt)
                    requireSession(session)
                    intents.acknowledge(intent)
                }
            }
        }

    private suspend fun resolve(
        intent: GiftBoxIntent,
        session: OutboxSession,
    ): GiftBoxOpening {
        requireSession(session, intent)
        if (intents.active(intent.ownerUid) != intent) fail(GiftBoxFailureReason.PENDING, intent)
        var receipt = receipts.find(intent.ownerUid, intent.mutationId)
        if (receipt == null) {
            scheduler.enqueueManualSync()
            if (network.isOnline()) withTimeoutOrNull(FOREGROUND_WAIT_MS) { engine.drain(intent.ownerUid) }
            requireSession(session, intent)
            receipt = receipts.find(intent.ownerUid, intent.mutationId)
        }
        val saved =
            receipt ?: run {
                val reason =
                    if (intents.requiresAttention(intent)) {
                        GiftBoxFailureReason.REQUIRES_ATTENTION
                    } else {
                        GiftBoxFailureReason.PENDING
                    }
                fail(reason, intent)
            }
        val result = readReceipt(intent, saved)
        requireSession(session, intent)
        if (result.status == GiftBoxReceiptStatus.NO_BOXES) fail(GiftBoxFailureReason.NO_BOXES, intent)
        // Historical receipt balances never overwrite a current profile. Account switch shares
        // this gate; only a fresh profile fetch may update the profile repository.
        val profile =
            engine.withoutPasses {
                requireSession(session, intent)
                profileRepository.ensureCurrentProfile().getOrNull()?.takeIf { it.uid == intent.ownerUid }
            }
        requireSession(session, intent)
        return GiftBoxOpening(
            checkNotNull(result.reward),
            profile != null,
            profile?.boxCount,
            intent.openingId,
            intent.ownerUid,
        )
    }

    private fun readReceipt(
        intent: GiftBoxIntent,
        receipt: MutationReceipt,
    ) = try {
        check(receipt.ownerUid == intent.ownerUid && receipt.mutationId == intent.mutationId)
        check(receipt.operation == OutboxOperations.OPEN_GIFT_BOX && receipt.entityRef == intent.entityRef)
        remote.openingFromReceipt(receipt.payloadJson, intent.ownerUid, intent.openingId)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        throw GiftBoxOpeningException(GiftBoxFailureReason.RECEIPT_INVALID, intent.openingId, intent.ownerUid, error)
    }

    private fun currentSession() = sessions.current() ?: fail(GiftBoxFailureReason.AUTH_CHANGED)

    private fun requireSession(
        session: OutboxSession,
        intent: GiftBoxIntent? = null,
    ) {
        if (sessions.current() != session || (intent != null && session.ownerUid != intent.ownerUid)) {
            fail(GiftBoxFailureReason.AUTH_CHANGED, intent)
        }
    }

    private fun fail(
        reason: GiftBoxFailureReason,
        intent: GiftBoxIntent? = null,
    ): Nothing = throw GiftBoxOpeningException(reason, intent?.openingId, intent?.ownerUid)

    private companion object {
        const val FOREGROUND_WAIT_MS = 40_000L
    }
}

private suspend fun <T> capture(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
