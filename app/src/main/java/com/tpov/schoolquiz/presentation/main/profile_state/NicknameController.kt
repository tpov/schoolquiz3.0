package com.tpov.schoolquiz.presentation.main.profile_state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class NicknameController {

    private val _nicknameState = MutableStateFlow("")
    val nicknameState = _nicknameState.asStateFlow()

    fun setNickname(
       nick: String,
       thropy: String
    ) {
        _nicknameState.value = "$nick: $thropy"
    }

}
