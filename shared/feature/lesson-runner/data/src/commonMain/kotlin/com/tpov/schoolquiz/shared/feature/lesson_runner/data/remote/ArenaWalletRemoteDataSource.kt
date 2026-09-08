package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation

/** Original owner/epoch is mandatory; implementations must not recapture by UID. */
fun interface ArenaWalletRemoteDataSource {
    suspend fun read(expected: OutboxSession): Result<ArenaWalletObservation>
}
