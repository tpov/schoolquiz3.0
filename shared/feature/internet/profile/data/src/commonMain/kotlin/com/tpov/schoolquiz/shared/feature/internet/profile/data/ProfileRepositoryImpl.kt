package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileBootstrapRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class ProfileRepositoryImpl(
    private val local: ProfileLocalDataSource,
    private val remote: ProfileRemoteDataSource,
    private val currentUidFlow: () -> Flow<String?>,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val defaultLanguages: () -> List<String> = { listOf("ru") },
) : ProfileRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentProfile(): Flow<UserProfile> =
        currentUidFlow().flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(UserProfile.offline())
            } else {
                local.observe(uid).map { it ?: createSeedProfile(uid) }
            }
        }

    override suspend fun currentProfile(): UserProfile {
        val uid = currentUidFlow().first()
        if (uid.isNullOrBlank()) return UserProfile.offline()
        return local.find(uid) ?: createSeedProfile(uid)
    }

    override suspend fun ensureCurrentProfile(): Result<UserProfile> {
        val uid = currentUidFlow().first()
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Authenticated uid is required"))
        }
        return runCatchingCancellable {
            val localProfile = local.find(uid) ?: createSeedProfile(uid)
            if (local.find(uid) == null) {
                local.upsert(localProfile)
            }
            val remoteProfile =
                remote.ensureProfile(
                    ProfileBootstrapRequest(
                        uid = uid,
                        nickname = localProfile.nickname,
                        knownLanguages = localProfile.knownLanguages.ifEmpty { defaultLanguages() },
                    ),
                )
            local.upsert(remoteProfile)
            remoteProfile
        }
    }

    override suspend fun updateNickname(nickname: String): Result<UserProfile> {
        val uid = currentUidFlow().first()
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Authenticated uid is required"))
        }
        return runCatchingCancellable {
            val base = local.find(uid) ?: createSeedProfile(uid)
            val remoteProfile =
                remote.updateNickname(
                    nickname = nickname,
                    knownLanguages = base.knownLanguages.ifEmpty { defaultLanguages() },
                )
            local.upsert(remoteProfile)
            remoteProfile
        }
    }

    private fun createSeedProfile(uid: String): UserProfile {
        val now = nowMs()
        return UserProfile(
            uid = uid,
            nickname = generatedNickname(uid),
            status = ProfileStatus.ANONYMOUS,
            avatarUrl = null,
            knownLanguages = defaultLanguages().distinct().filter { it.isNotBlank() },
            createdAtMs = now,
            updatedAtMs = now,
            skillPoints = 0,
            gold = 0L,
            nolics = 0L,
            standardHearts = 5,
            goldHearts = 0,
            qualification = ProfileQualification(),
            boxCount = 0,
            boxStreakDays = 0,
            nextBoxAtMs = 0L,
            premiumUntilMs = 0L,
            trophies = emptySet(),
            ownedLogos = emptyList(),
        )
    }
}

private fun generatedNickname(uid: String): String {
    val suffix = uid.filter { it.isLetterOrDigit() }.takeLast(6).uppercase()
    return "User${suffix.ifBlank { "000000" }}"
}

private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
