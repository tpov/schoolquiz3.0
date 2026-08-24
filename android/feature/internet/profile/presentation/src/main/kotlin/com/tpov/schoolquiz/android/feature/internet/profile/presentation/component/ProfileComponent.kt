package com.tpov.schoolquiz.android.feature.internet.profile.presentation.component

import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountChooserHost
import kotlinx.coroutines.flow.StateFlow

interface ProfileComponent {
    val state: StateFlow<ProfileUiState>

    /**
     * Attaches a Google identity to this account.
     *
     * Takes the host because the account chooser is a system sheet and needs a live screen to
     * appear over; nothing else about it belongs to the caller.
     */
    fun onLinkGoogle(host: AccountChooserHost)

    /**
     * The screen came back into view.
     *
     * Names can change from somewhere else — the shop sells and buys them — so what was fetched
     * when this component was built is not what the account holds by the time somebody returns.
     * Quiet on purpose: it says nothing on success, because arriving at a screen is not an action
     * that deserves a receipt.
     */
    fun onScreenShown()

    /** Opens the name field. Renaming is deliberate, so it takes a tap to get into. */
    fun onStartRename()

    /** Leaves the field and puts back the name that is actually in force. */
    fun onCancelRename()

    fun onNicknameChange(value: String)

    fun onSaveNickname()

    fun onRefresh()

    /** Wears one of the names already owned. */
    fun onSelectNickname(nickname: String)

    fun onMessageShown()
}
