package com.tpov.schoolquiz.shared.feature.lesson.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxNamespace
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Форма записи очереди для разблокировки урока (история 5.3).
 *
 * Имя операции и ссылка на сущность — часть контракта: имя читает приёмник, ссылку читает экран,
 * который ставит пометку «не отправлено». Пока писателя не существовало вовсе, обе стороны — и
 * реестр операций, и маршрутизатор карантина — выглядели живыми при нулевой реализации.
 */
class LessonUnlockOutboxTest {

    private val lessonId = LessonId("l-42")

    @Test
    fun `запись несёт имя операции и ссылку своей фичи`() {
        val record = LessonUnlockOutbox.record("uid-1", lessonId, "lesson", chargedNolics = 53L, createdAtMs = 1_000L)

        assertEquals("lesson.UNLOCK", record.operation)
        assertEquals("lesson.LESSON:l-42", record.entityRef)
        assertEquals("uid-1", record.ownerUid)
        assertEquals(OutboxState.WAITING, record.state)
        assertEquals(1_000L, record.createdAtMs)
        // Разблокировка не версионируется: сверять на сервере нечего (AD-24).
        assertNull(record.expectedVersion)
    }

    @Test
    fun `операция и ссылка живут в одном пространстве имён`() {
        val record = LessonUnlockOutbox.record("uid-1", lessonId, "lesson", chargedNolics = 53L, createdAtMs = 1_000L)

        // Та же проверка, что стоит у строки таблицы: разъедутся — и пометка встанет на чужую
        // сущность либо не встанет ни на какую (NFR4).
        OutboxNamespace.requireSameNamespace(record.operation, record.entityRef)
        assertEquals("lesson", OutboxNamespace.of(record.operation))
        assertEquals("lesson", OutboxNamespace.of(record.entityRef!!))
    }

    @Test
    fun `тело несёт то, что читает приёмник, и списанное число для отката`() {
        val record =
            LessonUnlockOutbox.record("uid-1", lessonId, "hardMode", chargedNolics = 27L, createdAtMs = 1_000L)

        // `applyLessonUnlock` читает из тела `lessonId` и `kind`. Третье поле сервер не читает
        // вовсе и цену по-прежнему считает сам (AD-3); клиенту оно нужно затем, что откатывать
        // списание придётся спустя дни, когда цену уже не пересчитать по тем же вопросам (AD-28).
        assertEquals("""{"lessonId":"l-42","kind":"hardMode","chargedNolics":27}""", record.payload)
        assertEquals("hardMode", LessonUnlockOutbox.kindOf(record.payload))
        assertEquals(27L, LessonUnlockOutbox.chargedNolicsOf(record.payload))
    }

    @Test
    fun `ключ покупки совпадает с тем, которым отвечает сервер`() {
        // Зеркало `LessonUnlockKind.keyFor` из economy и `unlockDocId` с сервера: разъедутся — и
        // локально открытый урок не сойдётся с набором, который пришлёт синхронизация профиля.
        assertEquals("lesson:l-42", LessonUnlockOutbox.unlockKey("lesson", lessonId))
        assertEquals("hardMode:l-42", LessonUnlockOutbox.unlockKey("hardMode", lessonId))
    }

    @Test
    fun `тело без списанного числа не выдумывает его`() {
        // Строка от прежнего писателя: откатывать наугад нельзя — это выдача ноликов из воздуха.
        assertNull(LessonUnlockOutbox.chargedNolicsOf("""{"lessonId":"l-42","kind":"lesson"}"""))
        assertNull(LessonUnlockOutbox.chargedNolicsOf("не json"))
    }

    @Test
    fun `ключ приёмник принимает и выводит его из владельца, вида покупки и урока`() {
        val key = LessonUnlockOutbox.mutationKey("uid-1", lessonId, "lesson")

        // Сервер принимает ключ только из [A-Za-z0-9_-]: точка имени операции обязана быть заменена.
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(key), "приёмник не примет ключ $key")
        assertEquals("lesson-UNLOCK-uid-1-lesson-l-42", key)
        // Одно и то же намерение даёт один и тот же ключ, разные виды покупки — разные.
        assertEquals(key, LessonUnlockOutbox.mutationKey("uid-1", lessonId, "lesson"))
        assertTrue(key != LessonUnlockOutbox.mutationKey("uid-1", lessonId, "hardMode"))
        // Уникальность `mutation_id` в таблице сквозная, а на сервере ключ принадлежит аккаунту:
        // без владельца в ключе второй аккаунт на том же устройстве получил бы чужую строку (AD-8).
        assertTrue(key != LessonUnlockOutbox.mutationKey("uid-2", lessonId, "lesson"))
    }

    @Test
    fun `урок читается обратно только из своей ссылки`() {
        assertEquals(lessonId, LessonUnlockOutbox.lessonIdOf(LessonUnlockOutbox.entityRef(lessonId)))
        assertNull(LessonUnlockOutbox.lessonIdOf("lesson.LESSON:"))
        assertNull(LessonUnlockOutbox.lessonIdOf("quest.QUEST:l-42"))
        assertNull(LessonUnlockOutbox.lessonIdOf(null))
    }
}
