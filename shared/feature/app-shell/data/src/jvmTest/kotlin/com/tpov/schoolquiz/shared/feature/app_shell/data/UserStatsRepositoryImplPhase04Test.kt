package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.feature.app_shell.data.fake.FakeFirebaseUserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.data.fake.FakeUserStatsDao
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

// Phase 04 supplemental tests — 04-testing.md §3.5 — US-01..US-08.
// Require: UserStatsRepositoryImpl rewrite + UserStatsMapper + core:persistence dep (all Phase 04).
class UserStatsRepositoryImplPhase04Test {

    private val testUid = "uid-test-123"

    private fun entityWithDefaults(
        uid: String = testUid,
        nickname: String = "TestUser",
        avatarUrl: String? = "https://example.com/avatar.png",
        hasPremium: Boolean = true,
        streakDays: Int = 7,
        stars: Long = 1234L,
        nolics: Long = 5678L,
        standardHearts: Int = 3,
        goldHearts: Int = 1,
        gold: Long = 999L,
        currentSkill: Int = 420,
        testerLevel: Int = 2,
        moderatorLevel: Int = 3,
        sponsorLevel: Int = 0,
        translatorLevel: Int = 1,
        adminLevel: Int = 0,
        developerLevel: Int = 4,
    ) = UserStatsEntity(
        uid = uid,
        nickname = nickname,
        avatarUrl = avatarUrl,
        hasPremium = hasPremium,
        streakDays = streakDays,
        stars = stars,
        nolics = nolics,
        standardHearts = standardHearts,
        goldHearts = goldHearts,
        gold = gold,
        currentSkill = currentSkill,
        testerLevel = testerLevel,
        moderatorLevel = moderatorLevel,
        sponsorLevel = sponsorLevel,
        translatorLevel = translatorLevel,
        adminLevel = adminLevel,
        developerLevel = developerLevel,
    )

    // -------------------------------------------------------------------------
    // US-01 — observeStats uid=null → emits UserStats.guest() (H2 fix)
    // spec: 04-testing.md §3.5 (updated: H2 cross-phase fix — uid=null now emits guest stats
    //       so AppShell UI immediately shows guest privileges on sign-out)
    // GIVEN currentUidFlow emits null WHEN observeStats().take(1).toList() THEN guest stats
    // -------------------------------------------------------------------------

    @Test
    fun `US-01 given currentUid null when observeStats then emits guest stats`() = runTest {
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = FakeFirebaseUserStatsDataSource(),
            userStatsDao = FakeUserStatsDao(),
            currentUidFlow = { flowOf(null) },
        )

        val collected = repo.observeStats().take(1).toList()

