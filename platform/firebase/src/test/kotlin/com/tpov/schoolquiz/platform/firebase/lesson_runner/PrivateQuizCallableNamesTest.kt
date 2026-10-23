package com.tpov.schoolquiz.platform.firebase.lesson_runner

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Клиент и сервер называют callable-поверхность приватного квиза одинаково (AD-29, история 8.4).
 *
 * Дверь, замороженный прогон и собственный результат зарегистрированы в `functions/index.js`;
 * клиент стреляет по строке имени, и расхождение в бою выглядит как обычный NOT_FOUND. Реестр
 * читается текстом — тот же приём, что у `LifecycleCallableNamesTest`.
 */
class PrivateQuizCallableNamesTest {
    private val source: String by lazy {
        val file = File("../../functions/index.js")
        assertTrue("нет functions/index.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `every private quiz callable the client fires is registered`() {
        PrivateQuizCallableNamesTestRegistry.ALL.forEach { callable ->
            assertTrue(
                "exports.$callable не найден в functions/index.js — клиент стреляет в пустоту",
                source.contains("exports.$callable = onCall("),
            )
        }
    }

    @Test
    fun `the start gate refusal carries a machine readable reason in its details`() {
        val body =
            Regex("""exports\.startPrivateQuizAttempt = onCall[\s\S]*?\n}\);""").find(source)?.value
        assertTrue("не нашёл регистрацию startPrivateQuizAttempt", body != null)
        assertTrue(
            "отказ двери обязан ехать с машиночитаемым кодом в деталях — на нём держится типизированный вход",
            body!!.contains("{reason: error.code}"),
        )
    }
}

private object PrivateQuizCallableNamesTestRegistry {
    val ALL =
        listOf(
            PrivateQuizCallableNames.START,
            PrivateQuizCallableNames.FETCH_RUN,
            PrivateQuizCallableNames.READ_RESULT,
        )
}
