package com.tpov.schoolquiz.shared.feature.lesson.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.fake.FakeUserStatsDao
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonUnlockOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Постановка разблокировки в очередь вместе с её локальной половиной (история 5.3).
 *
 * До этой работы у записи `lesson.UNLOCK` локальной половины не было вовсе: игрок платил, а урок
 * оставался закрытым до ближайшей синхронизации профиля. Владелец решил 2026-09-03, что так быть
 * не должно — нолики мягкая валюта, и урок открывается сразу.
 */
class OutboxLessonUnlockQueueTest {

    private val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(7_000L) }
    private val lessonId = LessonId("l-42")

    private fun dao(nolics: Long = 100L, unlocks: Set<String> = emptySet()) =
        FakeUserStatsDao(stats(uid = "uid-1", nolics = nolics, unlocks = unlocks))

    private fun queue(dao: FakeUserStatsDao) = OutboxLessonUnlockQueue(dao, clock)

    @Test
    fun `нажатие списывает нолики, открывает урок и кладёт ровно одну запись`() = runTest {
        val dao = dao(nolics = 100L)

        val result = queue(dao).queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)

        assertEquals(LessonUnlockOutcome.Opened, result.getOrNull())
        // Локальная половина: списано и открыто прямо сейчас, до всякой отправки (AD-23).
        val stats = dao.statsOf("uid-1")!!
        assertEquals(47L, stats.nolics)
        assertEquals(setOf("lesson:l-42"), stats.lessonUnlocks)
        // И вторая половина — намерение в очереди, той же транзакцией.
        val row = dao.outboxRows.single()
        assertEquals("lesson.UNLOCK", row.operation)
        assertEquals("lesson.LESSON:l-42", row.entityRef)
        assertEquals("uid-1", row.ownerUid)
        assertEquals(OutboxState.WAITING.name, row.state)
        assertEquals(7_000L, row.createdAtMs)
        assertTrue(row.payload.contains("\"lessonId\":\"l-42\""))
        assertTrue(row.payload.contains("\"kind\":\"lesson\""))
    }

    @Test
    fun `второе нажатие по тому же уроку ни второй записи, ни второго списания не создаёт`() = runTest {
        val dao = dao(nolics = 100L)
        val queue = queue(dao)

        queue.queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)
        val second = queue.queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)

        // То же намерение — тот же ключ (AD-2). Урок уже открыт, и платить второй раз не за что.
        assertEquals(LessonUnlockOutcome.AlreadyOpen, second.getOrNull())
        assertEquals(1, dao.outboxRows.size)
        assertEquals(47L, dao.statsOf("uid-1")!!.nolics)
    }

    /** Сервер подтвердил покупку раньше, чем игрок нажал снова: складывать списания нельзя. */
    @Test
    fun `подтверждённая сервером покупка не списывается второй раз`() = runTest {
        val dao = dao(nolics = 100L, unlocks = setOf("lesson:l-42"))

        val result = queue(dao).queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)

        assertEquals(LessonUnlockOutcome.AlreadyOpen, result.getOrNull())
        assertEquals(100L, dao.statsOf("uid-1")!!.nolics)
        assertEquals(emptyList(), dao.outboxRows)
    }

    @Test
    fun `не хватает ноликов — записи в очереди нет, и причина названа своя`() = runTest {
        val dao = dao(nolics = 10L)

        val result = queue(dao).queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)

        assertEquals(LessonUnlockOutcome.NotEnoughNolics(priceNolics = 53, balanceNolics = 10L), result.getOrNull())
        // Отправлять нечего: отказ по балансу снимается пополнением, а не появлением связи.
        assertEquals(emptyList(), dao.outboxRows)
        assertEquals(10L, dao.statsOf("uid-1")!!.nolics)
        assertEquals(emptySet(), dao.statsOf("uid-1")!!.lessonUnlocks)
    }

    @Test
    fun `без профиля на устройстве списывать не с чего`() = runTest {
        val dao = FakeUserStatsDao()

        val result = queue(dao).queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)

        assertEquals(LessonUnlockOutcome.NoProfile, result.getOrNull())
        assertEquals(emptyList(), dao.outboxRows)
    }

    @Test
    fun `карантинная запись не запирает урок навсегда`() = runTest {
        val dao = dao(nolics = 100L)
        val queue = queue(dao)
        queue.queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)
        val key = dao.outboxRows.single().mutationId
        dao.setOutboxState(key, OutboxState.QUARANTINED.name)
        // Откат по карантину: нолики вернулись, урок закрылся (AD-28).
        LessonUnlockQuarantineRollback(dao).onQuarantined(
            record = quarantined(dao, key),
        )

        val again = queue.queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)

        // Ключ выводится из тройки «владелец, вид, урок», другим у нового намерения быть не может.
        // Не снимай мы терминальную строку — списание осталось бы без своей записи очереди.
        assertEquals(LessonUnlockOutcome.Opened, again.getOrNull())
        val row = dao.outboxRows.single()
        assertEquals(OutboxState.WAITING.name, row.state)
        assertEquals(47L, dao.statsOf("uid-1")!!.nolics)
    }

    @Test
    fun `второй аккаунт на том же устройстве получает свою запись`() = runTest {
        val dao = FakeUserStatsDao(stats("uid-1", nolics = 100L))
        dao.upsert(stats("uid-2", nolics = 100L))
        val queue = queue(dao)

        queue.queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)
        queue.queueUnlock("uid-2", lessonId, "lesson", priceNolics = 53)

        // Уникальность `mutation_id` сквозная, поэтому владелец входит в ключ: иначе второй
        // аккаунт получил бы чужую строку, а сервер отверг бы её как ключ другого аккаунта (AD-8).
        assertEquals(2, dao.outboxRows.size)
        assertEquals(setOf("uid-1", "uid-2"), dao.outboxRows.map { it.ownerUid }.toSet())
        assertEquals(2, dao.outboxRows.map { it.mutationId }.distinct().size)
    }

    @Test
    fun `отказанная покупка видна экрану по карантинной строке`() = runTest {
        val dao = dao(nolics = 100L)
        val queue = queue(dao)
        queue.queueUnlock("uid-1", lessonId, "lesson", priceNolics = 53)
        assertEquals(emptySet(), queue.observeRefusedUnlocks("uid-1").first())

        dao.setOutboxState(dao.outboxRows.single().mutationId, OutboxState.QUARANTINED.name)

        // Объяснение живёт в самой очереди, а не во второй таблице пометок: та разошлась бы с ней.
        assertEquals(setOf(lessonId), queue.observeRefusedUnlocks("uid-1").first())
        assertEquals(emptySet(), queue.observeRefusedUnlocks("uid-2").first())
    }

    private suspend fun quarantined(
        dao: FakeUserStatsDao,
        mutationId: String,
    ) = dao.outboxRows.first { it.mutationId == mutationId }.let { row ->
        com.tpov.schoolquiz.shared.core.outbox.OutboxRecord(
            id = row.id,
            mutationId = row.mutationId,
            ownerUid = row.ownerUid,
            operation = row.operation,
            payload = row.payload,
            entityRef = row.entityRef,
            expectedVersion = row.expectedVersion,
            state = OutboxState.QUARANTINED,
            createdAtMs = row.createdAtMs,
        )
    }

    private fun stats(
        uid: String,
        nolics: Long,
        unlocks: Set<String> = emptySet(),
    ) = UserStatsEntity(
        uid = uid,
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
        lessonUnlocks = unlocks,
    )
}
