package com.tpov.schoolquiz.shared.core.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DAO boundary tests for [UserStatsDao].
 *
 * Tests: upsert+observe, updateDeveloperLevel targeted, upsert replaces, null uid flow.
 * Source: docs/features/menu-refactor/04-testing.md §5.2
 *
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class UserStatsDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserStatsDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        dao = db.userStatsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun testEntity(
        uid: String = "test-uid",
        developerLevel: Int = 0,
        testerLevel: Int = 0,
    ) = UserStatsEntity(
        uid = uid,
        nickname = "TestUser",
        avatarUrl = null,
        hasPremium = false,
        streakDays = 0,
        stars = 0L,
        nolics = 0L,
        standardHearts = 5,
        goldHearts = 0,
        gold = 0L,
        currentSkill = 0,
        testerLevel = testerLevel,
        moderatorLevel = 0,
        sponsorLevel = 0,
        translatorLevel = 0,
        adminLevel = 0,
        developerLevel = developerLevel,
    )

    @Test
    fun upsert_and_observe_by_uid() = runTest {
        dao.upsert(testEntity(uid = "user1"))

        val result = dao.observeByUid("user1").take(1).toList()

        assert(result.first()?.uid == "user1") {
            "Expected uid='user1', got: ${result.first()?.uid}"
        }
    }

    @Test
    fun updateDeveloperLevel_targeted_update() = runTest {
        dao.upsert(testEntity(uid = "u", developerLevel = 0, testerLevel = 50))

        dao.updateDeveloperLevel("u", 100)

        val entity = dao.findByUid("u")
        assert(entity?.developerLevel == 100) {
            "Expected developerLevel=100 after targeted update, got: ${entity?.developerLevel}"
        }
        assert(entity?.testerLevel == 50) {
            "Expected testerLevel=50 (other fields not changed), got: ${entity?.testerLevel}"
        }
    }

    @Test
    fun upsert_replace_overwrites_developer_level() = runTest {
        val uid = "test-uid"
        dao.upsert(testEntity(uid = uid, developerLevel = 100))

        dao.upsert(testEntity(uid = uid, developerLevel = 0))

        val entity = dao.findByUid(uid)
        assert(entity?.developerLevel == 0) {
            "Expected developerLevel=0 after full upsert overwrite, got: ${entity?.developerLevel}"
        }
    }

    @Test
    fun observe_returns_null_for_unknown_uid() = runTest {
        val result = dao.observeByUid("unknown-uid").take(1).toList()

        assert(result.first() == null) {
            "Expected null for unknown uid, got: ${result.first()}"
        }
    }
}
