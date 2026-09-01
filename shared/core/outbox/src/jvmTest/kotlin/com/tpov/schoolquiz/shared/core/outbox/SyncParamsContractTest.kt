package com.tpov.schoolquiz.shared.core.outbox

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Два срока — предельный возраст записи очереди и срок хранения выполненного ключа — обязаны быть
 * одним параметром обеих сторон, а не двумя совпадающими копиями (NFR9).
 *
 * Средства генерировать Kotlin-константы из внешнего файла в проекте нет, поэтому единственное
 * объявление живёт в `config/sync-params.json`: сервер читает его `require`'ом, а клиент держит
 * копию в [OutboxLimits], и этот тест — то, что делает копию копией, а не вторым мнением. Он
 * падает в `ciCheck`, то есть до того, как расхождение доедет до устройства.
 *
 * Почему это важно ровно здесь: запись, пережившая свой предельный возраст и всё же уехавшая на
 * сервер, будет для него новой операцией — то самое двойное применение, ради запрета которого
 * ключ идемпотентности и существует (AD-1).
 */
class SyncParamsContractTest {

    private val paramsFile = File("../../../config/sync-params.json")

    private fun param(name: String): Long {
        val body = paramsFile.readText()
        val match = Regex(""""$name"\s*:\s*(\d+)""").find(body)
        assertTrue(match != null, "в ${paramsFile.path} нет числа $name")
        return match!!.groupValues[1].toLong()
    }

    @Test
    fun `client max age matches the single declaration`() {
        if (!paramsFile.exists()) return // запуск вне дерева проекта

        assertEquals(
            param("queueRecordMaxAgeMs"),
            OutboxLimits().maxAgeMs,
            "OutboxLimits.maxAgeMs разошёлся с config/sync-params.json — правь файл, а не константу",
        )
    }

    @Test
    fun `key retention is strictly longer than the record it has to outlive`() {
        if (!paramsFile.exists()) return

        val maxAge = param("queueRecordMaxAgeMs")
        val ttl = param("mutationKeyTtlMs")

        assertTrue(
            ttl > maxAge,
            "AD-1 нарушен: срок хранения ключа ($ttl мс) обязан быть строго больше предельного " +
                "возраста записи очереди ($maxAge мс), иначе повтор станет новой операцией",
        )
    }

    @Test
    fun `the declaration is reachable from the server side too`() {
        if (!paramsFile.exists()) return

        val serverModule = File("../../../functions/mutation-queue.js")
        assertTrue(serverModule.exists(), "нет ${serverModule.path}")
        assertTrue(
            serverModule.readText().contains("config/sync-params.json"),
            "серверный приёмник перестал читать общее объявление и завёл своё число",
        )
    }
}
