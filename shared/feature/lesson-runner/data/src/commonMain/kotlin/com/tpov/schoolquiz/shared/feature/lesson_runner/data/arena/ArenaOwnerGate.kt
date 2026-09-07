package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind

interface ArenaOwnerGate {
    fun capture(owner: String): OutboxSession

    suspend fun <T> withOwner(
        session: OutboxSession,
        block: suspend () -> T,
    ): T
}

/** Shares the existing in-process account-switch gate. This is NOT a multiprocess auth lock. */
class OutboxArenaOwnerGate(
    private val sessions: OutboxSessionProvider,
    private val engine: OutboxEngine,
) : ArenaOwnerGate {
    override fun capture(owner: String): OutboxSession =
        sessions.current()?.takeIf { it.ownerUid == owner }
            ?: throw ArenaRemoteFailure(ArenaRemoteFailureKind.ACCOUNT_CHANGED)

    override suspend fun <T> withOwner(
        session: OutboxSession,
        block: suspend () -> T,
    ): T =
        engine.withoutPasses {
            ensure(session)
            block().also { ensure(session) }
        }

    private fun ensure(session: OutboxSession) {
        if (sessions.current() != session) throw ArenaRemoteFailure(ArenaRemoteFailureKind.ACCOUNT_CHANGED)
    }
}
