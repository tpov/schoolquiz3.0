package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import kotlinx.coroutines.flow.Flow

/** Observe the existing authentication epoch, including same-UID reauthentication. Never creates a new epoch. */
fun interface ArenaSessionObserver {
    fun observeSessions(): Flow<ArenaSessionStamp?>
}
