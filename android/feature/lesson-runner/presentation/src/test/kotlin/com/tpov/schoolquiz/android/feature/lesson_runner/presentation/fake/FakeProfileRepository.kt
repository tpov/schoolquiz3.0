package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProfileRepository(
    profile: UserProfile = defaultProfile(),
) : ProfileRepository {
    private val store = MutableStateFlow(profile)

    fun setProfile(profile: UserProfile) {
        store.value = profile
    }

    override fun observeCurrentProfile(): Flow<UserProfile> = store

    override suspend fun currentProfile(): UserProfile = store.value

    override suspend fun ensureCurrentProfile(): Result<UserProfile> = Result.success(store.value)

    override suspend fun updateNickname(nickname: String): Result<UserProfile> =
        Result.success(store.value.copy(nickname = nickname))

    companion object {
        /** Online profile with a known heart budget; hearts derive from lifePoints / 100. */
        fun defaultProfile(standardHearts: Int = 5, lifePoints: Int = standardHearts * 100) =
            UserProfile(
                uid = "user1",
                nickname = "Tester",
                status = ProfileStatus.REGISTERED,
                avatarUrl = null,
                knownLanguages = emptyList(),
                createdAtMs = 0L,
                updatedAtMs = 0L,
                skillPoints = 0,
                gold = 0L,
                nolics = 0L,
                standardHearts = standardHearts,
                goldHearts = 0,
                qualification = ProfileQualification(),
                lifePoints = lifePoints,
            )
    }
}
