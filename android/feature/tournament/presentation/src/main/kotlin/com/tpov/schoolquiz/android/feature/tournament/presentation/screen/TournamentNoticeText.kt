package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentRefusal

/**
 * Честное состояние (AD-21) словами. Имя серверной причины показывается как есть в прочих
 * отказах — но ветвление здесь уже случилось, экран только называет исход.
 */
@Composable
internal fun noticeText(notice: TournamentNotice): String =
    when (notice) {
        TournamentNotice.NotLoaded -> stringResource(R.string.tournament_state_not_loaded)
        // Готовность — своё слово: «работаем» занят занятостью, а не её отсутствием.
        TournamentNotice.Ready -> stringResource(R.string.tournament_state_ready)
        TournamentNotice.AccountUnavailable -> stringResource(R.string.tournament_state_account_unavailable)
        TournamentNotice.AccountChanged -> stringResource(R.string.tournament_state_account_changed)
        TournamentNotice.Offline -> stringResource(R.string.tournament_state_offline)
        TournamentNotice.Pending -> stringResource(R.string.tournament_state_pending)
        TournamentNotice.Retry -> stringResource(R.string.tournament_state_retry)
        is TournamentNotice.Refused ->
            when (notice.reason) {
                TournamentRefusal.OUTSIDE_QUALIFICATION_WINDOW ->
                    stringResource(R.string.tournament_refusal_outside_window)
                TournamentRefusal.NO_ADMISSION_BASIS ->
                    stringResource(R.string.tournament_refusal_no_basis)
                TournamentRefusal.INSUFFICIENT_BOXES ->
                    stringResource(R.string.tournament_refusal_insufficient)
                TournamentRefusal.SOURCE_NOT_ELIGIBLE ->
                    stringResource(R.string.tournament_refusal_source)
                TournamentRefusal.CONFLICTING_REPLAY ->
                    stringResource(R.string.tournament_refusal_conflicting_replay)
                TournamentRefusal.MEETING_BOUNDARY ->
                    stringResource(R.string.tournament_refusal_meeting_boundary)
                TournamentRefusal.OTHER ->
                    stringResource(R.string.tournament_refusal_other, notice.serverReason)
            }
    }

/** Проценты для витрины: пустота честная, а не ноль. */
@Composable
internal fun percentOrDash(value: Double?): String =
    value?.let { "%.2f".format(it) } ?: stringResource(R.string.tournament_percent_absent)
