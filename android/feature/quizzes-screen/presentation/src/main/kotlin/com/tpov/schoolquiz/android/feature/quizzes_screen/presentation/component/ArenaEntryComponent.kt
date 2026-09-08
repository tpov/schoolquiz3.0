package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingPreparation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.flow.StateFlow

interface ArenaEntryComponent {
    val state: StateFlow<ArenaEntryUiState>

    fun onEvent(event: ArenaEntryEvent)
}

fun interface ArenaEntryComponentFactory {
    fun create(context: ComponentContext): ArenaEntryComponent
}

data class ArenaEntryUiState(
    val session: ArenaSessionStamp? = null,
    val tickets: List<ArenaLocalEntry> = emptyList(),
    val preparations: List<ArenaPendingPreparation> = emptyList(),
    val wallet: ArenaWalletObservation? = null,
    val canLoadTickets: Boolean = false,
    val canLoadPreparations: Boolean = false,
    val canRecoverMore: Boolean = false,
    val hasLoadedTickets: Boolean = false,
    val hasLoadedPreparations: Boolean = false,
    val busy: Boolean = false,
    val dialog: ArenaEntryDialog? = null,
    val notice: ArenaEntryNotice = ArenaEntryNotice.NOT_LOADED,
    val lastRequestId: String? = null,
)

/** Frozen consent, never linked to a subsequently refreshed catalogue. Not persisted as authority. */
sealed interface ArenaEntryDialog {
    val id: String

    data class Prepare(
        override val id: String,
        val lesson: ArenaLessonCatalogCard?,
        val difficulty: Difficulty = Difficulty.EASY,
    ) : ArenaEntryDialog

    data class Cancel(override val id: String, val ticket: ArenaLocalEntry) : ArenaEntryDialog
}

enum class ArenaEntryNotice {
    NOT_LOADED,
    ACCOUNT_UNAVAILABLE,
    ACCOUNT_CHANGED,
    READY,
    REQUEST_RECORDED,
    RETRY_SAME_REQUEST,
    PRICE_CHANGED,
    OFFLINE,
    NEEDS_RECOVERY,
    UNAVAILABLE,
    CANCELLED_RESOLVED,
    STILL_RESERVED,
    SETTLED_OBSERVED,
    RESTORED,
    WALLET_UNKNOWN,
}

enum class ArenaEntryPage { TICKETS, PREPARATIONS, RECOVERY, WALLET }

enum class ArenaTicketAction { RECOVER, RETRY_CANCEL, RECONCILE_CANCELLED, RESTORE_SETTLEMENT }

/** Every screen callback retains its original epoch. A stale card cannot borrow a new account. */
sealed interface ArenaEntryEvent {
    val session: ArenaSessionStamp

    data class Load(
        override val session: ArenaSessionStamp,
        val page: ArenaEntryPage,
        val append: Boolean = false,
    ) : ArenaEntryEvent

    data class ShowPrepare(
        override val session: ArenaSessionStamp,
        val lesson: ArenaLessonCatalogCard?,
    ) : ArenaEntryEvent

    data class ShowCancel(override val session: ArenaSessionStamp, val ticketId: String) : ArenaEntryEvent

    data class ChangeDifficulty(
        override val session: ArenaSessionStamp,
        val dialogId: String,
        val difficulty: Difficulty,
    ) : ArenaEntryEvent

    data class Confirm(override val session: ArenaSessionStamp, val dialogId: String) : ArenaEntryEvent

    data class Dismiss(override val session: ArenaSessionStamp, val dialogId: String) : ArenaEntryEvent

    data class RetryPreparation(override val session: ArenaSessionStamp, val requestId: String) : ArenaEntryEvent

    data class TicketAction(
        override val session: ArenaSessionStamp,
        val ticketId: String,
        val action: ArenaTicketAction,
    ) : ArenaEntryEvent
}
