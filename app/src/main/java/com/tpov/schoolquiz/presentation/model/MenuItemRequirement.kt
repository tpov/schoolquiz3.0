package com.tpov.schoolquiz.presentation.model

data class MenuItemRequirement(
    val id: Int,
    val titleRes: Int,
    val iconRes: Int,
    val inset: Inset,
    val requiredRoles: Map<Role, Int>,
    val requiredSkill: Int
)


enum class Role {
    TESTER,
    MODERATOR,
    SPONSOR,
    TRANSLATOR,
    ADMIN,
    DEVELOPER
}
enum class Inset {
    HOME,
    EVENT,
    NETWORK
}
