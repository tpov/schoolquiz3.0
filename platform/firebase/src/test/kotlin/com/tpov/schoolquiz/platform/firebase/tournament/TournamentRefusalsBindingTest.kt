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
    fun `permanent prepare and create refusals are Refused, never retryable unknown`() {
        for (reason in listOf("SOURCE_NOT_ELIGIBLE", "RESOURCE_EXHAUSTED")) {
            val error = tournamentSyncError("failed-precondition", mapOf("reason" to reason), null, causedByIo = false)
            assertEquals("причина $reason повтором не лечится", SyncError.Refused(reason), error)
        }
    }

    private fun findRepoRoot(): File =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
}
