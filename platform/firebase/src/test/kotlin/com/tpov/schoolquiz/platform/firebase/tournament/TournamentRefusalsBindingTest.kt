package com.tpov.schoolquiz.platform.firebase.tournament

import com.tpov.schoolquiz.shared.core.network.SyncError
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Причины, названные сервером, обязаны быть классифицированы по имени.
 *
 * `courseTournamentFailure` (`functions/index.js`) кладёт структурную причину в `details.reason`,
 * а `tournamentSyncError` решает по ней ветвь [SyncError]. Разрыв таблиц — это либо повтор
 * безнадёжного отказа (причина не признана и уехала в «неизвестно»), либо вечный карантин
 * чинимого. Ловится текстом обеих сторон (стиль `OutboxOperationsRegistryTest`): каждая причина,
 * которую серверная таблица называет по имени, обязана быть названа и в клиентской — безмолвный
 * `else` годится только для причин будущего сервера, которых ещё никто не видел.
 *
 * L8: меряется не только тело маппера, но и эмиттеры — каждый `fail("ИМЯ"`-литерал шести
 * серверных модулей турнира обязан быть классифицирован клиентской таблицей: переименование
 * причины на сервере больше не проходит молча (ретро эпика 5, P1).
 */
class TournamentRefusalsBindingTest {
    private val serverSource: String by lazy {
        val file = File(findRepoRoot(), "functions/index.js")
        assertTrue("нет приёмника/коллблов: ${file.absolutePath}", file.exists())
        file.readText()
    }

    private val clientSource: String by lazy {
        val file =
            File(
                findRepoRoot(),
                "platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/tournament/" +
                    "TournamentRefusals.kt",
            )
        assertTrue("нет клиентской таблицы причин: ${file.absolutePath}", file.exists())
        file.readText()
    }

    /** Тело `courseTournamentFailure` — таблица причина -> HttpsError-код. */
    private val failureBody: String by lazy {
        val body =
            serverSource
                .substringAfter("function courseTournamentFailure")
                .substringBefore("return new HttpsError")
        assertTrue("не нашёл тело courseTournamentFailure в functions/index.js", body.contains("SERVER_FAILURE"))
        body
    }

    /** Имена причин: строки КАПСОМ; хвост-подчёркивание — семейство-префикс (`INCONSISTENT_`). */
    private val serverReasons: Set<String> by lazy {
        Regex("\"([A-Z][A-Z_]{0,63})\"")
            .findAll(failureBody)
            .map { it.groupValues[1] }
            .toSet()
    }

    /** Причины, названные по имени в клиентской таблице. */
    private val classified: Set<String> by lazy {
        Regex("\"([A-Z][A-Z_]{0,63})\"")
            .findAll(clientSource)
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `every reason the server failure names by name is classified by name in the client table`() {
        assertTrue("таблица сервера опустела: $serverReasons", serverReasons.size > 5)
        val silent =
            serverReasons.filterNot { reason ->
                (reason.endsWith("_") && clientSource.contains(reason)) || reason in classified
            }
        assertEquals(
            "сервер называет причину, а клиентская таблица роняет её в безмолвный else: $silent",
            emptySet<String>(),
            silent.toSet(),
        )
    }

    @Test
    fun `the client else branch stays the designated home of genuinely future reasons`() {
        // Ветвь «причина будущего сервера» обязана существовать и отвечать Unknown, а не Refused.
        val elseBranch = clientSource.substringAfterLast("else ->")
        assertTrue("else обязан оставаться «неизвестно»: $elseBranch", elseBranch.contains("SyncError.Unknown()"))
        // И семейство-префикс классифицировано членами, а не совпадением подстроки.
        for (family in
            listOf(
                "INCONSISTENT_SESSION",
                "INCONSISTENT_ESCROW",
                "INCONSISTENT_ADMISSION",
                "INCONSISTENT_PREPARATION",
            )
        ) {
            assertTrue("член семейства $family не назван в клиентской таблице", family in classified)
        }
    }

    @Test
    fun `every fail literal across the six server modules is classified by name in the client table`() {
        // L8: эмиттеры, а не маппер — `fail("ИМЯ"` в любом из шести турнирных модулей обязан
        // находить ветвь клиентской таблицы; молчаливый else — только для будущего сервера.
        val modules =
            listOf(
                "tournament-service.js",
                "tournament-session.js",
                "tournament-policy.js",
                "tournament-source-bank.js",
                "tournament-bank.js",
                "tournament-worker.js",
            )
        val emitted = sortedMapOf<String, MutableSet<String>>()
        for (module in modules) {
            val file = File(findRepoRoot(), "functions/$module")
            assertTrue("нет серверного модуля: ${file.absolutePath}", file.exists())
            for (name in failLiterals(file.readText())) {
                emitted.getOrPut(name) { mutableSetOf() }.add(module)
            }
        }
        assertTrue("эмиттеры шести модулей опустели — проверь список модулей: $emitted", emitted.size >= 20)
        val silent =
            emitted.keys.filterNot { reason ->
                (reason.endsWith("_") && clientSource.contains(reason)) || reason in classified
            }
        assertEquals(
            "серверный модуль называет причину fail-литералом, а клиентская таблица её не знает: " +
                silent.map { reason -> "$reason (${emitted[reason]?.joinToString()})" },
            emptyList<String>(),
            silent,
        )
    }

    /** Причины, названные сервером, обязаны быть классифицированы по имени. */
    private fun failLiterals(source: String): Set<String> =
        Regex("fail\\(\"([A-Z][A-Z_]{0,63})\"")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `permanent prepare and create refusals are Refused, never retryable unknown`() {
        for (reason in listOf("SOURCE_NOT_ELIGIBLE", "RESOURCE_EXHAUSTED")) {
            val error = tournamentSyncError("failed-precondition", mapOf("reason" to reason), null, causedByIo = false)
            assertEquals("причина $reason повтором не лечится", SyncError.Refused(reason), error)
        }
        // L2: типизированные причины допуска теперь реально доезжают до клиента именем —
        // обе обязаны быть Refused, а не безмолвным Unknown.
        for (reason in listOf("NO_ADMISSION_BASIS", "OUTSIDE_QUALIFICATION_WINDOW")) {
            val error = tournamentSyncError("failed-precondition", mapOf("reason" to reason), null, causedByIo = false)
            assertEquals("причина допуска $reason обязана быть поимённым отказом", SyncError.Refused(reason), error)
        }
    }

    private fun findRepoRoot(): File =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
}
