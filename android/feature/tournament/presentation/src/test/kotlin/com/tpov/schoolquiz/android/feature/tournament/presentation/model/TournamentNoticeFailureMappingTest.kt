package com.tpov.schoolquiz.android.feature.tournament.presentation.model

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ветвление [failureNotice] по структурным причинам (ретро item 7): SETTLEMENT_REUSE говорит
 * «эффект уже на сервере» — исходу своя честная ветвь [TournamentNotice.AlreadyRecorded],
 * а не склад «повтори ещё раз».
 */
class TournamentNoticeFailureMappingTest {
    @Test
    fun settlementAlreadyRecordedIsItsOwnHonestOutcomeNotARetry() {
        val notice = failureNotice(SyncFailure(SyncError.SettlementAlreadyRecorded()))
        assertEquals(TournamentNotice.AlreadyRecorded, notice)
    }

    @Test
    fun otherQueueDomainOutcomesKeepTheirBranches() {
        assertEquals(
            TournamentNotice.Retry,
            failureNotice(SyncFailure(SyncError.TermsConsentRequired)),
        )
        assertEquals(
            TournamentNotice.Retry,
            failureNotice(SyncFailure(SyncError.VersionConflict(serverVersion = 4L))),
        )
        assertEquals(
            TournamentNotice.Offline,
            failureNotice(SyncFailure(SyncError.NoNetwork)),
        )
    }
}
