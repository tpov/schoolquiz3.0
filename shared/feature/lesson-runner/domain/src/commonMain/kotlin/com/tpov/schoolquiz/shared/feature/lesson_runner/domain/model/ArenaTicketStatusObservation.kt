package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/** Immutable metadata projection only: no frozen questions, live hierarchy or launch capability. */
data class ArenaTicketStatusMetadata(
    val header: ArenaTicketHeader,
    val authorUid: String,
    val ratingVersion: Long,
    val contentContext: ArenaTicketContentContext?,
)

/**
 * A read-only server observation, deliberately unrelated to ArenaEntryTicket/ArenaTicketSnapshot.
 * RESERVED is not permission to launch; terminal values carry the actual permanent receipt.
 */
sealed interface ArenaTicketStatusObservation {
    val metadata: ArenaTicketStatusMetadata

    data class Reserved(override val metadata: ArenaTicketStatusMetadata) : ArenaTicketStatusObservation {
        init {
            require(metadata.header.status == ArenaTicketStatus.RESERVED)
        }
    }

    data class Cancelled(
        override val metadata: ArenaTicketStatusMetadata,
        val receipt: ArenaCancellationReceipt,
    ) : ArenaTicketStatusObservation {
        init {
            require(metadata.header.status == ArenaTicketStatus.CANCELLED)
        }
    }

    data class Settled(
        override val metadata: ArenaTicketStatusMetadata,
        val receipt: ArenaSettlementReceipt,
    ) : ArenaTicketStatusObservation {
        init {
            require(metadata.header.status == ArenaTicketStatus.SETTLED)
        }
    }
}
