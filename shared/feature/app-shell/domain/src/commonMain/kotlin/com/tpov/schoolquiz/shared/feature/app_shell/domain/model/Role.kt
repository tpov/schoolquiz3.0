package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * User roles for progressive section unlock.
 *
 * - USER: maps to UserStats.currentSkill (quiz experience points).
 * - All other roles: map to the corresponding field in UserStats.qualification.
 *
 * Domain rule (FR #20): Role.USER is the 7th "role" representing the user's skill level;
 * the other 6 are administrative qualification roles.
 */
enum class Role {
    USER,
    TESTER,
    MODERATOR,
    SPONSOR,
    TRANSLATOR,
    ADMIN,
    DEVELOPER,
}
