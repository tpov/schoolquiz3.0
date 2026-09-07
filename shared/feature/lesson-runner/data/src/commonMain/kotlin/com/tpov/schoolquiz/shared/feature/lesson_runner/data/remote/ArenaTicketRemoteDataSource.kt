package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketListRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketPage

/** Remote boundary only: no automatic purchase, retry loop, request-ID generation, cache or wallet writes. */
interface ArenaTicketRemoteDataSource {
    suspend fun prepare(request: ArenaPrepareRequest): Result<ArenaPrepareResponse>

    suspend fun list(request: ArenaTicketListRequest): Result<ArenaTicketPage>

    suspend fun get(request: ArenaTicketGetRequest): Result<ArenaEntryTicket>

    suspend fun cancel(request: ArenaTicketCancelRequest): Result<ArenaCancelResponse>

    /** Required original-session paths; never delegate these to a recapturing plain call. */
    suspend fun prepare(
        expected: OutboxSession,
        request: ArenaPrepareRequest,
    ): Result<ArenaPrepareResponse>

    suspend fun list(
        expected: OutboxSession,
        request: ArenaTicketListRequest,
    ): Result<ArenaTicketPage>

    suspend fun get(
        expected: OutboxSession,
        request: ArenaTicketGetRequest,
    ): Result<ArenaEntryTicket>

    suspend fun cancel(
        expected: OutboxSession,
        request: ArenaTicketCancelRequest,
    ): Result<ArenaCancelResponse>
}
