package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryResolution
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArenaEntryRecoverySummaryTest {
    @Test fun cancelledSettlementIsDiscoverableThroughListAndReadWithoutAnAutomaticEffect() =
        runTest {
            val f = EntryFacadeFixture(retainSettlement = true)
            val terminal =
                f.codec.terminal(
                    f.row,
                    ArenaCancelResponse.Cancelled(f.header.ticketId, f.base.cancelled.receipt),
                    200,
                ).copy(localUse = "LAUNCHED", decision = "SETTLE", snapshotJson = null, checkpointJson = null)
            f.seed(terminal)
            val before = f.base.snapshot()
            val repository = f.repository(this)
            val entry = assertNotNull(repository.readTicket(f.stamp, f.header.ticketId).getOrThrow())
            val listed = repository.localTickets(f.stamp).getOrThrow().entries.single()
            assertEquals(entry, listed)
            assertEquals(ArenaLocalEntryState.TERMINAL_OBSERVED, entry.state)
            assertEquals(ArenaTicketStatus.CANCELLED, entry.header?.status)
            assertEquals(ArenaLocalDecision.SETTLE, entry.localDecision)
            assertEquals(f.header.requestId, entry.header?.requestId)
            assertEquals(before, f.base.snapshot())
            assertTrue(f.base.requests.isEmpty())
            assertFalse(entry.toString().contains("bodyJson"))
            assertIs<ArenaEntryResolution.CancelledResolved>(
                repository.reconcileCancelledSettlement(f.stamp, entry.ticketId).getOrThrow(),
            )
            assertTrue(f.storage.outbox.isEmpty())
            assertTrue(f.storage.acknowledgedMutations.isEmpty())
            // The tombstone is retained, not reinterpreted as a new pending payout.
            assertEquals(
                ArenaLocalDecision.SETTLE,
                repository.readTicket(f.stamp, entry.ticketId).getOrThrow()?.localDecision,
            )
            val resolved = f.base.snapshot()
            repository.reconcileCancelledSettlement(f.stamp, entry.ticketId).getOrThrow()
            assertEquals(resolved, f.base.snapshot())
        }

    @Test fun terminalCancelAndCorruptRowsDoNotEraseTheLocalDecisionMarker() {
        val f = EntryFacadeFixture()
        val terminal =
            f.codec.terminal(
                f.row,
                ArenaCancelResponse.Cancelled(f.header.ticketId, f.base.cancelled.receipt),
                200,
            ).copy(decision = "CANCEL", snapshotJson = null)
        assertEquals(ArenaLocalDecision.CANCEL, f.mapper.ticket("player", terminal).localDecision)
        val malformed = f.mapper.ticket("player", f.row.copy(termsJson = "broken", decision = "SETTLE"))
        assertEquals(ArenaLocalEntryState.NEEDS_RECOVERY, malformed.state)
        assertEquals(ArenaLocalDecision.SETTLE, malformed.localDecision)
        assertEquals(
            ArenaLocalDecision.UNKNOWN,
            f.mapper.ticket("player", f.row.copy(decision = "future")).localDecision,
        )
        assertEquals(ArenaLocalDecision.NONE, f.mapper.ticket("player", f.row).localDecision)
    }
}
