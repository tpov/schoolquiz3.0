package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileBootstrapRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LeagueStanding
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRepositoryImplTest {
    @Test
    fun ensureCurrentProfile_createsAnonymousProfileForCurrentUid() = runTest {
        val local = FakeProfileLocalDataSource()
        val remote = FakeProfileRemoteDataSource()
        val repository =
            ProfileRepositoryImpl(
                local = local,
                remote = remote,
                currentUidFlow = { flowOf("uid-123456") },
                pendingReferral = InMemoryPendingReferralStore(),
                nowMs = { 100L },
                defaultLanguages = { listOf("ru") },
            )

        val profile = repository.ensureCurrentProfile().getOrThrow()

        assertEquals("uid-123456", profile.uid)
        assertEquals(ProfileStatus.ANONYMOUS, profile.status)
        assertEquals(listOf("ru"), profile.knownLanguages)
        assertEquals(profile, local.find("uid-123456"))
        assertEquals(1, remote.ensureCalls)
    }

    @Test
    fun updateNickname_delegatesSanitizedNicknameToRemote() = runTest {
        val local = FakeProfileLocalDataSource()
        val remote = FakeProfileRemoteDataSource()
        val repository =
            ProfileRepositoryImpl(
                local = local,
                remote = remote,
                currentUidFlow = { flowOf("uid-1") },
                pendingReferral = InMemoryPendingReferralStore(),
                nowMs = { 100L },
                defaultLanguages = { listOf("en") },
            )

        val profile = repository.updateNickname("Better Name").getOrThrow()

        assertEquals("Better Name", profile.nickname)
        assertEquals("Better Name", remote.lastNickname)
    }

    @Test
    fun ensureCurrentProfile_returnsFailureWithoutUid() = runTest {
        val repository =
            ProfileRepositoryImpl(
                local = FakeProfileLocalDataSource(),
                remote = FakeProfileRemoteDataSource(),
                currentUidFlow = { flowOf(null) },
                pendingReferral = InMemoryPendingReferralStore(),
            )

        assertTrue(repository.ensureCurrentProfile().isFailure)
    }

    @Test
    fun ensureCurrentProfile_sendsPendingReferralAndSpendsIt() = runTest {
        val remote = FakeProfileRemoteDataSource()
        val pending = InMemoryPendingReferralStore()
        pending.write("inviter-uid")
        val repository =
            ProfileRepositoryImpl(
                local = FakeProfileLocalDataSource(),
                remote = remote,
                currentUidFlow = { flowOf("uid-123456") },
                pendingReferral = pending,
            )

        repository.ensureCurrentProfile().getOrThrow()

        assertEquals("inviter-uid", remote.lastRequest?.referredByUid)
        assertNull(pending.read())
    }

    @Test
    fun ensureCurrentProfile_keepsPendingReferralWhenRemoteFails() = runTest {
        val remote = FakeProfileRemoteDataSource(failEnsure = true)
        val pending = InMemoryPendingReferralStore()
        pending.write("inviter-uid")
        val repository =
            ProfileRepositoryImpl(
                local = FakeProfileLocalDataSource(),
                remote = remote,
                currentUidFlow = { flowOf("uid-123456") },
                pendingReferral = pending,
            )

        assertTrue(repository.ensureCurrentProfile().isFailure)

        assertEquals("inviter-uid", pending.read())
    }

    @Test
    fun ensureCurrentProfile_dropsSelfInvitationWithoutSending() = runTest {
        val remote = FakeProfileRemoteDataSource()
        val pending = InMemoryPendingReferralStore()
        pending.write("uid-123456")
        val repository =
            ProfileRepositoryImpl(
                local = FakeProfileLocalDataSource(),
                remote = remote,
                currentUidFlow = { flowOf("uid-123456") },
                pendingReferral = pending,
            )

        repository.ensureCurrentProfile().getOrThrow()

        assertNull(remote.lastRequest?.referredByUid)
        assertNull(pending.read())
    }

    @Test
    fun ensureCurrentProfile_dropsMalformedReferralWithoutSending() = runTest {
        val remote = FakeProfileRemoteDataSource()
        val pending = InMemoryPendingReferralStore()
        pending.write("a/broken/code")
        val repository =
            ProfileRepositoryImpl(
                local = FakeProfileLocalDataSource(),
                remote = remote,
                currentUidFlow = { flowOf("uid-123456") },
                pendingReferral = pending,
            )

        repository.ensureCurrentProfile().getOrThrow()

        assertNull(remote.lastRequest?.referredByUid)
        assertNull(pending.read())
    }

    @Test
    fun ensureCurrentProfile_sendsNoReferrerWhenNoneWasCaptured() = runTest {
        val remote = FakeProfileRemoteDataSource()
        val repository =
            ProfileRepositoryImpl(
                local = FakeProfileLocalDataSource(),
                remote = remote,
                currentUidFlow = { flowOf("uid-123456") },
                pendingReferral = InMemoryPendingReferralStore(),
            )

        repository.ensureCurrentProfile().getOrThrow()

        assertNull(remote.lastRequest?.referredByUid)
    }
}

private class FakeProfileLocalDataSource : ProfileLocalDataSource {
    private val state = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    override fun observe(uid: String): Flow<UserProfile?> =
        state.map { it[uid] }

    override suspend fun find(uid: String): UserProfile? = state.value[uid]

    override suspend fun upsert(profile: UserProfile) {
        state.value = state.value + (profile.uid to profile)
    }
}

private class FakeProfileRemoteDataSource(
    private val failEnsure: Boolean = false,
) : ProfileRemoteDataSource {
    /** Ranking is not what these tests are about; a fixed standing keeps them honest and quiet. */
    override suspend fun leagueStanding(): LeagueStanding = LeagueStanding(place = 1, total = 1, topPercent = 1)

    var ensureCalls: Int = 0
    var lastNickname: String? = null
    var lastRequest: ProfileBootstrapRequest? = null

    override suspend fun ensureProfile(request: ProfileBootstrapRequest): UserProfile {
        ensureCalls += 1
        lastRequest = request
        lastNickname = request.nickname
        if (failEnsure) error("network is down")
        return profile(
            uid = request.uid,
            nickname = request.nickname,
            knownLanguages = request.knownLanguages,
        )
    }

    override suspend fun updateNickname(
        nickname: String,
        knownLanguages: List<String>,
    ): UserProfile {
        lastNickname = nickname
        return profile(uid = "uid-1", nickname = nickname, knownLanguages = knownLanguages)
    }

    private fun profile(
        uid: String,
        nickname: String,
        knownLanguages: List<String>,
    ): UserProfile =
        UserProfile(
            uid = uid,
            nickname = nickname,
            status = ProfileStatus.ANONYMOUS,
            avatarUrl = null,
            knownLanguages = knownLanguages,
            createdAtMs = 1L,
            updatedAtMs = 1L,
            skillPoints = 0,
            gold = 0L,
            nolics = 0L,
            standardHearts = 5,
            goldHearts = 0,
            qualification = ProfileQualification(),
        )
}
