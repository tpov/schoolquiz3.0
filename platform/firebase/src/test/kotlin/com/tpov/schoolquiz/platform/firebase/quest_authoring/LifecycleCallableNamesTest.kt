package com.tpov.schoolquiz.platform.firebase.quest_authoring

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Клиент и сервер называют callable-поверхность TZ06B одинаково.
 *
 * Четыре читающих view и кураторский переход зарегистрированы в `functions/index.js`; клиент
 * стреляет по строке имени, и расхождение в бою выглядит как обычный NOT_FOUND сервера. Поэтому
 * реестр читается текстом — тот же приём, что у `OutboxOperationsRegistryTest`.
 *
 * Имя [LifecycleCallableNames] внутреннее; тест живёт в том же модуле и видит его.
 */
class LifecycleCallableNamesTest {
    private val source: String by lazy {
        val file = File("../../functions/index.js")
        assertTrue("нет functions/index.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    /** Callable → метод внутри `callArenaLifecycleView`, который за ним закреплён. */
    private val viewCallables =
        mapOf(
            LifecycleCallableNames.LIST_OWNED to "listOwned",
            LifecycleCallableNames.LIST_CURATOR to "listCurator",
            LifecycleCallableNames.LIST_HISTORY to "listHistory",
            LifecycleCallableNames.LIST_ROYALTY to "listMyRoyalty",
        )

    @Test
    fun `every lifecycle view the client reads is registered under its method`() {
        viewCallables.forEach { (callable, method) ->
            val found =
                Regex("""exports\.$callable = onCall\(LIFECYCLE_CALLABLE_OPTIONS, \(request\) =>\s*\n\s*callArenaLifecycleView\("([A-Za-z]+)", request\)\);""")
                    .find(source)
                    ?.groupValues
                    ?.get(1)
            assertEquals("exports.$callable обязан диспатчить метод \"$method\"", method, found)
        }
    }

    @Test
    fun `curator transition rides the arena service transition callable`() {
        val line = Regex("""exports\.setLessonPlacement = onCall\(CALLABLE_OPTIONS, \(request\) => callArenaService\("(\w+)", request\)\);""").find(source)
        assertEquals("exports.setLessonPlacement обязан вести в transitionLesson", "transitionLesson", line?.groupValues?.get(1))
    }
}
