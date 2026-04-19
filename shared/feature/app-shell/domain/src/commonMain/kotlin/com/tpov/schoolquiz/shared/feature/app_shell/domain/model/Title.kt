package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * User skill titles with corresponding skill-point ranges.
 *
 * Ranges are copied 1-to-1 from legacy TitleUserValue.kt.
 * [skillRange] defines the inclusive range of skill points for this title.
 * [first] is the minimum skill points required to reach this title — used as
 * the threshold value in DrawerSection.requiredRoles.
 */
enum class Title(val skillRange: IntRange) {
    RECRUIT(0..999),
    NOVICE(1_000..2_999),
    TEACHINGS(3_000..9_999),
    PLAYER(10_000..19_999),
    AMATEUR(20_000..49_999),
    SCRABBLE(50_000..99_999),
    JUNIOR(100_000..149_999),
    TEACHER(150_000..299_999),
    SENIOR(300_000..499_999),
    EXPERT(500_000..999_999),
    LEGEND(1_000_000..Int.MAX_VALUE),
    ;

    /**
     * The minimum skill points required to reach this title.
     * Equals the lower bound of [skillRange].
     */
    val first: Int get() = skillRange.first
}
