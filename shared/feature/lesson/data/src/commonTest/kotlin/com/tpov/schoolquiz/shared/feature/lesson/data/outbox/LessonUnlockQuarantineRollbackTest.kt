package com.tpov.schoolquiz.shared.feature.lesson.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.fake.FakeUserStatsDao
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Карантин разблокировки откатывает обе половины (AD-28).
 *
 * До этой работы здесь стоял `NoLocalEffect`: считалось, что и нолики, и набор `lessonUnlocks`
 * пишет только сервер (AD-25). Владелец решил 2026-09-03 иначе, и молчание на карантин стало бы
 * навсегда открытым уроком, за который сервер никогда не списал.
 */
class LessonUnlockQuarantineRollbackTest {

    private val lessonId = LessonId("l-42")

    @Test
    fun `откат возвращает нолики и закрывает урок`() = runTest {
        val dao = FakeUserStatsDao(stats(nolics = 47L, unlocks = setOf("lesson:l-42")))

        LessonUnlockQuarantineRollback(dao).onQuarantined(record(chargedNolics = 53L))

        val stats = dao.statsOf("uid-1")!!
        assertEquals(100L, stats.nolics)
        assertEquals(emptySet(), stats.lessonUnlocks)
    }

    @Test
    fun `второй откат по той же записи ноликов не выдаёт`() = runTest {
        val dao = FakeUserStatsDao(stats(nolics = 47L, unlocks = setOf("lesson:l-42")))
        val rollback = LessonUnlockQuarantineRollback(dao)

        rollback.onQuarantined(record(chargedNolics = 53L))
        rollback.onQuarantined(record(chargedNolics = 53L))

        // Повторный проход движка по той же строке — не второй возврат: денег из воздуха нет.
        assertEquals(100L, dao.statsOf("uid-1")!!.nolics)
    }

    @Test
    fun `чужую операцию обработчик не трогает`() = runTest {
        val dao = FakeUserStatsDao(stats(nolics = 47L, unlocks = setOf("lesson:l-42")))

        LessonUnlockQuarantineRollback(dao).onQuarantined(
            record(chargedNolics = 53L).copy(
                operation = "lesson_runner.SUBMIT_ATTEMPT",
                entityRef = null,
            ),
        )

        assertEquals(47L, dao.statsOf("uid-1")!!.nolics)
    }

    @Test
    fun `запись без списанного числа ноликов не возвращает`() = runTest {
        val dao = FakeUserStatsDao(stats(nolics = 47L, unlocks = setOf("lesson:l-42")))

        // Строка, положенная кем-то, кто локально ничего не менял: возвращать наугад нельзя.
        LessonUnlockQuarantineRollback(dao).onQuarantined(
            record(chargedNolics = 53L).copy(payload = """{"lessonId":"l-42","kind":"lesson"}"""),
        )

        assertEquals(47L, dao.statsOf("uid-1")!!.nolics)
        assertEquals(setOf("lesson:l-42"), dao.statsOf("uid-1")!!.lessonUnlocks)
    }

    private fun record(chargedNolics: Long) =
        LessonUnlockOutbox
            .record(
                ownerUid = "uid-1",
                lessonId = lessonId,
                kindWireName = "lesson",
                chargedNolics = chargedNolics,
                createdAtMs = 1_000L,
            ).copy(state = OutboxState.QUARANTINED, lastError = "Refused by server")

    private fun stats(
        nolics: Long,
        unlocks: Set<String>,
    ) = UserStatsEntity(
        uid = "uid-1",
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
