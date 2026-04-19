package com.tpov.schoolquiz.shared.core.stats

data class RawUserStats(
    val nickname: String = "",
    val avatarUrl: String? = null,
    val hasPremium: Boolean = false,
    val streakDays: Int = 0,
    val stars: Long = 0L,
    val nolics: Long = 0L,
    val standardHearts: Int = 0,
    val goldHearts: Int = 0,
    val gold: Long = 0L,
    val currentSkill: Int = 0,
    val testerLevel: Int = 0,
    val moderatorLevel: Int = 0,
    val sponsorLevel: Int = 0,
    val translatorLevel: Int = 0,
    val adminLevel: Int = 0,
    val developerLevel: Int = 0,
)
