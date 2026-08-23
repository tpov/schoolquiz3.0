package com.tpov.schoolquiz.shared.feature.internet.profile.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.UserProfileEntity
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile

fun UserProfileEntity.toDomain(): UserProfile =
    UserProfile(
        uid = uid,
        nickname = nickname,
        status = status.toProfileStatus(),
        avatarUrl = avatarUrl,
        knownLanguages = knownLanguages,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        skillPoints = skillPoints,
        gold = gold,
        nolics = nolics,
        standardHearts = standardHearts,
        goldHearts = goldHearts,
        qualification =
            ProfileQualification(
                sponsorLevel = sponsorLevel,
                testerLevel = testerLevel,
                translatorLevel = translatorLevel,
                moderatorLevel = moderatorLevel,
                adminLevel = adminLevel,
                developerLevel = developerLevel,
            ),
        boxCount = boxCount,
        boxStreakDays = boxStreakDays,
        nextBoxAtMs = nextBoxAtMs,
        premiumUntilMs = premiumUntilMs,
        trophies = trophies.toSet(),
        ownedLogos = ownedLogos,
        lifePoints = lifePoints,
        lifePointsUpdatedAtMs = lifePointsUpdatedAtMs,
        realName = realName,
        birthday = birthday,
        city = city,
        telegram = telegram,
        verifiedAtMs = verifiedAtMs,
    )

fun UserProfile.toEntity(): UserProfileEntity =
    UserProfileEntity(
        uid = uid,
        nickname = nickname,
        status = status.name,
        avatarUrl = avatarUrl,
        knownLanguages = knownLanguages,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        skillPoints = skillPoints,
        gold = gold,
        nolics = nolics,
        standardHearts = standardHearts,
        goldHearts = goldHearts,
        sponsorLevel = qualification.sponsorLevel,
        testerLevel = qualification.testerLevel,
        translatorLevel = qualification.translatorLevel,
        moderatorLevel = qualification.moderatorLevel,
        adminLevel = qualification.adminLevel,
        developerLevel = qualification.developerLevel,
        boxCount = boxCount,
        boxStreakDays = boxStreakDays,
        nextBoxAtMs = nextBoxAtMs,
        premiumUntilMs = premiumUntilMs,
        trophies = trophies.sorted(),
        ownedLogos = ownedLogos,
        lifePoints = lifePoints,
        lifePointsUpdatedAtMs = lifePointsUpdatedAtMs,
        realName = realName,
        birthday = birthday,
        city = city,
        telegram = telegram,
        verifiedAtMs = verifiedAtMs,
    )

private fun String.toProfileStatus(): ProfileStatus =
    ProfileStatus.entries.firstOrNull { it.name == this } ?: ProfileStatus.ANONYMOUS
