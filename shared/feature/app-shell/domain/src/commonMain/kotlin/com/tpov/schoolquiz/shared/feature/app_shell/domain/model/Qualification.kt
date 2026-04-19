package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Administrative qualification levels for the 6 non-USER roles.
 *
 * Each field corresponds to a [Role] value (excluding Role.USER which maps to UserStats.currentSkill).
 * All fields must be >= 0.
 *
 * Domain rule: qualification levels are non-negative integers.
 * Legacy qualification threshold: 100 (literal from legacy MenuList.kt).
 */
data class Qualification(
    val tester: Int,
    val moderator: Int,
    val sponsor: Int,
    val translator: Int,
    val admin: Int,
    val developer: Int,
) {
    init {
        require(tester >= 0) { "Qualification.tester must be >= 0, was $tester" }
        require(moderator >= 0) { "Qualification.moderator must be >= 0, was $moderator" }
        require(sponsor >= 0) { "Qualification.sponsor must be >= 0, was $sponsor" }
        require(translator >= 0) { "Qualification.translator must be >= 0, was $translator" }
        require(admin >= 0) { "Qualification.admin must be >= 0, was $admin" }
        require(developer >= 0) { "Qualification.developer must be >= 0, was $developer" }
    }

    companion object {
        /**
         * All qualification levels at zero (new user / guest).
         */
        fun zero(): Qualification = Qualification(
            tester = 0,
            moderator = 0,
            sponsor = 0,
            translator = 0,
            admin = 0,
            developer = 0,
        )
    }
}
