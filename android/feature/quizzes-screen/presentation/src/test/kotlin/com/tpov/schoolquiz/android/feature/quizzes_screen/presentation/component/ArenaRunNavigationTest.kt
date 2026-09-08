package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArenaRunNavigationTest {
    private val session = ArenaSessionStamp("owner", 7)

    @Test
    fun `prepared row produces a fresh request bound to its exact session`() {
        val request = arenaRunRequest(state(preparedEntry()), session, TICKET_ID, fresh = true)

        assertNotNull(request)
        assertEquals(session, request.initiatingSession)
        assertEquals(session, request.fresh?.session)
        assertEquals(SessionMode.LEARNING, request.fresh?.mode)
    }

    @Test
    fun `readable row produces ResumeOnly request while retaining its live epoch`() {
        val request =
            arenaRunRequest(
                state(preparedEntry(state = ArenaLocalEntryState.RESUME_OR_RECONCILE)),
                session,
                TICKET_ID,
                fresh = false,
            )

        assertNotNull(request)
        assertNull(request.fresh)
        assertEquals(session, request.initiatingSession)
    }

    @Test
    fun `Fresh cannot be used for a resumable row and ResumeOnly cannot claim a prepared row`() {
        val resumable = state(preparedEntry(state = ArenaLocalEntryState.RESUME_OR_RECONCILE))
        val prepared = state(preparedEntry())

        assertNull(arenaRunRequest(resumable, session, TICKET_ID, fresh = true))
        assertNull(arenaRunRequest(prepared, session, TICKET_ID, fresh = false))
    }

    @Test
    fun `ABA session change rejects a callback captured by the old generation`() {
        val current = session.copy(generation = session.generation + 1)
        val afterReauthentication = state(preparedEntry(), session = current)

        assertNull(arenaRunRequest(afterReauthentication, session, TICKET_ID, fresh = true))
        assertNotNull(arenaRunRequest(afterReauthentication, current, TICKET_ID, fresh = true))
    }

    @Test
    fun `busy malformed and duplicate ticket rows never become navigation authority`() {
        assertNull(arenaRunRequest(state(preparedEntry(), busy = true), session, TICKET_ID, fresh = true))
        assertNull(
            arenaRunRequest(
                state(preparedEntry(ownerUid = "another-owner")),
                session,
                TICKET_ID,
                fresh = true,
            ),
        )
        assertNull(
            arenaRunRequest(
                state(preparedEntry(headerTicketId = "another-ticket")),
                session,
                TICKET_ID,
                fresh = true,
            ),
        )
        assertNull(
            arenaRunRequest(
                state(preparedEntry(), tickets = listOf(preparedEntry(), preparedEntry())),
                session,
                TICKET_ID,
                fresh = true,
            ),
        )
    }

    @Test
    fun `Fresh denial table never promotes terminal decision recovery or unreserved rows`() {
        val denied =
            listOf(
                preparedEntry(localDecision = ArenaLocalDecision.CANCEL),
                preparedEntry(localDecision = ArenaLocalDecision.SETTLE),
                preparedEntry(state = ArenaLocalEntryState.TERMINAL_OBSERVED),
                preparedEntry(state = ArenaLocalEntryState.NEEDS_RECOVERY, includeHeader = false),
                preparedEntry(status = ArenaTicketStatus.CANCELLED),
                preparedEntry(status = ArenaTicketStatus.SETTLED),
            )

        denied.forEach { row ->
            assertNull(arenaRunRequest(state(row), session, TICKET_ID, fresh = true))
        }
    }

    private fun state(
        entry: ArenaLocalEntry,
        session: ArenaSessionStamp = this.session,
        busy: Boolean = false,
        tickets: List<ArenaLocalEntry> = listOf(entry),
    ): ArenaEntryUiState = ArenaEntryUiState(session = session, tickets = tickets, busy = busy)

    private fun preparedEntry(
        ownerUid: String = session.ownerUid,
        headerTicketId: String = TICKET_ID,
        state: ArenaLocalEntryState = ArenaLocalEntryState.PREPARED_SNAPSHOT,
        status: ArenaTicketStatus = ArenaTicketStatus.RESERVED,
        localDecision: ArenaLocalDecision = ArenaLocalDecision.NONE,
        includeHeader: Boolean = true,
    ) = ArenaLocalEntry(
        ticketId = TICKET_ID,
        header =
            if (includeHeader) {
                ArenaTicketHeader(
                    schemaVersion = 1,
                    ticketId = headerTicketId,
                    attemptId = "attempt",
                    ownerUid = ownerUid,
                    requestId = "request",
                    mode = ArenaEntryMode.SELECTED,
                    difficulty = Difficulty.EASY,
                    lessonId = "lesson",
                    lessonVersion = 1,
                    status = status,
                    priceNolics = 1,
                    lifeCost = 1,
                    issuedAtMs = 1,
                    dayId = "2026-09-08",
                )
            } else {
                null
            },
        state = state,
        localDecision = localDecision,
    )

    private companion object {
        const val TICKET_ID = "ticket"
    }
}
