package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryCommandOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryResolution
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryPage

/** Explicit caller-owned operations. Every invocation requires the initiating session, never just a UID. */
@Suppress("TooManyFunctions") // One explicit entry lifecycle; splitting read/intent/dispatch would hide its boundary.
interface ArenaEntryRepository {
    suspend fun localTickets(
        stamp: ArenaSessionStamp,
        size: Int = 20,
        cursor: ArenaEntriesCursor? = null,
    ): Result<ArenaEntriesPage>

    suspend fun pendingPreparations(
        stamp: ArenaSessionStamp,
        size: Int = 20,
        cursor: ArenaPreparationsCursor? = null,
    ): Result<ArenaPreparationsPage>

    /** Local absence is not a revocation. Does not recover, prepare, launch or retry. */
    suspend fun readTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaLocalEntry?>

    /** Requires explicit online consent and preserves the caller's original request ID and ceiling. */
    suspend fun recordPrepareConsent(
        stamp: ArenaSessionStamp,
        request: ArenaPrepareRequest,
    ): Result<Unit>

    suspend fun dispatchPrepare(
        stamp: ArenaSessionStamp,
        requestId: String,
    ): Result<ArenaEntryCommandOutcome>

    /** Explicit header recovery may commit a prefix before a later failure; never a complete-inventory assertion. */
    suspend fun recoverPage(
        stamp: ArenaSessionStamp,
        size: Int = 20,
        cursor: ArenaRecoveryCursor? = null,
    ): Result<ArenaRecoveryPage>

    suspend fun recoverTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaLocalEntry>

    /** Durable local choice only; server cancellation requires the separate dispatch. */
    suspend fun recordCancelIntent(
        stamp: ArenaSessionStamp,
        ticketId: String,
        requestId: String,
    ): Result<Unit>

    suspend fun dispatchCancel(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaEntryCommandOutcome>

    suspend fun reconcileCancelledSettlement(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaEntryResolution>

    /** Restore the retained SAME settlement body through the existing sender, never a new attempt or server result. */
    suspend fun restorePendingSettlement(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<Unit>
}
