package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingPreparation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/** Pure row projection. No reads, writes, clock, defaults, live hierarchy or new financial/runner authority. */
class ArenaEntryLocalMapper(
    private val codec: ArenaJournalCodec,
    private val parser: ArenaTicketWireParser,
) {
    fun ticket(
        owner: String,
        row: ArenaTicketRecordEntity,
    ): ArenaLocalEntry {
        require(row.ownerUid == owner) // Never expose even an ID from a foreign storage result.
        val header =
            readable {
                codec.header(row).also {
                    require(codec.record(it, row.observedAtMs).termsJson == row.termsJson)
                }
            } ?: return ArenaLocalEntry(row.ticketId, null, ArenaLocalEntryState.NEEDS_RECOVERY)
        val state = readable { ticketState(row, header) } ?: ArenaLocalEntryState.NEEDS_RECOVERY
        return ArenaLocalEntry(row.ticketId, header, state)
    }

    fun preparation(
        owner: String,
        row: ArenaCommandEntity,
    ): ArenaPendingPreparation {
        require(row.ownerUid == owner)
        return readable {
            require(row.commandKind == "PREPARE" && row.ticketId == null && row.attemptId == null)
            require(row.createdAtMs >= 0 && row.state in setOf("PENDING", "ATTENTION"))
            val request = codec.prepare(row)
            require(row.lastFailure != "PRICE_CHANGED" || row.state == "ATTENTION")
            ArenaPendingPreparation(
                row.commandId,
                request,
                if (row.state == "PENDING") ArenaPreparationState.PENDING else ArenaPreparationState.ATTENTION,
                preparationReason(row),
            )
        } ?: ArenaPendingPreparation(
            row.commandId,
            null,
            ArenaPreparationState.NEEDS_RECOVERY,
            ArenaPreparationReason.NEEDS_RECOVERY,
        )
    }

    private fun ticketState(
        row: ArenaTicketRecordEntity,
        header: ArenaTicketHeader,
    ): ArenaLocalEntryState {
        require(row.observedAtMs >= 0 && row.checkpointRevision >= 0)
        require(row.localUse in setOf("UNUSED", "LAUNCHED") && row.decision in setOf("NONE", "CANCEL", "SETTLE"))
        require(row.decision != "SETTLE" || row.localUse == "LAUNCHED")
        if (header.status != ArenaTicketStatus.RESERVED) {
            validateTerminal(row, header) // No bank decode: unsafe/evicted snapshots must not erase terminal history.
            return ArenaLocalEntryState.TERMINAL_OBSERVED
        }
        require(row.terminalJson == null)
        return when (row.decision) {
            "CANCEL" -> ArenaLocalEntryState.CANCEL_PENDING
            "SETTLE" -> ArenaLocalEntryState.SETTLEMENT_PENDING
            else -> undecidedState(row)
        }
    }

    private fun undecidedState(row: ArenaTicketRecordEntity): ArenaLocalEntryState {
        // LAUNCHED without a checkpoint remains reconcile-only; never fabricate an initial checkpoint.
        codec.decode(row)
        return if (row.localUse == "LAUNCHED") {
            ArenaLocalEntryState.RESUME_OR_RECONCILE
        } else {
            require(row.checkpointJson == null && row.checkpointRevision == 0L)
            ArenaLocalEntryState.PREPARED_SNAPSHOT
        }
    }

    private fun validateTerminal(
        row: ArenaTicketRecordEntity,
        header: ArenaTicketHeader,
    ) {
        val wire =
            buildJsonObject {
                put("status", row.serverStatus)
                put("ticketId", row.ticketId)
                put("terminal", Json.parseToJsonElement(requireNotNull(row.terminalJson)))
            }.toString()
        val result = parser.cancel(wire, ArenaTicketCancelRequest(header, "local-summary"))
        if (result is ArenaCancelResponse.AlreadySettled && header.mode == ArenaEntryMode.RANDOM) {
            require(
                result.receipt.authorCreditNolics == 0L && result.receipt.sinkNolics == 0L &&
                    result.receipt.royaltyReserveDeltaNolics == 0L,
            )
        }
    }

    private fun preparationReason(row: ArenaCommandEntity): ArenaPreparationReason =
        when {
            row.lastFailure == "PRICE_CHANGED" -> ArenaPreparationReason.PRICE_CHANGED_REQUIRES_CONSENT
            row.state == "ATTENTION" -> ArenaPreparationReason.OTHER_ATTENTION
            row.lastFailure in
                setOf(
                    null,
                    "RETRY_REGEN",
                    "EXCHANGE_FAILED",
                )
            -> ArenaPreparationReason.RETRY_SAME_REQUEST
            else -> ArenaPreparationReason.OTHER_ATTENTION
        }

    // Stored corruption stays visible as a closed recovery state; never leak parser/SQL/body messages.
    @Suppress("TooGenericExceptionCaught")
    private fun <T> readable(block: () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
}
