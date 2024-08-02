package com.tpov.setting.presentation

data class ProfileEditModel(
    val login: String?,
    val password: String?,
    val name: String?,
    val nickname: String?,
    val birthday: String?,
    val city: String?,
    val logo: Int?,
    val languages: String?,
)
