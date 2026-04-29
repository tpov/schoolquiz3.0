package com.tpov.schoolquiz.shared.core.leaderboard

import kotlinx.serialization.Serializable

@Serializable
data class TopParticipant(
    val nickname: String,
    val avatarUrl: String?,
    val percent: Int,
)
