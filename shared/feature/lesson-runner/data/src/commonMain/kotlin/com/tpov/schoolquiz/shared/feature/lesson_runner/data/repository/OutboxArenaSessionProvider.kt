package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp

/** No cached uid, new epoch counter, network call or mutation: preserve the source stamp exactly. */
class OutboxArenaSessionProvider(private val sessions: OutboxSessionProvider) : ArenaSessionProvider {
    override fun current(): ArenaSessionStamp? =
        sessions.current()?.let { ArenaSessionStamp(ownerUid = it.ownerUid, generation = it.generation) }
}
