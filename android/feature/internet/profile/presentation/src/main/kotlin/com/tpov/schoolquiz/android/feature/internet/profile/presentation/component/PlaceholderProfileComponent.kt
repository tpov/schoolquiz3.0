package com.tpov.schoolquiz.android.feature.internet.profile.presentation.component

import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaceholderProfileComponent : ProfileComponent {
    override val state: StateFlow<ProfileUiState> = MutableStateFlow(ProfileUiState())

    override fun onNicknameChange(value: String) = Unit

    override fun onSaveNickname() = Unit

    override fun onRefresh() = Unit

    override fun onMessageShown() = Unit
}
