@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryEvent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryPage
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryUiState
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.availableEntryActions
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.canOfferCancellation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.canReadArenaRun
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.canRetryPreparation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.canStartArenaRun
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingPreparation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp

internal fun LazyListScope.arenaEntryItems(
    state: ArenaEntryUiState,
    callbacks: ArenaEntryCallbacks,
) {
    val onEvent = callbacks.onEvent
    item(key = "entry_header") { ArenaEntryHeader(state, onEvent) }
    val stamp = state.session ?: return
    items(state.preparations, key = { "entry_prepare_" + it.requestId }) { row ->
        ArenaPreparationCard(row, stamp, state.busy, onEvent)
    }
    items(state.tickets, key = { "entry_ticket_" + it.ticketId }) { row ->
        ArenaTicketCard(row, stamp, state.busy, callbacks)
    }
    item(key = "entry_pages") {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (state.canLoadPreparations) {
                EntryButton(R.string.arena_entry_more_preparations, "arena_preparations_more", !state.busy) {
                    onEvent(ArenaEntryEvent.Load(stamp, ArenaEntryPage.PREPARATIONS, true))
                }
            }
            if (state.canLoadTickets) {
                EntryButton(R.string.arena_entry_more_tickets, "arena_tickets_more", !state.busy) {
                    onEvent(ArenaEntryEvent.Load(stamp, ArenaEntryPage.TICKETS, true))
                }
            }
            if (state.canRecoverMore) {
                EntryButton(R.string.arena_entry_more_recovery, "arena_recovery_more", !state.busy) {
                    onEvent(ArenaEntryEvent.Load(stamp, ArenaEntryPage.RECOVERY, true))
                }
            }
        }
    }
}

@Composable
private fun ArenaEntryHeader(
    state: ArenaEntryUiState,
    onEvent: (ArenaEntryEvent) -> Unit,
) {
    val stamp = state.session
    val enabled = stamp != null && !state.busy
    Card(Modifier.fillMaxWidth().padding(16.dp).testTag("arena_entry")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.arena_entry_title), style = MaterialTheme.typography.titleLarge)
            val wallet = state.wallet
            if (wallet == null) {
                Text(stringResource(R.string.arena_entry_wallet_unknown), Modifier.testTag("arena_wallet_unknown"))
            } else {
                Text(
                    stringResource(R.string.arena_entry_nolics, wallet.availableNolics, wallet.reservedNolics),
                    Modifier.testTag("arena_wallet_nolics"),
                )
                Text(stringResource(R.string.arena_entry_life, wallet.storedLifePoints, wallet.reservedLifePoints))
                Text(
                    stringResource(
                        R.string.arena_entry_observed,
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(wallet.observedAtMs)),
                    ),
                )
            }
            EntryButton(R.string.arena_entry_refresh_wallet, "arena_wallet_refresh", enabled) {
                if (stamp != null) onEvent(ArenaEntryEvent.Load(stamp, ArenaEntryPage.WALLET))
            }
            EntryButton(R.string.arena_entry_random, "arena_random", enabled) {
                if (stamp != null) onEvent(ArenaEntryEvent.ShowPrepare(stamp, null))
            }
            EntryButton(R.string.arena_entry_tickets, "arena_tickets_load", enabled) {
                if (stamp != null) onEvent(ArenaEntryEvent.Load(stamp, ArenaEntryPage.TICKETS))
            }
            EntryButton(R.string.arena_entry_preparations, "arena_preparations_load", enabled) {
                if (stamp != null) onEvent(ArenaEntryEvent.Load(stamp, ArenaEntryPage.PREPARATIONS))
            }
            EntryButton(R.string.arena_entry_recover, "arena_recovery_load", enabled) {
                if (stamp != null) onEvent(ArenaEntryEvent.Load(stamp, ArenaEntryPage.RECOVERY))
            }
            if (state.busy) CircularProgressIndicator(Modifier.testTag("arena_entry_busy"))
            Text(stringResource(state.notice.entryResource()), Modifier.testTag("arena_entry_notice"))
            state.lastRequestId?.let { Text(stringResource(R.string.arena_entry_request_id, it)) }
            if (state.hasLoadedTickets && state.tickets.isEmpty()) {
                Text(
                    stringResource(R.string.arena_entry_no_local_tickets),
                )
            }
            if (state.hasLoadedPreparations && state.preparations.isEmpty()) {
                Text(
                    stringResource(R.string.arena_entry_no_preparations),
                )
            }
        }
    }
}

@Composable
private fun ArenaPreparationCard(
    row: ArenaPendingPreparation,
    stamp: ArenaSessionStamp,
    busy: Boolean,
    onEvent: (ArenaEntryEvent) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(16.dp).testTag("arena_preparation_" + row.requestId)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.arena_entry_request_id, row.requestId))
            Text(stringResource(row.reason.preparationResource()))
            Text(stringResource(R.string.arena_entry_retry_warning))
            EntryButton(
                R.string.arena_entry_retry_prepare,
                "arena_retry_prepare_" + row.requestId,
                !busy && row.canRetryPreparation(),
            ) { onEvent(ArenaEntryEvent.RetryPreparation(stamp, row.requestId)) }
        }
    }
}

@Composable
private fun ArenaTicketCard(
    row: ArenaLocalEntry,
    stamp: ArenaSessionStamp,
    busy: Boolean,
    callbacks: ArenaEntryCallbacks,
) {
    val onEvent = callbacks.onEvent
    Card(Modifier.fillMaxWidth().padding(16.dp).testTag("arena_ticket_" + row.ticketId)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.arena_entry_ticket_id, row.ticketId))
            row.header?.let {
                Text(stringResource(R.string.arena_entry_frozen_cost, it.priceNolics, it.lifeCost))
                Text(stringResource(R.string.arena_entry_request_id, it.requestId))
            }
            Text(stringResource(row.state.localStateResource()))
            val open = callbacks.onOpenRun
            if (open != null && row.canStartArenaRun()) {
                EntryButton(R.string.arena_entry_start_run, "arena_start_" + row.ticketId, !busy) {
                    open(stamp, row.ticketId, true)
                }
            }
            if (open != null && row.canReadArenaRun()) {
                EntryButton(R.string.arena_entry_open_run, "arena_open_" + row.ticketId, !busy) {
                    open(stamp, row.ticketId, false)
                }
            }
            if (row.canOfferCancellation()) {
                EntryButton(R.string.arena_entry_cancel, "arena_cancel_" + row.ticketId, !busy) {
                    onEvent(ArenaEntryEvent.ShowCancel(stamp, row.ticketId))
                }
            }
            for (action in row.availableEntryActions()) EntryButton(
                action.actionResource(),
                "arena_action_${action.name}_${row.ticketId}",
                !busy,
            ) {
                onEvent(ArenaEntryEvent.TicketAction(stamp, row.ticketId, action))
            }
        }
    }
}

@Composable
private fun EntryButton(
    label: Int,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().testTag(tag), enabled = enabled) { Text(stringResource(label)) }
}

@Preview(showBackground = true)
@Suppress("UnusedPrivateMember") // Android Studio invokes this preview.
@Composable
private fun EntryPreview() {
    SchoolQuizTheme { ArenaEntryHeader(ArenaEntryUiState(session = ArenaSessionStamp("preview", 1)), {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Suppress("UnusedPrivateMember") // Android Studio invokes this preview.
@Composable
private fun EntryDarkPreview() {
    SchoolQuizTheme { ArenaEntryHeader(ArenaEntryUiState(), {}) }
}
