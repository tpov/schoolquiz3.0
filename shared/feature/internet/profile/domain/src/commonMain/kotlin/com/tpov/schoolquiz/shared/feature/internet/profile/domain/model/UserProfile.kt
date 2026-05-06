package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

data class UserProfile(
    val uid: String,
    val nickname: String,
    val status: ProfileStatus,
    val avatarUrl: String?,
    val knownLanguages: List<String>,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val skillPoints: Int,
    val gold: Long,
    val nolics: Long,
    val standardHearts: Int,
    val goldHearts: Int,
    val qualification: ProfileQualification,
    val boxCount: Int = 0,
    val boxStreakDays: Int = 0,
    val nextBoxAtMs: Long = 0L,
    val premiumUntilMs: Long = 0L,
    val trophies: Long = 0L,
    val ownedLogos: List<String> = emptyList(),
) {
    init {
        require(uid.isNotBlank() || status == ProfileStatus.OFFLINE) {
            "uid must be present for online profiles"
        }
        require(nickname.isNotBlank()) { "nickname must not be blank" }
        require(skillPoints >= 0) { "skillPoints must be non-negative" }
        require(gold >= 0) { "gold must be non-negative" }
        require(nolics >= 0) { "nolics must be non-negative" }
        require(standardHearts >= 0) { "standardHearts must be non-negative" }
        require(goldHearts >= 0) { "goldHearts must be non-negative" }
        require(boxCount >= 0) { "boxCount must be non-negative" }
        require(boxStreakDays >= 0) { "boxStreakDays must be non-negative" }
        require(nextBoxAtMs >= 0) { "nextBoxAtMs must be non-negative" }
        require(premiumUntilMs >= 0) { "premiumUntilMs must be non-negative" }
        require(trophies >= 0) { "trophies must be non-negative" }
    }

    companion object {
        fun offline(): UserProfile =
            UserProfile(
                uid = "",
                nickname = "Гость",
                status = ProfileStatus.OFFLINE,
                avatarUrl = null,
                knownLanguages = emptyList(),
                createdAtMs = 0L,
                updatedAtMs = 0L,
                skillPoints = 0,
                gold = 0L,
                nolics = 0L,
                standardHearts = 5,
                goldHearts = 0,
                qualification = ProfileQualification(),
            )
    }
}
