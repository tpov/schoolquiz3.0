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
        // An empty set is read as "not told", not as "owns nothing". A shop purchase answers with
        // a balance, and any server older than the one that put the unlocks into that balance
        // answers without them — taking that literally shuts lessons the player has paid for.
        // Nothing is lost by keeping what we hold: unlocks are only ever added, and the profile
        // sync replaces the set outright when it genuinely changes.
        lessonUnlocks = balance.lessonUnlocks.ifEmpty { lessonUnlocks },
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
