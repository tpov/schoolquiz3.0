package com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.validateProfileNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ACTIVITY_WINDOW_DAYS

data class ProfileUiState(
    val profile: UserProfile = UserProfile.offline(),
    val nicknameInput: String = profile.nickname,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    /**
     * Lessons finished on each of the last [ACTIVITY_WINDOW_DAYS] days, oldest first.
     *
     * Held as a plain list of counts because that is all the chart needs; the dates are implied by
     * the positions, and carrying them would invite the two to drift apart.
     */
    val dailyActivity: List<Int> = List(ACTIVITY_WINDOW_DAYS) { 0 },
    /** Whether the player is in the middle of typing a new name. */
    val isEditingNickname: Boolean = false,
    /**
     * Every name this account holds, active one included.
     *
     * Owning names is part of the profile even though they are traded in the shop — the profile is
     * where you look to see what you have and to change which one you wear.
     */
    val ownedNicknames: List<OwnedNickname> = emptyList(),
    val isLoadingNicknames: Boolean = false,
    /** Set when the list could not be fetched, so an empty shelf is not read as "you own none". */
    val nicknamesUnreachable: Boolean = false,
    /** The name a switch is in flight for, so only that row shows as busy. */
    val switchingNickname: String? = null,
    /** True while the Google account sheet is up or the link is in flight. */
    val isLinkingGoogle: Boolean = false,
) {
    /**
     * Whether upgrading the account is on offer.
     *
     * Only an anonymous account has anything to gain: a registered one is already linked, and an
     * offline one has no account to link to.
     */
    val canLinkGoogle: Boolean
        get() = profile.status == ProfileStatus.ANONYMOUS && !isLinkingGoogle

    val canEditNickname: Boolean
        get() = profile.status != ProfileStatus.VALIDATED && profile.status != ProfileStatus.OFFLINE

    /** True once there is any play to draw — an all-zero fortnight gets a line, not a chart. */
    val hasActivity: Boolean
        get() = dailyActivity.any { it > 0 }

    val canSaveNickname: Boolean
        get() =
            canEditNickname &&
                !isSaving &&
                validateProfileNickname(nicknameInput).isSuccess &&
                nicknameInput.trim() != profile.nickname
}
