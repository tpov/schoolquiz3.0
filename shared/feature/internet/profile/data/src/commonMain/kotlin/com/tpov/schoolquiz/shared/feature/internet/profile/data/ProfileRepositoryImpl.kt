package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileBootstrapRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LeagueStanding
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
    private val pendingReferral: PendingReferralStore,
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

    /** Best effort: a standing nobody could fetch is left unsaid rather than guessed at. */
    override suspend fun leagueStanding(): LeagueStanding? = runCatching { remote.leagueStanding() }.getOrNull()

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
            val referral = consumeReferralForBootstrap(uid)
            val remoteProfile =
                remote.ensureProfile(
                    ProfileBootstrapRequest(
                        uid = uid,
                        nickname = localProfile.nickname,
                        knownLanguages = localProfile.knownLanguages.ifEmpty { defaultLanguages() },
                        referredByUid = referral,
                    ),
                )
            // Вызов дошёл — код потрачен: следующий bootstrap уже без пригласившего, каким бы
            // ни был ответ сервера по существу (для существующего аккаунта поле игнорируется).
            if (referral != null) pendingReferral.clear()
            local.upsert(remoteProfile)
            remoteProfile
        }
    }

    /**
     * Регистрация по ссылке (история 5.3): код уходит только с первым успешным bootstrap и
     * стирается лишь тогда — упавший по сети вызов код не тратит, попытка повторится при
     * следующей синхронизации. Испорченный код и самоприглашение выбрасываются без отправки:
     * сервер отвечает на них отказом всего вызова, а из-за ссылки нельзя блокировать вход.
     */
    private suspend fun consumeReferralForBootstrap(uid: String): String? {
        val code = pendingReferral.read()?.trim() ?: return null
        if (!code.isSendableReferrer(selfUid = uid)) {
            pendingReferral.clear()
            return null
        }
        return code
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

/**
 * Зеркало проверки сервера (referral-registration.js, documentUid): то, что сервер отверг бы
 * отказом всего вызова, клиент не отправляет вовсе.
 */
private const val MAX_REFERRAL_CODE_LENGTH: Int = 128

private fun String.isSendableReferrer(selfUid: String): Boolean =
    isNotBlank() &&
        this != selfUid &&
        this != "." &&
        this != ".." &&
        !contains('/') &&
        length <= MAX_REFERRAL_CODE_LENGTH

private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
