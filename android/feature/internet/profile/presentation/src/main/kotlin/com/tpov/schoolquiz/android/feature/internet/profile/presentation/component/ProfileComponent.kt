package com.tpov.schoolquiz.android.feature.internet.profile.presentation.component

import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import kotlinx.coroutines.flow.StateFlow

interface ProfileComponent {
    val state: StateFlow<ProfileUiState>

    fun onNicknameChange(value: String)

    fun onSaveNickname()

    fun onRefresh()

    fun onMessageShown()
}
