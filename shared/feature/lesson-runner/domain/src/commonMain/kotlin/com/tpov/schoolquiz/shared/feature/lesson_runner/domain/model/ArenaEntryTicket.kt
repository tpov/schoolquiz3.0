package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay

enum class ArenaEntryMode { RANDOM, SELECTED }

enum class ArenaTicketStatus { RESERVED, CANCELLED, SETTLED }

/** Public recovery metadata only. No request hash, private grading key, wallet balance or price ceiling. */
@Suppress("LongParameterList") // Public wire projection; no optional field can silently default.
data class ArenaTicketHeader(
    val schemaVersion: Int,
    val ticketId: String,
    val attemptId: String,
    val ownerUid: String,
    val requestId: String,
    val mode: ArenaEntryMode,
    val difficulty: Difficulty,
    val lessonId: String,
    val lessonVersion: Long,
    val status: ArenaTicketStatus,
    val priceNolics: Int,
    val lifeCost: Long,
    val issuedAtMs: Long,
    val dayId: String,
)

data class ArenaTicketContentContext(
    val catalogId: String,
    val questId: String,
    val sectionId: String,
    val themeId: String,
)

data class ArenaTicketQuestion(
    val questionId: String,
    val questionVersion: Long,
    val order: Int,
    val language: String,
    val payload: String,
    val display: QuestionDisplay,
)

data class ArenaTicketSnapshot(
    val header: ArenaTicketHeader,
    val authorUid: String,
    val ratingVersion: Long,
    val contentContext: ArenaTicketContentContext?,
    val questions: List<ArenaTicketQuestion>,
)

/** Only Reserved can be considered by a future launcher; terminal recovery is never permission. */
sealed interface ArenaEntryTicket {
    val snapshot: ArenaTicketSnapshot

    data class Reserved(override val snapshot: ArenaTicketSnapshot) : ArenaEntryTicket {
        init {
            require(snapshot.header.status == ArenaTicketStatus.RESERVED)
        }
    }

    data class Cancelled(
        override val snapshot: ArenaTicketSnapshot,
        val receipt: ArenaCancellationReceipt,
    ) : ArenaEntryTicket {
        init {
            require(snapshot.header.status == ArenaTicketStatus.CANCELLED)
        }
    }

    data class Settled(
        override val snapshot: ArenaTicketSnapshot,
        val receipt: ArenaSettlementReceipt,
    ) : ArenaEntryTicket {
        init {
            require(snapshot.header.status == ArenaTicketStatus.SETTLED)
        }
    }
}

data class ArenaCancellationReceipt(
    val receiptId: String,
    val cancelledAtMs: Long,
    val releasedNolics: Int,
    val releasedLifePoints: Long,
)

@Suppress("LongParameterList") // Frozen settlement receipt, including explicit conservation terms.
data class ArenaSettlementReceipt(
    val receiptId: String,
    val attemptId: String,
    val settledAtMs: Long,
    val percentScore: Double,
    val consumedNolics: Int,
    val consumedLifePoints: Long,
    val authorCreditNolics: Long,
    val sinkNolics: Long,
    val royaltyReserveDeltaNolics: Long,
)

/** Exclusive document-ID cursor. Multiple pages do not promise an atomic inventory. */
data class ArenaTicketPage(val tickets: List<ArenaTicketHeader>, val nextTicketId: String?)
