package com.tpov.schoolquiz.presentation.core

object TitleUserValue {
    const val COUNT_SKILL_IN_VISIBLE_POINT = 1000

    val skillTitleCount = mapOf(
        Title.RECRUIT to (0..999),
        Title.NOVICE to (1000..2999),
        Title.TEACHINGS to (3000..9999),
        Title.PLAYER to (10_000..19_999),
        Title.AMATEUR to (20_000..49_999),
        Title.SCRABBLE to (50_000..99_999),
        Title.JUNIOR to (100_000..149_999),
        Title.TEACHER to (150_000..299_999),
        Title.SENIOR to (300_000..499_999),
        Title.EXPERT to (500_000..999_999),
        Title.LEGEND to (1_000_000..Int.MAX_VALUE)
    )
    val cvalificationTitleCount = mapOf(
        Cvalification.TESTER_1 to (0..999)
    )
}

enum class Title {
    RECRUIT,
    NOVICE,
    TEACHINGS,
    PLAYER,
    AMATEUR,
    SCRABBLE,
    JUNIOR,
    TEACHER,
    SENIOR,
    EXPERT,
    LEGEND
}

enum class Cvalification {
    TESTER_1
}


