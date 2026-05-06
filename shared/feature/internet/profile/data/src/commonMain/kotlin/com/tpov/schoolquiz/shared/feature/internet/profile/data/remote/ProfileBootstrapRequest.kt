package com.tpov.schoolquiz.shared.feature.internet.profile.data.remote

data class ProfileBootstrapRequest(
    val uid: String,
    val nickname: String,
    val knownLanguages: List<String>,
)
