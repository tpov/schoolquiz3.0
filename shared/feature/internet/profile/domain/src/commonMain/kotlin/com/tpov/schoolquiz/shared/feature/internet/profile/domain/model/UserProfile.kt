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
    /** Named badges the player holds; see TROPHY_VERIFIED in functions/trophies.js. */
    val trophies: Set<String> = emptySet(),
    val ownedLogos: List<String> = emptyList(),
    /**
     * Activity budget. A heart is a slot worth [LIFE_POINTS_PER_HEART] points, and playing costs
     * points rather than whole hearts — the model inherited from the legacy app. The server is the
     * authority: it regenerates and charges these points, the client only mirrors them.
     */
    val lifePoints: Int = standardHearts * LIFE_POINTS_PER_HEART,
    val lifePointsUpdatedAtMs: Long = 0L,
) {
    /** Ceiling: every owned heart slot holds [LIFE_POINTS_PER_HEART] points. */
    val maxLifePoints: Int get() = standardHearts * LIFE_POINTS_PER_HEART

    /** Whether the player can still pay for a lesson attempt. */
    val canAffordLessonAttempt: Boolean get() = lifePoints >= LESSON_ATTEMPT_LIFE_COST

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
        require(lifePoints >= 0) { "lifePoints must be non-negative" }
        require(lifePointsUpdatedAtMs >= 0) { "lifePointsUpdatedAtMs must be non-negative" }
    }

    companion object {
        /** One heart holds this many life points. Mirrors LIFE_POINTS_PER_HEART in functions/life-points.js. */
        const val LIFE_POINTS_PER_HEART = 100

        /** Cost of one lesson attempt, from the legacy price list. */
        const val LESSON_ATTEMPT_LIFE_COST = 33

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
