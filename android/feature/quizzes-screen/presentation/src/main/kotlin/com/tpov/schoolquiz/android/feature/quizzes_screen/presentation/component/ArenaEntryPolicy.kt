package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingPreparation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailureKind

/** A known RESERVED header can be cancelled even if its cached question bank is missing/corrupt. */
internal fun ArenaLocalEntry.canOfferCancellation(): Boolean =
    header?.status == ArenaTicketStatus.RESERVED && localDecision == ArenaLocalDecision.NONE

internal fun ArenaPendingPreparation.canRetryPreparation(): Boolean =
    request != null && state != ArenaPreparationState.NEEDS_RECOVERY &&
        reason != ArenaPreparationReason.NEEDS_RECOVERY &&
        reason != ArenaPreparationReason.PRICE_CHANGED_REQUIRES_CONSENT

internal fun ArenaLocalEntry.availableEntryActions(): Set<ArenaTicketAction> =
    buildSet {
        add(ArenaTicketAction.RECOVER)
        if (localDecision == ArenaLocalDecision.CANCEL && state != ArenaLocalEntryState.TERMINAL_OBSERVED) {
            add(ArenaTicketAction.RETRY_CANCEL)
        }
        if (localDecision == ArenaLocalDecision.SETTLE) {
            if (header?.status == ArenaTicketStatus.CANCELLED) {
                add(ArenaTicketAction.RECONCILE_CANCELLED)
            } else {
                add(ArenaTicketAction.RESTORE_SETTLEMENT)
            }
        }
    }

internal fun entryFailureNotice(error: Exception): ArenaEntryNotice =
    when (error) {
        is ArenaEntryFailure ->
            when (error.kind) {
                ArenaEntryFailureKind.ACCOUNT_UNAVAILABLE -> ArenaEntryNotice.ACCOUNT_UNAVAILABLE
                ArenaEntryFailureKind.ACCOUNT_CHANGED -> ArenaEntryNotice.ACCOUNT_CHANGED
                ArenaEntryFailureKind.NO_NETWORK -> ArenaEntryNotice.OFFLINE
                ArenaEntryFailureKind.NEEDS_RECOVERY, ArenaEntryFailureKind.CONFLICT,
                ArenaEntryFailureKind.INVALID_REQUEST,
                -> ArenaEntryNotice.NEEDS_RECOVERY
                ArenaEntryFailureKind.UNAVAILABLE -> ArenaEntryNotice.UNAVAILABLE
            }
        is ArenaWalletFailure ->
            when (error.kind) {
                ArenaWalletFailureKind.ACCOUNT_UNAVAILABLE -> ArenaEntryNotice.ACCOUNT_UNAVAILABLE
                ArenaWalletFailureKind.ACCOUNT_CHANGED -> ArenaEntryNotice.ACCOUNT_CHANGED
                ArenaWalletFailureKind.NO_NETWORK -> ArenaEntryNotice.OFFLINE
                else -> ArenaEntryNotice.WALLET_UNKNOWN
            }
        else -> ArenaEntryNotice.UNAVAILABLE
    }
