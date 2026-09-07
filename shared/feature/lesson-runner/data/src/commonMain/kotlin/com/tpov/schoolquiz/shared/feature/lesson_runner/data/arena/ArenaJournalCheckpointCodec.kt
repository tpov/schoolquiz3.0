package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot

/** Required pure feature validator. There is deliberately no permissive production default. */
interface ArenaJournalCheckpointCodec {
    fun initial(
        snapshot: ArenaTicketSnapshot,
        snapshotSha256: String,
        request: ArenaInitialCheckpoint,
    ): String

    /** Null previous means strict initial state; completion also requires exact durable final intent. */
    fun validate(
        snapshot: ArenaTicketSnapshot,
        snapshotSha256: String,
        previousJson: String?,
        candidateJson: String,
        completionPayloadJson: String?,
    )
}

data class ArenaSavedCheckpoint(
    val snapshot: ArenaTicketSnapshot,
    val snapshotSha256: String,
    val json: String,
    val revision: Long,
)
