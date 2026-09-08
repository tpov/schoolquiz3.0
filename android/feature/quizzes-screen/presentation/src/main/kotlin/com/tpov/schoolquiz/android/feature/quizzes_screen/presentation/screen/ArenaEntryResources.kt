package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryNotice
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaTicketAction
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationReason

internal fun ArenaEntryNotice.entryResource(): Int =
    when (this) {
        ArenaEntryNotice.NOT_LOADED -> R.string.arena_entry_not_loaded
        ArenaEntryNotice.ACCOUNT_UNAVAILABLE -> R.string.arena_entry_account_unavailable
        ArenaEntryNotice.ACCOUNT_CHANGED -> R.string.arena_entry_account_changed
        ArenaEntryNotice.READY -> R.string.arena_entry_ready
        ArenaEntryNotice.REQUEST_RECORDED -> R.string.arena_entry_recorded
        ArenaEntryNotice.RETRY_SAME_REQUEST -> R.string.arena_entry_retry_warning
        ArenaEntryNotice.PRICE_CHANGED -> R.string.arena_entry_price_changed
        ArenaEntryNotice.OFFLINE -> R.string.arena_entry_offline
        ArenaEntryNotice.NEEDS_RECOVERY -> R.string.arena_entry_needs_recovery
        ArenaEntryNotice.UNAVAILABLE -> R.string.arena_entry_unavailable
        ArenaEntryNotice.CANCELLED_RESOLVED -> R.string.arena_entry_cancelled_resolved
        ArenaEntryNotice.STILL_RESERVED -> R.string.arena_entry_still_reserved
        ArenaEntryNotice.SETTLED_OBSERVED -> R.string.arena_entry_settled_observed
        ArenaEntryNotice.RESTORED -> R.string.arena_entry_restored
        ArenaEntryNotice.WALLET_UNKNOWN -> R.string.arena_entry_wallet_unknown
    }

internal fun ArenaPreparationReason.preparationResource(): Int =
    when (this) {
        ArenaPreparationReason.RETRY_SAME_REQUEST -> R.string.arena_entry_retry_warning
        ArenaPreparationReason.PRICE_CHANGED_REQUIRES_CONSENT -> R.string.arena_entry_price_changed
        ArenaPreparationReason.OTHER_ATTENTION -> R.string.arena_entry_unavailable
        ArenaPreparationReason.NEEDS_RECOVERY -> R.string.arena_entry_needs_recovery
    }

internal fun ArenaLocalEntryState.localStateResource(): Int =
    when (this) {
        ArenaLocalEntryState.PREPARED_SNAPSHOT -> R.string.arena_entry_prepared
        ArenaLocalEntryState.RESUME_OR_RECONCILE -> R.string.arena_entry_resume
        ArenaLocalEntryState.CANCEL_PENDING -> R.string.arena_entry_cancel_pending
        ArenaLocalEntryState.SETTLEMENT_PENDING -> R.string.arena_entry_settlement_pending
        ArenaLocalEntryState.TERMINAL_OBSERVED -> R.string.arena_entry_terminal
        ArenaLocalEntryState.NEEDS_RECOVERY -> R.string.arena_entry_needs_recovery
    }

internal fun ArenaTicketAction.actionResource(): Int =
    when (this) {
        ArenaTicketAction.RECOVER -> R.string.arena_entry_recover_ticket
        ArenaTicketAction.RETRY_CANCEL -> R.string.arena_entry_retry_cancel
        ArenaTicketAction.RECONCILE_CANCELLED -> R.string.arena_entry_reconcile_cancelled
        ArenaTicketAction.RESTORE_SETTLEMENT -> R.string.arena_entry_restore_settlement
    }
