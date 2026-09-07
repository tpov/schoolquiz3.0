package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation

/** Explicit read only. Never a snapshot, automatic acknowledgement, local decision or launch claim. */
interface ArenaTicketStatusRemoteDataSource {
    suspend fun status(request: ArenaTicketGetRequest): Result<ArenaTicketStatusObservation>
}
