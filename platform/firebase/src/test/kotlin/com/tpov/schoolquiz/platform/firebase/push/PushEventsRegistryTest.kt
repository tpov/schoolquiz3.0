package com.tpov.schoolquiz.platform.firebase.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Клиент и сервер называют события push одинаково.
 *
 * Реестр на сервере — единственный источник (`functions/push-notifications.js`); клиентская копия
 * нужна только чтобы решить, показывать ли уведомление и какими словами. Незнакомое имя здесь не
 * ошибка, а «синхронизироваться молча» — то есть событие, ради которого push и заводили, доедет
 * без слов, и ловить это в бою нечем. Первое расхождение уже случилось: сервер писал
 * `qualification.AUTHOR_RATING_CHANGED`, клиент ждал `profile.QUALIFICATION_CHANGED`, и каждая
 * сторона проверяла только себя.
 *
 * Читается исходник, как в `OutboxOperationsRegistryTest`: сервер на JVM не поднять.
 */
class PushEventsRegistryTest {
    private val source: String by lazy {
        val file = File("../../functions/push-notifications.js")
        assertTrue("нет серверного реестра: ${file.absolutePath}", file.exists())
        file.readText()
    }

    /** Все литералы `eventType: "<фича>.<СОБЫТИЕ>"` в реестре `PUSH_EVENTS`. */
    private val serverEvents: Set<String> by lazy {
        val body =
            Regex("""const PUSH_EVENTS = Object\.freeze\(\{(.*?)\n\}\);""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.get(1)
        assertTrue("не нашёл реестр PUSH_EVENTS в push-notifications.js", body != null)
        Regex("""eventType:\s*"([A-Za-z0-9_.]+)"""").findAll(body!!).map { it.groupValues[1] }.toSet()
    }

    private val clientEvents: Set<String> = PushEvents.all.map { it.eventType }.toSet()

    @Test
    fun `every event the server sends is known to the client`() {
        val silent = serverEvents - clientEvents
        assertEquals(
            "эти события доедут без уведомления: $silent; клиент знает: $clientEvents",
            emptySet<String>(),
            silent,
        )
    }

    @Test
    fun `every event the client shows is one the server sends`() {
        val dead = clientEvents - serverEvents
        assertEquals("события, которых сервер не шлёт, — мёртвые строки: $dead", emptySet<String>(), dead)
    }

    @Test
    fun `the server registry is not empty`() {
        assertTrue(serverEvents.isNotEmpty())
    }
}
