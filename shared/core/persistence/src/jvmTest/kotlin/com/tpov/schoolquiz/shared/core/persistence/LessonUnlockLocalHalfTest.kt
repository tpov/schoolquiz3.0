package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Локальная половина разблокировки урока — против настоящего SQLite.
 *
 * Проверяется то, чего фейком не изобразить честно: уникальный индекс по `mutation_id`, `−1` от
 * подавленной вставки и — главное — откат всей транзакции при исключении внутри неё. Ради этого
 * последнего правило AD-23 и существует: списание, показанное игроку сделанным, обязано лечь на
 * диск вместе со своей записью очереди или не лечь вовсе.
 *
 * Владелец решил 2026-09-03, что локальная половина у этой операции есть: нолики — мягкая валюта,
 * и AD-25 остаётся про настоящие серверные поля.
 */
class LessonUnlockLocalHalfTest {

    private lateinit var db: AppDatabase
    private lateinit var stats: UserStatsDao
    private lateinit var outbox: OutboxDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        stats = db.userStatsDao()
        outbox = db.outboxDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `charge and queue row land together`() = runTest {
        stats.upsert(profile(nolics = 100L))

        val result = stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, priceNolics = 53L, outboxRow = row())

        assertEquals(LessonUnlockLocalResult.Opened, result)
        val saved = stats.findByUid(UID)!!
        assertEquals(47L, saved.nolics)
        assertEquals(setOf(UNLOCK_KEY), saved.lessonUnlocks)
        val queued = outbox.findByMutationId(MUTATION_ID)
        assertNotNull("списание без своей записи очереди — молчаливая потеря (AD-23)", queued)
        assertEquals(OutboxState.WAITING.name, queued!!.state)
        assertEquals("lesson.LESSON:$LESSON_ID", queued.entityRef)
    }

    /**
     * Отказ на строке очереди роняет транзакцию целиком — вместе со списанием.
     *
     * Строка из будущей версии клиента (состояние, которого этот код не знает) — единственный
     * способ занять ключ так, чтобы вставка была подавлена, а разобрать её было нечем. Игрок
     * должен увидеть отказ, а не открытый урок, за который никто не заплатит.
     */
    @Test
    fun `a refused queue row leaves no charge behind`() = runTest {
        stats.upsert(profile(nolics = 100L))
        outbox.insert(row().copy(state = "FROM_A_FUTURE_VERSION"))

        val failure = runCatching { stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row()) }

        assertTrue("занятый нечитаемой строкой ключ обязан быть слышен", failure.isFailure)
        val saved = stats.findByUid(UID)!!
        assertEquals("списание не должно пережить отказ", 100L, saved.nolics)
        assertEquals(emptySet<String>(), saved.lessonUnlocks)
    }

    @Test
    fun `not enough nolics queues nothing at all`() = runTest {
        stats.upsert(profile(nolics = 10L))

        val result = stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, priceNolics = 53L, outboxRow = row())

        assertEquals(LessonUnlockLocalResult.NotEnoughNolics(balanceNolics = 10L, priceNolics = 53L), result)
        assertNull("отправлять нечего: отказ снимается пополнением, а не связью", outbox.findByMutationId(MUTATION_ID))
        assertEquals(10L, stats.findByUid(UID)!!.nolics)
    }

    @Test
    fun `a second tap on an open lesson charges nothing`() = runTest {
        stats.upsert(profile(nolics = 100L))
        stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row())

        val again = stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row())

        assertEquals(LessonUnlockLocalResult.AlreadyOpen, again)
        assertEquals(47L, stats.findByUid(UID)!!.nolics)
        assertEquals(1, outbox.countPending(UID))
    }

    @Test
    fun `quarantine rollback gives the nolics back once`() = runTest {
        stats.upsert(profile(nolics = 100L))
        stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row())

        assertTrue(stats.rollbackLessonUnlock(UID, UNLOCK_KEY, refundNolics = 53L))
        assertEquals(false, stats.rollbackLessonUnlock(UID, UNLOCK_KEY, refundNolics = 53L))

        val saved = stats.findByUid(UID)!!
        assertEquals(100L, saved.nolics)
        assertEquals(emptySet<String>(), saved.lessonUnlocks)
    }

    /**
     * Карантинная строка держит ключ; повторное нажатие обязано её снять и купить заново.
     *
     * Ключ выводится из тройки «владелец, вид, урок», другим у нового намерения быть не может
     * (AD-2). Не снимай транзакция терминальную строку — списание осталось бы без своей записи, и
     * урок был бы заперт навсегда.
     */
    @Test
    fun `a quarantined row does not lock the lesson forever`() = runTest {
        stats.upsert(profile(nolics = 100L))
        stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row())
        val stuck = outbox.findByMutationId(MUTATION_ID)!!
        outbox.applyDecision(stuck.id, OutboxState.QUARANTINED.name, 0L, 5, "Refused by server", null)
        stats.rollbackLessonUnlock(UID, UNLOCK_KEY, 53L)

        val again = stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row())

        assertEquals(LessonUnlockLocalResult.Opened, again)
        val requeued = outbox.findByMutationId(MUTATION_ID)!!
        assertEquals(OutboxState.WAITING.name, requeued.state)
        assertEquals("новая попытка начинается с нуля, а не с чужих пяти", 0, requeued.attemptCount)
        assertEquals(47L, stats.findByUid(UID)!!.nolics)
    }

    /**
     * Синхронизация профиля, пришедшая раньше доставки, не отменяет локальное открытие.
     *
     * Правило слияния здесь то же, что применяет `UserStatsRepositoryImpl.refreshProfile`: набор
     * купленного монотонен, и обе стороны складываются. Иначе снимок сервера, ещё не знающий о
     * покупке, закрыл бы урок, который игрок уже открыл, — а запись очереди при этом лежит и
     * уедет (AD-30: ресинк лечит только сторону чтения и не трогает уже применённые эффекты).
     */
    @Test
    fun `a profile refresh in flight does not shut the lesson again`() = runTest {
        stats.upsert(profile(nolics = 100L))
        stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row())

        // Снимок сервера, снятый до того, как покупка доехала.
        val fromServer = profile(nolics = 100L)
        val held = stats.findByUid(UID)!!.lessonUnlocks
        stats.upsert(fromServer.copy(lessonUnlocks = held + fromServer.lessonUnlocks))

        assertEquals(setOf(UNLOCK_KEY), stats.findByUid(UID)!!.lessonUnlocks)
        assertNotNull("запись очереди пережила чтение и уедет", outbox.findByMutationId(MUTATION_ID))
    }

    /**
     * Сервер подтвердил — состояния сходятся, а не складываются.
     *
     * Снимок после применения уже несёт и списание, и ключ. Локальная сторона второй раз не
     * списывает: она вообще не считает арифметику на синхронизации, а нажатие по открытому уроку
     * отвечает «уже открыт» (AD-2, идемпотентность приёмника из AD-1).
     */
    @Test
    fun `a confirmed purchase converges instead of charging twice`() = runTest {
        stats.upsert(profile(nolics = 100L))
        stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row())
        outbox.delete(outbox.findByMutationId(MUTATION_ID)!!.id)

        // Снимок сервера после применения мутации: списано ровно один раз.
        val applied = profile(nolics = 47L).copy(lessonUnlocks = setOf(UNLOCK_KEY))
        val held = stats.findByUid(UID)!!.lessonUnlocks
        stats.upsert(applied.copy(lessonUnlocks = held + applied.lessonUnlocks))

        val saved = stats.findByUid(UID)!!
        assertEquals(47L, saved.nolics)
        assertEquals(setOf(UNLOCK_KEY), saved.lessonUnlocks)
        assertEquals(LessonUnlockLocalResult.AlreadyOpen, stats.unlockLessonWithOutbox(UID, UNLOCK_KEY, 53L, row()))
        assertEquals(47L, stats.findByUid(UID)!!.nolics)
    }

    private fun row() =
        OutboxEntity(
            mutationId = MUTATION_ID,
            ownerUid = UID,
            operation = OutboxOperations.UNLOCK_LESSON,
            payload = """{"lessonId":"$LESSON_ID","kind":"lesson","chargedNolics":53}""",
            entityRef = "lesson.LESSON:$LESSON_ID",
            expectedVersion = null,
            state = OutboxState.WAITING.name,
            attemptCount = 0,
            nextRetryAtMs = 0L,
            lastError = null,
            createdAtMs = 1_000L,
        )

    private fun profile(nolics: Long) =
        UserStatsEntity(
            uid = UID,
            nickname = "player",
            avatarUrl = null,
            hasPremium = false,
            streakDays = 0,
            stars = 0L,
            nolics = nolics,
            standardHearts = 0,
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

    private companion object {
        const val UID = "user-1"
        const val LESSON_ID = "lesson-1"
        const val UNLOCK_KEY = "lesson:$LESSON_ID"
        val MUTATION_ID = OutboxOperations.mutationKey(OutboxOperations.UNLOCK_LESSON, "$UID-lesson-$LESSON_ID")
    }
}
