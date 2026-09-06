package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.feature.app_shell.data.fake.FakeFirebaseUserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.data.fake.FakeUserStatsDao
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

// Legacy D-series tests adapted to Phase 04 DAO-based constructor.
// D1 (observeStats from Firebase), D3a (network error propagation),
// D4 (normal flow completion), D5 (AuthUidChanged retry) are removed —
// those scenarios no longer apply: observeStats reads from Room DAO, not Firebase.
// Phase04-specific US-01..08 coverage lives in UserStatsRepositoryImplPhase04Test.kt.
class UserStatsRepositoryImplTest {

    private val testUid = "uid-legacy"

    private fun emptyEntity(uid: String = testUid) = UserStatsEntity(
        uid = uid,
        nickname = "Bob",
        avatarUrl = null,
        hasPremium = false,
        streakDays = 0,
        stars = 42L,
        nolics = 0L,
        standardHearts = 5,
        goldHearts = 0,
        gold = 0L,
        currentSkill = 0,
        testerLevel = 0,
        moderatorLevel = 0,
        sponsorLevel = 0,
        translatorLevel = 0,
        adminLevel = 0,
        developerLevel = 0,
    )

    // -------------------------------------------------------------------------
    // Локальная трата, о которой сервер ещё не знает
    // -------------------------------------------------------------------------

    @Test
    fun `given an undelivered local charge then the profile snapshot does not give the nolics back`() =
        runTest {
            // Профиль ходит по своему расписанию и очередь не ждёт. Снимок сервера про покупку не
            // знает: верни его как есть — игрок потратит эти нолики второй раз, а откат карантина
            // добавит возврат к уже восстановленному балансу.
            val fakeDao = FakeUserStatsDao()
            fakeDao.emit(emptyEntity().copy(nolics = 47L))
            val remote = FakeFirebaseUserStatsDataSource()
            remote.result = Result.success(RawUserStats(nolics = 100L))
            val repo =
                UserStatsRepositoryImpl(
                    remoteDataSource = remote,
                    userStatsDao = fakeDao,
                    currentUidFlow = { flowOf(testUid) },
                    pendingCharges = { 53L },
                )

            repo.refreshProfile()

            assertEquals(47L, fakeDao.findByUid(testUid)?.nolics, "списанное остаётся списанным")
        }

    @Test
    fun `given everything was delivered then the server balance stands as it is`() = runTest {
        val fakeDao = FakeUserStatsDao()
        fakeDao.emit(emptyEntity().copy(nolics = 47L))
        val remote = FakeFirebaseUserStatsDataSource()
        remote.result = Result.success(RawUserStats(nolics = 100L))
        val repo =
            UserStatsRepositoryImpl(
                remoteDataSource = remote,
                userStatsDao = fakeDao,
                currentUidFlow = { flowOf(testUid) },
                pendingCharges = { 0L },
            )

        repo.refreshProfile()

        assertEquals(100L, fakeDao.findByUid(testUid)?.nolics, "доехало всё — вычитать нечего")
    }

    // -------------------------------------------------------------------------
    // D2 — currentStats() reads from DAO and maps domain model
    // -------------------------------------------------------------------------

    @Test
    fun `D2 currentStats returns mapped domain model from DAO`() = runTest {
        val fakeDao = FakeUserStatsDao()
        fakeDao.emit(emptyEntity())
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = FakeFirebaseUserStatsDataSource(),
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )

        val result = repo.currentStats()

        assertEquals("Bob", result.nickname)
        assertEquals(42L, result.stars)
    }

    // -------------------------------------------------------------------------
    // D3b — currentStats() returns guest when DAO has no entity
    // -------------------------------------------------------------------------

    @Test
    fun `D3b currentStats returns guest when DAO has no entity for uid`() = runTest {
        val fakeDao = FakeUserStatsDao()
        // No entity emitted — DAO returns null
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = FakeFirebaseUserStatsDataSource(),
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )

        val result = repo.currentStats()

        assertEquals(UserStats.guest(), result)
    }
}
