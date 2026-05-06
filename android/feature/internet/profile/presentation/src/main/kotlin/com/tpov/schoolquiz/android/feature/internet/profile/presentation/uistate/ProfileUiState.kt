package com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.validateProfileNickname

data class ProfileUiState(
    val profile: UserProfile = UserProfile.offline(),
    val nicknameInput: String = profile.nickname,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val canEditNickname: Boolean
        get() = profile.status != ProfileStatus.VALIDATED && profile.status != ProfileStatus.OFFLINE

    val canSaveNickname: Boolean
        get() =
            canEditNickname &&
                !isSaving &&
                validateProfileNickname(nicknameInput).isSuccess &&
                nicknameInput.trim() != profile.nickname
}
