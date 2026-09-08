package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation

/** Explicit read only. A displayed balance never authorizes the next prepare operation. */
fun interface ArenaWalletRepository {
    suspend fun read(stamp: ArenaSessionStamp): Result<ArenaWalletObservation>
}