        assertEquals(1, collected.size, "Expected exactly one emission (guest stats) when uid is null")
        assertEquals(UserStats.guest(), collected.first())
    }

    // -------------------------------------------------------------------------
    // US-02 — observeStats entity=null → UserStats.guest()
    // spec: 04-testing.md §3.5
    // GIVEN uid exists, Room entity=null WHEN observeStats().take(1) THEN UserStats.guest()
    // -------------------------------------------------------------------------

    @Test
    fun `US-02 given entity null in Room when observeStats then emits guest`() = runTest {
        val fakeDao = FakeUserStatsDao()
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = FakeFirebaseUserStatsDataSource(),
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )
        fakeDao.emit(null)

        val emitted = repo.observeStats().take(1).toList().first()

        assertEquals(UserStats.guest(), emitted)
    }

    // -------------------------------------------------------------------------
    // US-03 — toDomain() maps all 16 data fields correctly
    // spec: 04-testing.md §3.5
    // GIVEN entity with all fields WHEN observeStats().take(1) THEN all 16 fields correct
    // -------------------------------------------------------------------------

    @Test
    fun `US-03 given entity with all 16 fields when observeStats then all fields are mapped`() = runTest {
        val fakeDao = FakeUserStatsDao()
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = FakeFirebaseUserStatsDataSource(),
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )
        fakeDao.emit(entityWithDefaults())

        val result = repo.observeStats().take(1).toList().first()

        assertEquals("TestUser", result.nickname)
        assertEquals("https://example.com/avatar.png", result.avatarUrl)
        assertEquals(true, result.hasPremium)
        assertEquals(7, result.streakDays)
        assertEquals(1234L, result.stars)
        assertEquals(5678L, result.nolics)
        assertEquals(3, result.standardHearts)
        assertEquals(1, result.goldHearts)
        assertEquals(999L, result.gold)
        assertEquals(420, result.currentSkill)
        assertEquals(2, result.qualification.tester)
        assertEquals(3, result.qualification.moderator)
        assertEquals(0, result.qualification.sponsor)
        assertEquals(1, result.qualification.translator)
        assertEquals(0, result.qualification.admin)
        assertEquals(4, result.qualification.developer)
    }

    @Test
    fun `US-03 avatarUrl null is preserved through toDomain`() = runTest {
        val fakeDao = FakeUserStatsDao()
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = FakeFirebaseUserStatsDataSource(),
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )
        fakeDao.emit(entityWithDefaults(avatarUrl = null))

        val result = repo.observeStats().take(1).toList().first()

        assertNull(result.avatarUrl)
    }

    // -------------------------------------------------------------------------
    // US-04 — setLocalDeveloperLevel → updateDeveloperLevel (NOT upsert) — ADR-HLA-02
    // spec: 04-testing.md §3.5 P0
    // GIVEN uid exists WHEN setLocalDeveloperLevel(100) THEN updateDeveloperLevelCalls==1
    // AND lastUpserted==null (upsert NOT called)
    // -------------------------------------------------------------------------

    @Test
    fun `US-04 given uid exists when setLocalDeveloperLevel then targeted UPDATE called not upsert`() = runTest {
        val fakeDao = FakeUserStatsDao()
        fakeDao.emit(entityWithDefaults())
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = FakeFirebaseUserStatsDataSource(),
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )

        repo.setLocalDeveloperLevel(100)

        assertEquals(1, fakeDao.updateDeveloperLevelCalls)
        assertNull(fakeDao.lastUpserted, "upsert must NOT be called — ADR-HLA-02 targeted UPDATE only")
    }

    // -------------------------------------------------------------------------
    // US-05 — setLocalDeveloperLevel uid=null → noop
    // spec: 04-testing.md §3.5
    // GIVEN currentUidFlow emits null WHEN setLocalDeveloperLevel(100) THEN DAO not called
    // -------------------------------------------------------------------------

    @Test
    fun `US-05 given uid null when setLocalDeveloperLevel then DAO not called`() = runTest {
        val fakeDao = FakeUserStatsDao()
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = FakeFirebaseUserStatsDataSource(),
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(null) },
        )

        repo.setLocalDeveloperLevel(100)

        assertEquals(0, fakeDao.updateDeveloperLevelCalls)
        assertNull(fakeDao.lastUpserted)
    }

    // -------------------------------------------------------------------------
    // US-06 — refreshProfile → upsert in Room
    // spec: 04-testing.md §3.5
    // GIVEN Firebase returns RawUserStats WHEN refreshProfile() THEN upsert called, Result.success
    // -------------------------------------------------------------------------

    @Test
    fun `US-06 given Firebase returns stats when refreshProfile then upsert called and Result success`() = runTest {
        val fakeDao = FakeUserStatsDao()
        val fakeFirebase = FakeFirebaseUserStatsDataSource().apply {
            result = Result.success(RawUserStats(developerLevel = 0))
        }
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = fakeFirebase,
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )

        val result = repo.refreshProfile()

        assertNotNull(fakeDao.lastUpserted, "upsert must be called after refreshProfile")
        assertTrue(result.isSuccess)
    }

    // -------------------------------------------------------------------------
    // US-07 — refreshProfile uid=null → Result.failure, no Firebase call
    // spec: 04-testing.md §3.5 P1 (auth guard)
    // GIVEN currentUidFlow emits null WHEN refreshProfile() THEN Result.failure, fetchCalls==0
    // -------------------------------------------------------------------------

    @Test
    fun `US-07 given uid null when refreshProfile then Result failure and no Firebase call`() = runTest {
        val fakeFirebase = FakeFirebaseUserStatsDataSource()
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = fakeFirebase,
            userStatsDao = FakeUserStatsDao(),
            currentUidFlow = { flowOf(null) },
        )

        val result = repo.refreshProfile()

        assertTrue(result.isFailure, "refreshProfile must fail when uid is null")
        assertEquals(0, fakeFirebase.fetchCalls, "Firebase fetchRaw must NOT be called when uid is null")
    }

    // -------------------------------------------------------------------------
    // US-08 — refreshProfile full-overwrites developerLevel (dev mode auto-deactivation)
    // spec: 04-testing.md §3.5 P0, ADR-HLA-02 + dev-mode AC #8
    // GIVEN Room has developerLevel=100, Firebase returns developerLevel=0
    // WHEN refreshProfile() THEN upserted entity has developerLevel==0
    // -------------------------------------------------------------------------

    @Test
    fun `US-08 given local developerLevel 100 when refreshProfile with server 0 then upserted entity has developerLevel 0`() = runTest {
        val fakeDao = FakeUserStatsDao()
        fakeDao.emit(entityWithDefaults(developerLevel = 100))
        val fakeFirebase = FakeFirebaseUserStatsDataSource().apply {
            result = Result.success(RawUserStats(developerLevel = 0))
        }
        val repo = UserStatsRepositoryImpl(
            remoteDataSource = fakeFirebase,
            userStatsDao = fakeDao,
            currentUidFlow = { flowOf(testUid) },
        )

        repo.refreshProfile()

        val upserted = fakeDao.lastUpserted
        assertNotNull(upserted)
        assertEquals(0, upserted.developerLevel,
            "refreshProfile must full-overwrite developerLevel with server value — dev mode auto-deactivation")
    }
}
