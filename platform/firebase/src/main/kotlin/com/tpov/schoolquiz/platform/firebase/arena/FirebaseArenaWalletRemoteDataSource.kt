package com.tpov.schoolquiz.platform.firebase.arena

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaWalletRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

internal fun interface ArenaWalletExchange {
    fun start(ownerUid: String): ArenaPendingWalletRead
}

internal fun interface ArenaPendingWalletRead {
    suspend fun await(): ArenaWalletDocument
}

/** Reads users/{original UID}, never profile upsert/callables. SDK initiation only is under the shared gate. */
class FirebaseArenaWalletRemoteDataSource internal constructor(
    private val exchange: ArenaWalletExchange,
    private val sessions: OutboxSessionProvider,
    private val network: NetworkMonitor,
    private val gate: ArenaOwnerGate,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : ArenaWalletRemoteDataSource {
    constructor(
        firestore: FirebaseFirestore,
        sessions: OutboxSessionProvider,
        network: NetworkMonitor,
        gate: ArenaOwnerGate,
        dispatcher: CoroutineDispatcher,
        clock: Clock,
    ) : this(
        exchange = firebaseWalletExchange(firestore),
        sessions = sessions,
        network = network,
        gate = gate,
        dispatcher = dispatcher,
        clock = clock,
    )

    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(expected: OutboxSession): Result<ArenaWalletObservation> {
        coroutineContext.ensureActive()
        return try {
            requireSession(expected)
            requireOwnerId(expected.ownerUid)
            val observation =
                withContext(dispatcher) {
                    requireSession(expected)
                    val online = network.isOnline()
                    requireSession(expected)
                    if (!online) throw ArenaWalletFailure(ArenaWalletFailureKind.NO_NETWORK)
                    val pending =
                        gate.withOwner(expected) {
                            coroutineContext.ensureActive()
                            requireSession(expected)
                            exchange.start(expected.ownerUid)
                        }
                    val document = pending.await() // Never keep the mutex locked during network IO.
                    coroutineContext.ensureActive()
                    requireSession(expected)
                    parseArenaWallet(
                        document,
                        ArenaSessionStamp(expected.ownerUid, expected.generation),
                        clock.now().toEpochMilliseconds(),
                    ).also { requireSession(expected) }
                }
            coroutineContext.ensureActive()
            requireSession(expected)
            Result.success(observation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            coroutineContext.ensureActive()
            val current = sessions.current()
            val kind =
                when {
                    current == null -> ArenaWalletFailureKind.ACCOUNT_UNAVAILABLE
                    current != expected -> ArenaWalletFailureKind.ACCOUNT_CHANGED
                    else -> walletFailureKind(error)
                }
            Result.failure(ArenaWalletFailure(kind))
        }
    }

    private fun requireSession(expected: OutboxSession) {
        val current = sessions.current()
        if (current == null) throw ArenaWalletFailure(ArenaWalletFailureKind.ACCOUNT_UNAVAILABLE)
        if (current != expected) throw ArenaWalletFailure(ArenaWalletFailureKind.ACCOUNT_CHANGED)
    }

    private fun requireOwnerId(owner: String) {
        val validPath = owner.isNotBlank() && owner != "." && owner != ".." && '/' !in owner
        if (!validPath || owner.encodeToByteArray().size > MAX_ID_BYTES) {
            throw ArenaWalletFailure(ArenaWalletFailureKind.INVALID_REQUEST)
        }
    }

    private companion object {
        const val MAX_ID_BYTES = 1_500
    }
}

private fun firebaseWalletExchange(firestore: FirebaseFirestore): ArenaWalletExchange =
    ArenaWalletExchange { owner ->
        val reference = firestore.collection("users").document(owner)
        val task = reference.get(Source.SERVER)
        ArenaPendingWalletRead {
            val snapshot = task.await()
            ArenaWalletDocument(
                reference.path,
                snapshot.data,
                snapshot.metadata.isFromCache,
                snapshot.metadata.hasPendingWrites(),
            )
        }
    }

private fun walletFailureKind(error: Exception): ArenaWalletFailureKind =
    when (error) {
        is ArenaWalletFailure -> error.kind
        is FirebaseFirestoreException ->
            when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> ArenaWalletFailureKind.ACCESS_DENIED
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> ArenaWalletFailureKind.ACCOUNT_UNAVAILABLE
                FirebaseFirestoreException.Code.UNAVAILABLE -> ArenaWalletFailureKind.NO_NETWORK
                else -> ArenaWalletFailureKind.UNAVAILABLE
            }
        is IOException -> ArenaWalletFailureKind.NO_NETWORK
        else -> ArenaWalletFailureKind.UNAVAILABLE
    }
