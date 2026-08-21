package com.tpov.schoolquiz.platform.firebase.profile

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileBootstrapRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlinx.coroutines.tasks.await

class FirebaseProfileRemoteDataSource(
    private val functions: FirebaseFunctions,
) : ProfileRemoteDataSource {
    override suspend fun ensureProfile(request: ProfileBootstrapRequest): UserProfile {
        val data =
            functions
                .getHttpsCallable(ENSURE_USER_PROFILE)
                .call(
                    mapOf(
                        NICKNAME to request.nickname,
                        KNOWN_LANGUAGES to request.knownLanguages,
                    ),
                )
                .await()
                .data
        return (data as? Map<*, *>).toUserProfile(request.uid)
    }

    override suspend fun updateNickname(
        nickname: String,
        knownLanguages: List<String>,
    ): UserProfile {
        val data =
            functions
                .getHttpsCallable(UPDATE_USER_NICKNAME)
                .call(
                    mapOf(
                        NICKNAME to nickname,
                        KNOWN_LANGUAGES to knownLanguages,
                    ),
                )
                .await()
                .data
        return (data as? Map<*, *>).toUserProfile(fallbackUid = "")
    }

    private fun Map<*, *>?.toUserProfile(fallbackUid: String): UserProfile {
        val qualification = map(QUALIFICATION)
        return UserProfile(
            uid = string(UID) ?: fallbackUid,
            nickname = string(NICKNAME) ?: "User",
            status = profileStatus(string(STATUS)),
            avatarUrl = string(AVATAR_URL),
            knownLanguages = stringList(KNOWN_LANGUAGES),
            createdAtMs = long(CREATED_AT_MS),
            updatedAtMs = long(UPDATED_AT_MS),
            skillPoints = long(SKILL_POINTS).toInt().coerceAtLeast(0),
            gold = long(GOLD).coerceAtLeast(0L),
            nolics = long(NOLICS).coerceAtLeast(0L),
            standardHearts = long(STANDARD_HEARTS).toInt().coerceAtLeast(0),
            goldHearts = long(GOLD_HEARTS).toInt().coerceAtLeast(0),
            // Already regenerated server-side, so the client shows it as-is.
            lifePoints = long(LIFE_POINTS).toInt().coerceAtLeast(0),
            lifePointsUpdatedAtMs = long(LIFE_POINTS_UPDATED_AT_MS).coerceAtLeast(0L),
            qualification =
                ProfileQualification(
                    sponsorLevel = qualification.long(SPONSOR_LEVEL).toInt().coerceAtLeast(0),
                    testerLevel = qualification.long(TESTER_LEVEL).toInt().coerceAtLeast(0),
                    translatorLevel = qualification.long(TRANSLATOR_LEVEL).toInt().coerceAtLeast(0),
                    moderatorLevel = qualification.long(MODERATOR_LEVEL).toInt().coerceAtLeast(0),
                    adminLevel = qualification.long(ADMIN_LEVEL).toInt().coerceAtLeast(0),
                    developerLevel = qualification.long(DEVELOPER_LEVEL).toInt().coerceAtLeast(0),
                ),
            boxCount = long(BOX_COUNT).toInt().coerceAtLeast(0),
            boxStreakDays = long(BOX_STREAK_DAYS).toInt().coerceAtLeast(0),
            nextBoxAtMs = long(NEXT_BOX_AT_MS).coerceAtLeast(0L),
            premiumUntilMs = long(PREMIUM_UNTIL_MS).coerceAtLeast(0L),
            trophies = long(TROPHIES).coerceAtLeast(0L),
            ownedLogos = stringList(OWNED_LOGOS),
        )
    }

    private fun profileStatus(value: String?): ProfileStatus =
        ProfileStatus.entries.firstOrNull { it.name == value } ?: ProfileStatus.ANONYMOUS

    private fun Map<*, *>?.map(field: String): Map<*, *> = this?.get(field) as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun Map<*, *>?.string(field: String): String? = this?.get(field)?.toString()?.takeIf { it.isNotBlank() }

    private fun Map<*, *>?.stringList(field: String): List<String> =
        (this?.get(field) as? List<*>).orEmpty()
            .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            .distinct()

    private fun Map<*, *>?.long(field: String): Long =
        when (val value = this?.get(field)) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> 0L
        }

    private companion object {
        const val ENSURE_USER_PROFILE = "ensureUserProfile"
        const val UPDATE_USER_NICKNAME = "updateUserNickname"
        const val UID = "uid"
        const val NICKNAME = "nickname"
        const val STATUS = "status"
        const val AVATAR_URL = "avatarUrl"
        const val KNOWN_LANGUAGES = "knownLanguages"
        const val CREATED_AT_MS = "createdAtMs"
        const val UPDATED_AT_MS = "updatedAtMs"
        const val SKILL_POINTS = "skillPoints"
        const val GOLD = "gold"
        const val NOLICS = "nolics"
        const val STANDARD_HEARTS = "standardHearts"
        const val GOLD_HEARTS = "goldHearts"
        const val LIFE_POINTS = "lifePoints"
        const val LIFE_POINTS_UPDATED_AT_MS = "lifePointsUpdatedAtMs"
        const val BOX_COUNT = "boxCount"
        const val BOX_STREAK_DAYS = "boxStreakDays"
        const val NEXT_BOX_AT_MS = "nextBoxAtMs"
        const val PREMIUM_UNTIL_MS = "premiumUntilMs"
        const val TROPHIES = "trophies"
        const val OWNED_LOGOS = "ownedLogos"
        const val QUALIFICATION = "qualification"
        const val SPONSOR_LEVEL = "sponsorLevel"
        const val TESTER_LEVEL = "testerLevel"
        const val TRANSLATOR_LEVEL = "translatorLevel"
        const val MODERATOR_LEVEL = "moderatorLevel"
        const val ADMIN_LEVEL = "adminLevel"
        const val DEVELOPER_LEVEL = "developerLevel"
    }
}
