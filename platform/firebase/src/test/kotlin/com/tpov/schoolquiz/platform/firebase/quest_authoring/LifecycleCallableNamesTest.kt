package com.tpov.schoolquiz.platform.firebase.quest_authoring

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val file = File("../../../functions/index.js")
        assertTrue(file.exists(), "нет functions/index.js: ${file.absolutePath}")
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
            assertEquals(method, found, "exports.$callable обязан диспатчить метод \"$method\"")
        }
    }

    @Test
    fun `curator transition rides the arena service transition callable`() {
        val line = Regex("""exports\.setLessonPlacement = onCall\(CALLABLE_OPTIONS, \(request\) => callArenaService\("(\w+)", request\)\);""").find(source)
        assertEquals("transitionLesson", line?.groupValues?.get(1), "exports.setLessonPlacement обязан вести в transitionLesson")
    }
}
