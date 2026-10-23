package com.tpov.schoolquiz.platform.firebase.economy

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Клиент и сервер называют callable-поверхность собственной истории одинаково (AD-30, 8.6).
 *
 * Оба чтения зарегистрированы в `functions/index.js`; клиент стреляет по строке имени, и
 * расхождение в бою выглядит как обычный NOT_FOUND. Реестр читается текстом — тот же приём,
 * что у [com.tpov.schoolquiz.platform.firebase.quest_authoring.LifecycleCallableNamesTest].
 */
class OwnHistoryCallableNamesTest {
    private val source: String by lazy {
        val file = File("../../functions/index.js")
        assertTrue("нет functions/index.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `own history callables the client fires are registered`() {
        assertTrue(
            "exports.listMyOperationHistory не найден — история операций стреляет в пустоту",
            source.contains("exports.listMyOperationHistory = onCall("),
        )
        assertTrue(
            "exports.listMyLessonEarnings не найден — «Начисления» урока стреляют в пустоту",
            source.contains("exports.listMyLessonEarnings = onCall("),
        )
    }

    @Test
    fun `both callables dispatch the lifecycle view family methods`() {
        assertEquals(
            "listMyOperationHistory обязан диспатчить listMyHistory семьи TZ06B",
            "listMyHistory",
            Regex("""exports\.listMyOperationHistory = onCall\(LIFECYCLE_CALLABLE_OPTIONS, \(request\) =>\s*\n\s*callArenaLifecycleView\("([A-Za-z]+)", request\)\);""")
                .find(source)?.groupValues?.get(1),
        )
        assertEquals(
            "listMyLessonEarnings обязан диспатчить listMyLessonEarnings семьи TZ06B",
            "listMyLessonEarnings",
            Regex("""exports\.listMyLessonEarnings = onCall\(LIFECYCLE_CALLABLE_OPTIONS, \(request\) =>\s*\n\s*callArenaLifecycleView\("([A-Za-z]+)", request\)\);""")
                .find(source)?.groupValues?.get(1),
        )
    }

    @Test
    fun `the client constants carry exactly the registered names`() {
        assertEquals("listMyOperationHistory", OwnHistoryCallableNames.HISTORY)
        assertEquals("listMyLessonEarnings", OwnHistoryCallableNames.LESSON_EARNINGS)
    }
}
