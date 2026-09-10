package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaFrozenRatingState
import kotlinx.coroutines.flow.Flow

/** Bankless rating port: its implementation reads only the frozen arena settlement and receipts. */
interface ArenaFrozenRatingRepository {
    fun observe(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Flow<ArenaFrozenRatingState>

    /** Queued is local durability, not server acceptance. */
    suspend fun submit(
        stamp: ArenaSessionStamp,
        ticketId: String,
        rating: Int,
    ): Result<ArenaFrozenRatingState>
}
