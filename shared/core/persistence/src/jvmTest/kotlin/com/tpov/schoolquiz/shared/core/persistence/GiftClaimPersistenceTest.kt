package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Проводка заявок подарков против настоящего SQLite (Room на bundled-драйвере, без устройства).
 *
 * Ключевых свойств два, и оба про деньги: вторая заявка того же вида не создаёт второе намерение
 * (одно действие — одна строка, AD-2), а разрешает строку только свой operationId — повтор тела
 * после смерти процесса обязан найти своё, а не стереть чужое, вставшее тем же движением.
 */
class GiftClaimPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var intents: GiftClaimIntentDao
    private lateinit var localStreak: GiftBoxLocalStreakDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .addTypeConverter(StringSetConverter())
                .addTypeConverter(TopParticipantListConverter())
                .build()
        intents = db.giftClaimIntentDao()
        localStreak = db.giftBoxLocalStreakDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun secondClaimOfTheSameKindNeverCreatesASecondIntent() =
        runTest {
            val first = intent(operationId = "daily-id-0000000001")
            assertTrue(intents.insert(first) > 0L)
            assertTrue(intents.insert(intent(operationId = "daily-id-0000000002")) == -1L)

            val kept = intents.find(OWNER, KIND)
            assertNotNull(kept)
            assertEquals("daily-id-0000000001", kept.operationId)
        }

    @Test
    fun differentKindsHoldTheirOwnIntents() =
        runTest {
            assertTrue(intents.insert(intent(kind = "PREMIUM_DAILY", operationId = "daily-id-0000000001")) > 0L)
            assertTrue(intents.insert(intent(kind = "VISIT", operationId = "visit-id-0000000001")) > 0L)

            assertEquals("daily-id-0000000001", intents.find(OWNER, "PREMIUM_DAILY")?.operationId)
            assertEquals("visit-id-0000000001", intents.find(OWNER, "VISIT")?.operationId)
        }

    @Test
    fun resolveTouchesOnlyItsOwnOperationId() =
        runTest {
            intents.insert(intent(operationId = "daily-id-0000000001"))

            assertEquals(0, intents.resolve(OWNER, KIND, "daily-id-0000000099"))
            assertNotNull(intents.find(OWNER, KIND))
            assertEquals(1, intents.resolve(OWNER, KIND, "daily-id-0000000001"))
            assertNull(intents.find(OWNER, KIND))
        }

    @Test
    fun observeKeepsTheIntentVisibleUntilResolved() =
        runTest {
            assertNull(intents.observe(OWNER, KIND).first())

            intents.insert(intent(operationId = "daily-id-0000000001"))
            assertEquals("daily-id-0000000001", intents.observe(OWNER, KIND).first()?.operationId)

            intents.resolve(OWNER, KIND, "daily-id-0000000001")
            assertNull(intents.observe(OWNER, KIND).first())
        }

    @Test
    fun localStreakSeedsResetsAndDeletesByOwner() =
        runTest {
            assertNull(localStreak.find(OWNER))

            localStreak.upsert(
                GiftBoxLocalStreakEntity(
                    ownerUid = OWNER,
                    baseBoxCount = 1, baseStreakDays = 10, baseNextBoxAtMs = 100L,
                    boxCount = 3, streakDays = 12, nextBoxAtMs = 300L, updatedAtMs = 400L,
                ),
            )
            val kept = assertNotNull(localStreak.find(OWNER))
            assertEquals(12, kept.streakDays)
            assertEquals(10, kept.baseStreakDays)

            // Пересев новым словом сервера — то же движение, что и первая запись.
            localStreak.upsert(
                GiftBoxLocalStreakEntity(
                    ownerUid = OWNER,
                    baseBoxCount = 2, baseStreakDays = 11, baseNextBoxAtMs = 500L,
                    boxCount = 2, streakDays = 11, nextBoxAtMs = 500L, updatedAtMs = 600L,
                ),
            )
            assertEquals(11, localStreak.find(OWNER)?.streakDays)

            localStreak.delete(OWNER)
            assertNull(localStreak.find(OWNER))
        }

    @Test
    fun ownerRowsStaySeparatedInBothTables() =
        runTest {
            intents.insert(intent(operationId = "daily-id-0000000001"))
            intents.insert(intent(ownerUid = "uid-other", operationId = "daily-id-0000000002"))
            localStreak.upsert(
                GiftBoxLocalStreakEntity(
                    ownerUid = OWNER,
                    baseBoxCount = 1, baseStreakDays = 10, baseNextBoxAtMs = 100L,
                    boxCount = 3, streakDays = 12, nextBoxAtMs = 300L, updatedAtMs = 400L,
                ),
            )

            // Чужое намерение того же вида — другая строка: выборки всегда по владельцу.
            assertEquals("daily-id-0000000001", intents.find(OWNER, KIND)?.operationId)
            assertEquals("daily-id-0000000002", intents.find("uid-other", KIND)?.operationId)
            assertEquals("daily-id-0000000001", intents.observe(OWNER, KIND).first()?.operationId)
            assertNotNull(localStreak.find(OWNER))
            assertNull(localStreak.find("uid-other"))
        }

    private fun intent(
        ownerUid: String = OWNER,
        kind: String = KIND,
        operationId: String,
    ) = GiftClaimIntentEntity(
        ownerUid = ownerUid,
        kind = kind,
        operationId = operationId,
        payload = """{"kind":"$kind","ownerUid":"$ownerUid","operationId":"$operationId"}""",
        createdAtMs = 17L,
    )

    private companion object {
        const val OWNER = "uid-claims"
        const val KIND = "PREMIUM_DAILY"
    }
}
