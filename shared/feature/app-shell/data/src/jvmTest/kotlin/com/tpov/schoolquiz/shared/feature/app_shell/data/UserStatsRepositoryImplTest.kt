package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
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
