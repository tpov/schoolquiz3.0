package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation

/** Explicit read only. Never a snapshot, automatic acknowledgement, local decision or launch claim. */
interface ArenaTicketStatusRemoteDataSource {
    suspend fun status(request: ArenaTicketGetRequest): Result<ArenaTicketStatusObservation>

    /** Required proof-read lease, retained by the resolver through its later storage transaction. */
    suspend fun status(
        expected: OutboxSession,
        request: ArenaTicketGetRequest,
    ): Result<ArenaTicketStatusObservation>
}
