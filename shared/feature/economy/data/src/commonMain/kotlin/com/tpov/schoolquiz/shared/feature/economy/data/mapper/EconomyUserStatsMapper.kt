package com.tpov.schoolquiz.shared.feature.economy.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance

fun UserStatsEntity.toBalance(): EconomyResourceBalance =
    EconomyResourceBalance(
        hasPremium = hasPremium,
        streakDays = streakDays.coerceAtLeast(0),
        stars = stars.coerceAtLeast(0L),
        nolics = nolics.coerceAtLeast(0L),
        standardHearts = standardHearts.coerceIn(0, EconomyResourceBalance.MaxStandardHearts),
        goldHearts = goldHearts.coerceIn(0, EconomyResourceBalance.MaxGoldHearts),
        gold = gold.coerceAtLeast(0L),
        lessonUnlocks = lessonUnlocks,
    )

fun UserStatsEntity.mergeWithBalance(balance: EconomyResourceBalance): UserStatsEntity =
    copy(
        hasPremium = balance.hasPremium,
        streakDays = balance.streakDays.coerceAtLeast(0),
        stars = balance.stars.coerceAtLeast(0L),
        nolics = balance.nolics.coerceAtLeast(0L),
        standardHearts = balance.standardHearts.coerceIn(0, EconomyResourceBalance.MaxStandardHearts),
        goldHearts = balance.goldHearts.coerceIn(0, EconomyResourceBalance.MaxGoldHearts),
        gold = balance.gold.coerceAtLeast(0L),
        // Union, never replace. Unlocks are monotone on both sides — the server adds with
        // arrayUnion and nothing ever un-buys one — so the two sets can only be behind, never
        // wrong. Replacing loses whichever response happens to arrive last: two purchases in
        // flight answer {L1} and {L1,L2}, and the older answer would shut the lesson just paid for.
        lessonUnlocks = lessonUnlocks + balance.lessonUnlocks,
    )

fun EconomyResourceBalance.toNewUserStatsEntity(uid: String): UserStatsEntity =
    UserStatsEntity(
        uid = uid,
        nickname = "",
        avatarUrl = null,
        hasPremium = hasPremium,
        streakDays = streakDays.coerceAtLeast(0),
        stars = stars.coerceAtLeast(0L),
        nolics = nolics.coerceAtLeast(0L),
        standardHearts = standardHearts.coerceIn(0, EconomyResourceBalance.MaxStandardHearts),
        goldHearts = goldHearts.coerceIn(0, EconomyResourceBalance.MaxGoldHearts),
        gold = gold.coerceAtLeast(0L),
        currentSkill = 0,
        testerLevel = 0,
        moderatorLevel = 0,
        sponsorLevel = 0,
        translatorLevel = 0,
        adminLevel = 0,
        developerLevel = 0,
        lessonUnlocks = lessonUnlocks,
    )
