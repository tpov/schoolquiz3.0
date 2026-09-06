package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * User statistics for the drawer header and progressive section unlock.
 *
 * Domain rule: pure data class; no DI, no Flow, no serialization annotations.
 *
 * [currentSkill] maps to Role.USER in the visibility guard (actualLevel function).
 * [qualification] maps to all other Roles (TESTER, MODERATOR, SPONSOR, TRANSLATOR, ADMIN, DEVELOPER).
 */
data class UserStats(
    val nickname: String,
    val avatarUrl: String?,
    val hasPremium: Boolean,
    // 0..10
    val streakDays: Int,
    val stars: Long,
    val nolics: Long,
    // 0..5
    val standardHearts: Int,
    // 0..1
    val goldHearts: Int,
    val gold: Long,
    // >= 0, quiz experience points (Role.USER level)
    val currentSkill: Int,
    val qualification: Qualification,
) {
    init {
        require(currentSkill >= 0) { "UserStats.currentSkill must be >= 0, was $currentSkill" }
    }

    companion object {
        /**
         * Factory for the guest/unauthenticated state.
         * Domain test scenario 14 verifies all field values.
         */
        fun guest(): UserStats =
            UserStats(
                nickname = "Гость",
                avatarUrl = null,
                hasPremium = false,
                streakDays = 0,
                stars = 0L,
                nolics = 0L,
                standardHearts = 5,
                goldHearts = 0,
                gold = 0L,
                currentSkill = 0,
                qualification = Qualification.zero(),
            )
    }
}
