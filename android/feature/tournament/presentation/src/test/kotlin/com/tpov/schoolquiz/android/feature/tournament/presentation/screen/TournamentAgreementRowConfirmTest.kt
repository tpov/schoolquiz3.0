package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingParticipant
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingSlot
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentReadySignal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Отставка кнопок подтверждения (правка ревью 5.11): согласованный слот неизменен у сервера —
 * строка, уже согласованная или уже подтверждённая самим участником, подтверждение не зовёт.
 * Решение — чистая функция состояния: Compose-харнесса у модуля нет, компоненты покрыты сами.
 */
class TournamentAgreementRowConfirmTest {
    private val slot = 4_000L

    private fun page(
        agreedSlot: Long? = null,
        confirmations: List<TournamentMeetingSlot> = emptyList(),
    ): TournamentMeetingPage =
        TournamentMeetingPage(
            eventId = "event_champ_0000000001",
            meeting =
                TournamentMeetingView(
                    matchId = "match_qf_00000001",
                    status = "WAITING_FOR_AGREEMENT",
                    slotStartsAtMs = agreedSlot,
                    agreedAtMs = agreedSlot?.let { 900L },
                    windowEndMs = null,
                    startedAtMs = null,
                    endsAtMs = null,
                    resultDue = null,
                    winnerUid = null,
                    loserUid = null,
                    technicalForfeit = null,
                ),
            schedulingDeadlineMs = 5_000L,
            stageStartsAtMs = 0L,
            stageEndsAtMs = 10_000L,
            participants = listOf(TournamentMeetingParticipant("owner-1", 1), TournamentMeetingParticipant("owner-2", 2)),
            offers = listOf(TournamentMeetingSlot("offer_a", "owner-2", slot, 900L)),
            hasMoreOffers = false,
            nextOfferId = null,
            confirmations = confirmations,
            hasMoreConfirmations = false,
            nextConfirmationId = null,
            ready = emptyList(),
            serverNowMs = 1_000L,
        )

    @Test
    fun `an open agreement keeps the row confirm available`() {
        assertTrue(rowConfirmEnabled(slot, page(), ownerUid = "owner-1", busy = false))
    }

    @Test
    fun `an agreed slot retires every row confirm`() {
        assertFalse(rowConfirmEnabled(slot, page(agreedSlot = slot), ownerUid = "owner-1", busy = false))
    }

    @Test
    fun `a slot already confirmed by this participant retires its row confirm`() {
        val confirmed =
            page(confirmations = listOf(TournamentMeetingSlot("conf_a", "owner-1", slot, 950L)))
        assertFalse(rowConfirmEnabled(slot, confirmed, ownerUid = "owner-1", busy = false))
        // Чужое подтверждение того же слота своей строки не мешает: сервер решает поимённо.
        val confirmedByOther =
            page(confirmations = listOf(TournamentMeetingSlot("conf_b", "owner-2", slot, 950L)))
        assertTrue(rowConfirmEnabled(slot, confirmedByOther, ownerUid = "owner-1", busy = false))
    }

    @Test
    fun `busy retires the row confirm regardless of the meeting`() {
        assertFalse(rowConfirmEnabled(slot, page(), ownerUid = "owner-1", busy = true))
    }
}
