package com.tpov.schoolquiz.android.feature.internet.profile.presentation.component

import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountChooserHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaceholderProfileComponent : ProfileComponent {
    override val state: StateFlow<ProfileUiState> = MutableStateFlow(ProfileUiState())

    override fun onLinkGoogle(host: AccountChooserHost) = Unit

    override fun onScreenShown() = Unit

    override fun onStartRename() = Unit

    override fun onCancelRename() = Unit

    override fun onNicknameChange(value: String) = Unit

    override fun onSaveNickname() = Unit

    override fun onRefresh() = Unit

    override fun onSelectNickname(nickname: String) = Unit

    override fun onMessageShown() = Unit
}
