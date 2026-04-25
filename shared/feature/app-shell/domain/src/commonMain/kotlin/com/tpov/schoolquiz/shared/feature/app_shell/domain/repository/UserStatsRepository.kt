package com.tpov.schoolquiz.shared.feature.app_shell.domain.repository

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for user stats observation.
 *
 * Data layer provides the production implementation (Firebase/Room-backed).
 * Tests use [FakeUserStatsRepository] (in-memory fake, lives in test source set).
 *
 * Domain rule: signatures use only domain types — no DTO, Entity, HTTP codes.
 * Domain rule: no DI annotations — DI wiring is phase-01 concern.
 */
interface UserStatsRepository {

    /**
     * Continuously observes user stats.
     * Emits a new value whenever stats change (login, skill gain, role change, etc.).
     *
     * The first emission represents the current persisted state.
     * Callers should handle the case where the first emission may be [UserStats.guest()].
     */
    fun observeStats(): Flow<UserStats>

    /**
     * One-shot current snapshot. Used for cold-start / fallback state initialisation.
     * Returns [UserStats.guest()] if no authenticated user is available.
     */
    suspend fun currentStats(): UserStats

    /**
     * Writes the developer qualification level locally (dev mode only).
     * ADR-HLA-02: this is the only permitted client write path for the developer field.
     */
    suspend fun setLocalDeveloperLevel(value: Int)

    /**
     * Triggers a full profile refresh from the remote source (Firestore).
     * Called by SyncWorker. Returns [Result.failure] on network or parsing error.
     */
    suspend fun refreshProfile(): Result<Unit>
}
