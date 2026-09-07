package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

/** Process-local projection of the existing authentication epoch, never wire or durable authority. */
data class ArenaSessionStamp(val ownerUid: String, val generation: Long)

/** Read-only view of the app's existing session provider; does not sign in or create an owner gate. */
fun interface ArenaSessionProvider {
    fun current(): ArenaSessionStamp?
}
