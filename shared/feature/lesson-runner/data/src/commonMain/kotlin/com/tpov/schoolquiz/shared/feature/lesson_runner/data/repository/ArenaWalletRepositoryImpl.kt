package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaWalletRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/** Stateless and lazy. No Room/profile writes, owned scope, polling or regeneration. */
class ArenaWalletRepositoryImpl(
    private val remote: ArenaWalletRemoteDataSource,
    private val sessions: ArenaSessionProvider,
    private val dispatcher: CoroutineDispatcher,
) : ArenaWalletRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(stamp: ArenaSessionStamp): Result<ArenaWalletObservation> {
        coroutineContext.ensureActive()
        return try {
            requireSession(stamp)
            val result =
                withContext(dispatcher) {
                    requireSession(stamp)
                    val observation = remote.read(OutboxSession(stamp.ownerUid, stamp.generation)).getOrThrow()
                    coroutineContext.ensureActive()
                    requireSession(stamp)
                    if (observation.stamp != stamp) throw ArenaWalletFailure(ArenaWalletFailureKind.NEEDS_REFRESH)
                    observation
                }
            coroutineContext.ensureActive()
            requireSession(stamp) // Fence the dispatcher return too, not only the IO block.
            Result.success(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ArenaWalletFailure) {
            failed(stamp, failure.kind)
        } catch (_: Exception) {
            failed(stamp, ArenaWalletFailureKind.UNAVAILABLE)
        }
    }

    private suspend fun failed(
        stamp: ArenaSessionStamp,
        fallback: ArenaWalletFailureKind,
    ): Result<ArenaWalletObservation> {
        coroutineContext.ensureActive()
        val current = sessions.current()
        val kind =
            when {
                current == null -> ArenaWalletFailureKind.ACCOUNT_UNAVAILABLE
                current != stamp -> ArenaWalletFailureKind.ACCOUNT_CHANGED
                else -> fallback
            }
        return Result.failure(ArenaWalletFailure(kind))
    }

    private fun requireSession(stamp: ArenaSessionStamp) {
        val current = sessions.current()
        if (current == null) throw ArenaWalletFailure(ArenaWalletFailureKind.ACCOUNT_UNAVAILABLE)
        if (current != stamp) throw ArenaWalletFailure(ArenaWalletFailureKind.ACCOUNT_CHANGED)
    }
}
