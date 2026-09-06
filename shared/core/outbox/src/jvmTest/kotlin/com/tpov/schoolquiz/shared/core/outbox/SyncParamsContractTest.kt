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
 * объявление живёт в `functions/config/sync-params.json`: сервер читает его `require`'ом, а клиент
 * держит копию в [OutboxLimits], и этот тест — то, что делает копию копией, а не вторым мнением. Он
 * падает в `ciCheck`, то есть до того, как расхождение доедет до устройства.
 *
 * Почему объявление лежит внутри `functions/`: `firebase.json` упаковывает при деплое ровно этот
 * каталог, и файл, лежащий рядом с ним, в облако не уезжает — серверный модуль очереди тогда не
 * загружается вовсе. Числа обязаны ехать вместе с кодом, который их читает; со стороны сервера то
 * же самое стережёт `functions/deploy-package.test.js`.
 *
 * Почему это важно ровно здесь: запись, пережившая свой предельный возраст и всё же уехавшая на
 * сервер, будет для него новой операцией — то самое двойное применение, ради запрета которого
 * ключ идемпотентности и существует (AD-1).
 */
class SyncParamsContractTest {

    private val paramsFile = File("../../../functions/config/sync-params.json")

    /**
     * Молчаливого пропуска здесь быть не может.
     *
     * Прежняя версия начинала каждый случай с `if (!paramsFile.exists()) return`, и страж, которому
     * переставали быть виден файл, становился зелёным вместо красного — ровно в тот момент, когда
     * объявление уехало бы в другое место, а копия на клиенте осталась бы прежней.
     */
    private fun params(): File {
        assertTrue(
            paramsFile.exists(),
            "не найден ${paramsFile.absolutePath}: единственное объявление сроков синхронизации " +
                "переехало, а клиентская копия осталась несверенной",
        )
        return paramsFile
    }

    private fun param(name: String): Long {
        val body = params().readText()
        val match = Regex(""""$name"\s*:\s*(\d+)""").find(body)
        assertTrue(match != null, "в ${paramsFile.path} нет числа $name")
        return match!!.groupValues[1].toLong()
    }

    @Test
    fun `client max age matches the single declaration`() {
        assertEquals(
            param("queueRecordMaxAgeMs"),
            OutboxLimits().maxAgeMs,
            "OutboxLimits.maxAgeMs разошёлся с functions/config/sync-params.json — правь файл, " +
                "а не константу",
        )
    }

    @Test
    fun `key retention is strictly longer than the record it has to outlive`() {
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
        params()

        val serverModule = File("../../../functions/mutation-queue.js")
        assertTrue(serverModule.exists(), "нет ${serverModule.path}")
        assertTrue(
            serverModule.readText().contains("""require("./config/sync-params.json")"""),
            "серверный приёмник обязан читать общее объявление ИЗ СВОЕГО ПАКЕТА: путь наружу " +
                "каталога functions работает локально и не работает в облаке — там не загрузится " +
                "весь кодбейс, а не одна функция",
        )
    }
}
