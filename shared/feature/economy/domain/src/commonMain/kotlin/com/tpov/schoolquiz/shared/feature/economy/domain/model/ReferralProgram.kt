package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class ReferralProgram(
    val link: String,
    val invitedUsers: List<ReferralUser>,
) {
    val allOpenedBoxes: Int get() = invitedUsers.sumOf { it.allOpenedBoxes }
    val seasonBoxes: Int get() = invitedUsers.sumOf { it.seasonBoxes }
}

data class ReferralUser(
    val id: String,
    val nickname: String,
    val allOpenedBoxes: Int,
    val seasonBoxes: Int,
)
