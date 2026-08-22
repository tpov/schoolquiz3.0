package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val nickname: String,
    val status: String,
    val avatarUrl: String?,
    val knownLanguages: List<String>,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val skillPoints: Int,
    val gold: Long,
    val nolics: Long,
    val standardHearts: Int,
    val goldHearts: Int,
    val sponsorLevel: Int,
    val testerLevel: Int,
    val translatorLevel: Int,
    val moderatorLevel: Int,
    val adminLevel: Int,
    val developerLevel: Int,
    val boxCount: Int,
    val boxStreakDays: Int,
    val nextBoxAtMs: Long,
    val premiumUntilMs: Long,
    val trophies: List<String>,
    val ownedLogos: List<String>,
    val lifePoints: Int = 0,
    val lifePointsUpdatedAtMs: Long = 0,
)
