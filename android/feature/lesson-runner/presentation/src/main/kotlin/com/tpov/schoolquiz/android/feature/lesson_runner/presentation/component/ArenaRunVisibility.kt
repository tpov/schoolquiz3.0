package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase

/** Retained, process-local presentation intent. Never persist auto-reveal authority in a route/checkpoint. */
internal class ArenaRunVisibility {
    var foreground = false
        private set
    private var presented: ArenaRunToken? = null
    private var advance: ArenaRunToken? = null
    private var background: BackgroundBoundary? = null

    fun foreground() {
        foreground = true
    }

    fun presented(token: ArenaRunToken) {
        if (foreground) presented = token
    }

    fun advancing(token: ArenaRunToken) {
        check(foreground)
        advance = token
    }

    fun background(
        changingConfigurations: Boolean,
        nowMs: Long,
    ) {
        if (changingConfigurations) return
        foreground = false
        // Preserve the first actual stop while a write is pending; repeated callbacks cannot move its boundary.
        if (background == null) background = BackgroundBoundary(presented ?: advance, nowMs)
        advance = null
    }

    fun writeFailed() {
        advance = null // Explicit reload may recover a PAUSED row, but must not automatically reveal it.
    }

    /** Called only for a saved, idle, owner-verified Continuable observation, never for a proposed checkpoint. */
    fun afterSaved(
        token: ArenaRunToken,
        questionStartedAtMs: Long = 0L,
    ): ArenaRunAction? {
        val stopped = background
        if (stopped != null) {
            val original = stopped.token
            val sameSlot = original == null || token.sameAttempt(original) && token.index == original.index
            return if (sameSlot && token.phase in setOf(ArenaRunnerPhase.ANSWERING, ArenaRunnerPhase.FEEDBACK)) {
                // Keep the boundary until a saved read proves the slot closed. A failed CAS may be retried
                // only after explicit reload, still with the original stop time and never on the next slot.
                // A first claim may itself start after ON_STOP while local IO completes. No visible slot
                // existed at that stop: close this one at its own start, not at a negative duration.
                val atMs = if (original == null) maxOf(stopped.atMs, questionStartedAtMs) else stopped.atMs
                if (original == null) background = stopped.copy(token = token, atMs = atMs)
                ArenaRunAction.BackgroundAt(atMs)
            } else {
                background = null
                null
            }
        }
        val origin = advance
        advance = null
        return if (
            origin != null && foreground && token.isPreparedSuccessorOf(origin)
        ) {
            ArenaRunAction.RevealPrepared
        } else {
            null
        }
    }

    private data class BackgroundBoundary(val token: ArenaRunToken?, val atMs: Long)
}

private fun ArenaRunToken.sameAttempt(other: ArenaRunToken): Boolean =
    stamp == other.stamp && ticketId == other.ticketId && attemptId == other.attemptId

private fun ArenaRunToken.isPreparedSuccessorOf(origin: ArenaRunToken): Boolean {
    val nextRevision = origin.revision < Long.MAX_VALUE && revision == origin.revision + 1
    val nextSlot = index == origin.index + 1 && phase == ArenaRunnerPhase.PAUSED
    return sameAttempt(origin) && nextRevision && nextSlot
}
