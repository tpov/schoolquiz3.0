package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey val uid: String,
    val nickname: String,
    val avatarUrl: String?,
    val hasPremium: Boolean,
    val streakDays: Int,
    val stars: Long,
    val nolics: Long,
    val standardHearts: Int,
    val goldHearts: Int,
    val gold: Long,
    val currentSkill: Int,
    val testerLevel: Int,
    val moderatorLevel: Int,
    val sponsorLevel: Int,
    val translatorLevel: Int,
    val adminLevel: Int,
    val developerLevel: Int,
    /** Lessons opened with nolics, as "kind:lessonId". Rides the profile sync like the balance. */
    val lessonUnlocks: Set<String> = emptySet(),
)
