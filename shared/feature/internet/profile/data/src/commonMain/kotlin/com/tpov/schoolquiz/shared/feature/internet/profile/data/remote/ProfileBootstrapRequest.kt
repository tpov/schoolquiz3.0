package com.tpov.schoolquiz.shared.feature.internet.profile.data.remote

data class ProfileBootstrapRequest(
    val uid: String,
    val nickname: String,
    val knownLanguages: List<String>,
    /**
     * Код пригласившего (история 5.3): сервер читает поле только при создании аккаунта.
     * null — ссылка не приходила или уже потрачена.
     */
    val referredByUid: String? = null,
)
