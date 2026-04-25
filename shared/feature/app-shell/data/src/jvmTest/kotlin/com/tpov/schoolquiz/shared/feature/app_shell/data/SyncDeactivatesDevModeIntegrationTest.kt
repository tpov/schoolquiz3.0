package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.feature.app_shell.data.fake.FakeFirebaseUserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.data.fake.FakeUserStatsDao
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Journey integration test — "Dev mode auto-deactivation via sync" (US-08).
 *
 * Documents ADR-HLA-02 last-write-wins edge case:
 * local developerLevel=100 is overwritten by server developerLevel=0 on refreshProfile().
 *
 * Source: docs/features/menu-refactor/04-testing.md §5.3
 */
class SyncDeactivatesDevModeIntegrationTest {

    private val testUid = "dev-mode-uid"

    private fun entityWithDeveloperLevel(level: Int) = UserStatsEntity(
        uid = testUid,
        nickname = "DevUser",
        avatarUrl = null,
        hasPremium = false,
        streakDays = 0,
        stars = 0L,
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
        developerLevel = level,
    )

    @Test
    fun `given local developerLevel 100 when refreshProfile with server 0 then dao has developerLevel 0 and flow emits 0`() = runTest {
        val fakeDao = FakeUserStatsDao()
        fakeDao.emit(entityWithDeveloperLevel(100))
        val fakeFirebase = FakeFirebaseUserStatsDataSource().apply {
            result = Result.success(RawUserStats(developerLevel = 0))
        }
        val repository = UserStatsRepositoryImpl(
            remoteDataSource = fakeFirebase,
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )

        val result = repository.refreshProfile()

        assertTrue(result.isSuccess, "Expected Result.success after refreshProfile, got: $result")
        val upserted = fakeDao.lastUpserted
        assertNotNull(upserted, "Expected upsert to be called after refreshProfile")
        assertEquals(
            0,
            upserted.developerLevel,
            "server value (0) must overwrite local developerLevel (100) — last-write-wins ADR-HLA-02",
        )
        val flowValue = fakeDao.observeByUid(testUid).take(1).toList().first()
        assertEquals(
            0,
            flowValue?.developerLevel,
            "Flow must emit developerLevel=0 after server sync",
        )
    }
}
