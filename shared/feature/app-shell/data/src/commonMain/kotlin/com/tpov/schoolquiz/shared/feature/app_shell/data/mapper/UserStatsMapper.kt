package com.tpov.schoolquiz.shared.feature.app_shell.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats

fun UserStatsEntity.toDomain(): UserStats =
    UserStats(
        nickname = nickname,
        avatarUrl = avatarUrl,
        hasPremium = hasPremium,
        streakDays = streakDays,
        stars = stars,
        nolics = nolics,
        standardHearts = standardHearts,
        goldHearts = goldHearts,
        gold = gold,
        currentSkill = currentSkill,
        qualification = Qualification(
            tester = testerLevel,
            moderator = moderatorLevel,
            sponsor = sponsorLevel,
            translator = translatorLevel,
            admin = adminLevel,
            developer = developerLevel,
        ),
    )

fun RawUserStats.toEntity(uid: String): UserStatsEntity =
    UserStatsEntity(
        uid = uid,
        nickname = nickname,
        avatarUrl = avatarUrl,
        hasPremium = hasPremium,
        streakDays = streakDays,
        stars = stars,
        nolics = nolics,
        standardHearts = standardHearts,
        goldHearts = goldHearts,
        gold = gold,
        currentSkill = currentSkill,
        testerLevel = testerLevel,
        moderatorLevel = moderatorLevel,
        sponsorLevel = sponsorLevel,
        translatorLevel = translatorLevel,
        adminLevel = adminLevel,
        developerLevel = developerLevel,
    )
