package com.tpov.schoolquiz.shared.feature.quest_authoring.domain

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.PrivateQuizSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Дефолты и валидация настроек приватной ссылки — доменное слово AD-29 (история 8.3).
 *
 * Дефолты нового приватного квиза приняты владельцем и живут в модели, а не в UI: 1 попытка,
 * результат скрыт, ссылка без конечной даты. Загрузка существующего квиза сохраняет его
 * настройки — это значит, что модель не «чинит» их и не подставляет дефолты заново.
 */
class PrivateQuizSettingsTest {

    @Test
    fun defaults_areTheAd29Ones() {
        assertEquals(1, PrivateQuizSettings.DEFAULTS.attemptLimit)
        assertFalse(PrivateQuizSettings.DEFAULTS.showResult)
        assertNull(PrivateQuizSettings.DEFAULTS.linkExpiresAtMs)
    }

    @Test
    fun limit_belowOne_refuses() {
        val error = assertFailsWith<IllegalArgumentException> { PrivateQuizSettings(attemptLimit = 0) }
        assertEquals(true, error.message!!.contains("attemptLimit"))
    }

    @Test
    fun negativeExpiry_refuses() {
        assertFailsWith<IllegalArgumentException> { PrivateQuizSettings(linkExpiresAtMs = -5L) }
    }

    @Test
    fun validateForSave_acceptsNoExpiry_andFutureOnes_only() {
        val now = 1_000_000L
        assertTrue(PrivateQuizSettings(linkExpiresAtMs = null).validateForSave(now), "без срока — всегда можно")
        assertTrue(
            PrivateQuizSettings(linkExpiresAtMs = now + 1).validateForSave(now),
            "будущий срок — можно",
        )
        assertFalse(
            PrivateQuizSettings(linkExpiresAtMs = now).validateForSave(now),
            "срок «в момент сохранения» — уже не в будущем",
        )
        assertFalse(
            PrivateQuizSettings(linkExpiresAtMs = now - 1).validateForSave(now),
            "прошедший срок на сохранении отказывает",
        )
    }

    @Test
    fun higherLimits_areLegal() {
        assertEquals(5, PrivateQuizSettings(attemptLimit = 5).attemptLimit)
    }
}
