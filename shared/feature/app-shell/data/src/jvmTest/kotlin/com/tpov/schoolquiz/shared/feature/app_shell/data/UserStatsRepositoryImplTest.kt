package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.stats.AuthUidChanged
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.data.fake.FakeUserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class UserStatsRepositoryImplTest {

    // -------------------------------------------------------------------------
    // D1 — observeStats() mapping
    // -------------------------------------------------------------------------

    @Test
    fun `D1 when dataSource emits RawUserStats then observeStats emits mapped UserStats`() = runTest {
        val raw = RawUserStats(nickname = "Alice", currentSkill = 500, nolics = 1000L)
        val fake = FakeUserStatsDataSource(raw)
        val repo = UserStatsRepositoryImpl(fake)

        val emitted = repo.observeStats().first()

        assertEquals("Alice", emitted.nickname)
        assertEquals(500, emitted.currentSkill)
        assertEquals(1000L, emitted.nolics)
    }

    @Test
    fun `D1 when dataSource emits qualification levels then Qualification fields are mapped`() = runTest {
        val raw = RawUserStats(testerLevel = 3, moderatorLevel = 1, developerLevel = 5)
        val fake = FakeUserStatsDataSource(raw)
        val repo = UserStatsRepositoryImpl(fake)

        val emitted = repo.observeStats().first()

        assertEquals(3, emitted.qualification.tester)
        assertEquals(1, emitted.qualification.moderator)
        assertEquals(5, emitted.qualification.developer)
    }

    @Test
    fun `D1 when dataSource emits multiple stats then all emissions are mapped`() = runTest {
        val fake = FakeUserStatsDataSource(RawUserStats(currentSkill = 0))
        val repo = UserStatsRepositoryImpl(fake)
        val collected = mutableListOf<UserStats>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.observeStats().toList(collected)
        }

        fake.emit(RawUserStats(currentSkill = 100))
        fake.emit(RawUserStats(currentSkill = 200))
        job.cancel()

        assertTrue(collected.size >= 3)
        assertEquals(200, collected.last().currentSkill)
    }

    @Test
    fun `D1 full field round-trip RawUserStats toDomain preserves all fields`() = runTest {
        val raw = RawUserStats(
            nickname = "TestUser",
            avatarUrl = "https://example.com/avatar.png",
            hasPremium = true,
            streakDays = 7,
            stars = 1234L,
            nolics = 5678L,
            standardHearts = 3,
            goldHearts = 1,
            gold = 999L,
            currentSkill = 420,
            testerLevel = 2,
            moderatorLevel = 3,
            sponsorLevel = 0,
            translatorLevel = 1,
            adminLevel = 0,
            developerLevel = 4,
        )
        val fake = FakeUserStatsDataSource(raw)
        val repo = UserStatsRepositoryImpl(fake)

        val emitted = repo.observeStats().first()

        assertEquals("TestUser", emitted.nickname)
        assertEquals("https://example.com/avatar.png", emitted.avatarUrl)
        assertEquals(true, emitted.hasPremium)
        assertEquals(7, emitted.streakDays)
        assertEquals(1234L, emitted.stars)
        assertEquals(5678L, emitted.nolics)
        assertEquals(3, emitted.standardHearts)
        assertEquals(1, emitted.goldHearts)
        assertEquals(999L, emitted.gold)
        assertEquals(420, emitted.currentSkill)
        assertEquals(2, emitted.qualification.tester)
        assertEquals(3, emitted.qualification.moderator)
        assertEquals(0, emitted.qualification.sponsor)
        assertEquals(1, emitted.qualification.translator)
        assertEquals(0, emitted.qualification.admin)
        assertEquals(4, emitted.qualification.developer)
    }

    // -------------------------------------------------------------------------
    // D2 — currentStats() single fetch
    // -------------------------------------------------------------------------

    @Test
    fun `D2 currentStats returns mapped domain model`() = runTest {
        val raw = RawUserStats(nickname = "Bob", stars = 42L)
        val fake = FakeUserStatsDataSource().apply { fetchRawResult = raw }
        val repo = UserStatsRepositoryImpl(fake)

        val result = repo.currentStats()

        assertEquals("Bob", result.nickname)
        assertEquals(42L, result.stars)
        assertEquals(1, fake.fetchRawCallCount)
    }

    // -------------------------------------------------------------------------
    // D3 — Error recovery
    // -------------------------------------------------------------------------

    @Test
    fun `D3 when dataSource throws non-retryable error then observeStats propagates exception`() = runTest {
        val fake = FakeUserStatsDataSource().apply {
            observeRawShouldThrow = RuntimeException("network error")
        }
        val repo = UserStatsRepositoryImpl(fake)

        // Non-AuthUidChanged errors are not retried — they propagate to the consumer.
        val result = runCatching { repo.observeStats().toList() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `D3b currentStats offline returns guest`() = runTest {
        val fake = FakeUserStatsDataSource().apply {
            fetchRawShouldThrow = RuntimeException("offline")
        }
        val repo = UserStatsRepositoryImpl(fake)

        val result = repo.currentStats()

        assertEquals(UserStats.guest(), result)
    }

    // -------------------------------------------------------------------------
    // D4 — Normal flow completion (sign-out scenario)
    // Spec: observe_stats_normal_completion_no_spurious_guest
    // GIVEN source flow emits one value and completes normally (e.g., sign-out closes Firebase)
    // WHEN observeStats() is collected to end
    // THEN exactly 1 item emitted (the mapped value), NO extra guest emission from catch/retry
    // -------------------------------------------------------------------------

    @Test
    fun `D4 when source flow completes normally then observeStats completes without emitting guest`() = runTest {
        val source = object : UserStatsDataSource {
            override fun observeRaw(): Flow<RawUserStats> = flowOf(RawUserStats(nickname = "SignedIn"))
            override suspend fun fetchRaw(): RawUserStats = RawUserStats()
        }
        val repo = UserStatsRepositoryImpl(source)

        val collected = repo.observeStats().toList()

        // retryWhen { cause is AuthUidChanged } must NOT fire on normal completion.
        // Sign-out closes the upstream flow normally — 1 value, then completion (no spurious guest).
        assertEquals(1, collected.size)
        assertEquals("SignedIn", collected[0].nickname)
    }

    // -------------------------------------------------------------------------
    // D5 — retryWhen on AuthUidChanged
    // Spec: observe_stats_retries_on_auth_uid_changed
    // GIVEN source throws AuthUidChanged after first emission
    // WHEN observeStats() is collected
    // THEN first emission received, retry occurs, second emission received (no propagation)
    // -------------------------------------------------------------------------

    @Test
    fun `D5 when source throws AuthUidChanged then observeStats retries and emits subsequent values`() = runTest {
        var subscribeCount = 0
        val source = object : UserStatsDataSource {
            override fun observeRaw(): Flow<RawUserStats> = flow {
                subscribeCount++
                emit(RawUserStats(nickname = "attempt-$subscribeCount"))
                if (subscribeCount == 1) throw AuthUidChanged()
                // Second subscription: emit and complete normally
            }
            override suspend fun fetchRaw(): RawUserStats = RawUserStats()
        }
        val repo = UserStatsRepositoryImpl(source)

        val collected = repo.observeStats().toList()

        assertEquals(2, collected.size)
        assertEquals("attempt-1", collected[0].nickname)
        assertEquals("attempt-2", collected[1].nickname)
        assertEquals(2, subscribeCount)
    }
}
