package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Заглушки маршрутов соревнования: состояние «слой не привязан» (runCatching -> Placeholder,
 * образец quest-authoring) — экран честно молчит, вместо того чтобы притворяться живым слоем.
 */
class PlaceholderTournamentOrganizerComponent : TournamentOrganizerComponent {
    private val mutable = MutableStateFlow(OrganizerUiState())
    override val state: StateFlow<OrganizerUiState> = mutable.asStateFlow()

    override fun onSourceIdChange(value: String) = Unit

    override fun onPrepareClick() = Unit

    override fun onShowConsentClick() = Unit

    override fun onDismissConsent(dialogId: String) = Unit

    override fun onConfirmCreate(dialogId: String) = Unit

    override fun onRetryCreateClick() = Unit

    override fun onNewCreateClick() = Unit

    override fun onRefreshClick() = Unit
}

class PlaceholderTournamentBrowserComponent : TournamentBrowserComponent {
    private val mutable = MutableStateFlow(BrowserUiState())
    override val state: StateFlow<BrowserUiState> = mutable.asStateFlow()

    override fun onRefreshClick() = Unit

    override fun onLoadMoreClick() = Unit

    override fun onOpenEvent(eventId: String) = Unit

    override fun onCloseEvent() = Unit
}

class PlaceholderTournamentAdmissionComponent : TournamentAdmissionComponent {
    private val mutable = MutableStateFlow(AdmissionUiState())
    override val state: StateFlow<AdmissionUiState> = mutable.asStateFlow()

    override fun onOpenEvent(
        event: com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView,
        serverNowMs: Long,
    ) = Unit

    override fun onClose() = Unit

    override fun onRecordDownloadClick() = Unit

    override fun onContinueClick() = Unit
}

class PlaceholderTournamentQualificationComponent : TournamentQualificationComponent {
    private val mutable = MutableStateFlow(QualificationUiState())
    override val state: StateFlow<QualificationUiState> = mutable.asStateFlow()

    override fun onRefreshEventsClick() = Unit

    override fun onLoadMoreEventsClick() = Unit

    override fun onPickEvent(eventId: String) = Unit

    override fun onCloseEvent() = Unit

    override fun onLinkTokenChange(value: String) = Unit

    override fun onStartRoundClick(roundNumber: Int) = Unit

    override fun onToggleChoice(optionId: String) = Unit

    override fun onOrderTap(optionId: String) = Unit

    override fun onBlankChange(
        index: Int,
        value: String,
    ) = Unit

    override fun onSubmitAnswerClick() = Unit

    override fun onRecheckClick() = Unit
}

class PlaceholderTournamentAgreementComponent : TournamentAgreementComponent {
    private val mutable = MutableStateFlow(AgreementUiState())
    override val state: StateFlow<AgreementUiState> = mutable.asStateFlow()

    override fun onOpenMatch(
        eventId: String,
        matchId: String,
    ) = false

    override fun onCloseMatch() = Unit

    override fun onLoadMoreClick() = Unit

    override fun onSlotStartsAtChange(value: String) = Unit

    override fun onProposeClick() = Unit

    override fun onConfirmClick(slotStartsAtMs: Long) = Unit

    override fun onReadyClick() = Unit
}

class PlaceholderTournamentMatchPlayComponent : TournamentMatchPlayComponent {
    private val mutable = MutableStateFlow(MatchPlayUiState())
    override val state: StateFlow<MatchPlayUiState> = mutable.asStateFlow()

    override fun onOpenMatchSession(
        eventId: String,
        matchId: String,
        sessionId: String,
    ) = false

    override fun onCloseMatchSession() = Unit

    override fun onToggleChoice(optionId: String) = Unit

    override fun onOrderTap(optionId: String) = Unit

    override fun onBlankChange(
        index: Int,
        value: String,
    ) = Unit

    override fun onSubmitAnswerClick() = Unit

    override fun onRecheckClick() = Unit
}

class PlaceholderTournamentBracketComponent : TournamentBracketComponent {
    private val mutable = MutableStateFlow(BracketUiState())
    override val state: StateFlow<BracketUiState> = mutable.asStateFlow()

    override fun onRefreshClick() = Unit

    override fun onLoadMoreClick() = Unit

    override fun onOpenEvent(eventId: String) = Unit

    override fun onCloseEvent() = Unit
}

class PlaceholderTournamentResultsComponent : TournamentResultsComponent {
    private val mutable = MutableStateFlow(ResultsUiState())
    override val state: StateFlow<ResultsUiState> = mutable.asStateFlow()

    override fun onOpenEvent(eventId: String) = false

    override fun onClose() = Unit
}
