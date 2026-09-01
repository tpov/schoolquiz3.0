package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Страж одного вывода ключа на две стороны.
 *
 * Ключ перенесённой строки собирает SQL миграции 5 → 6, а ключ новой — `OutboxOperations` на
 * Kotlin. Это два разных языка и два разных файла, дающих одну строку; разъедутся — и одно и то
 * же действие уедет на сервер дважды под разными ключами, ровно то, от чего ключ и защищает
 * (AD-2). Проверка файловая по образцу `TypeConvertersPhase02Test`: общего кода у этих двух
 * сторон нет и быть не может.
 *
 * Здесь же сверяются имена операций: `operation` — часть контракта с сервером, и незнакомое имя
 * приёмник отвергает окончательно.
 */
class MigrationKeyContractTest {

    @Test
    fun migrationDerivesTheSameMutationKeyAsTheWriters() {
        val source = migrationSource() ?: return

        val expected =
            mapOf(
                "KEY_SUBMIT_ATTEMPT" to OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "X").dropLast(1),
                "KEY_SUBMIT_RATING" to OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, "X").dropLast(1),
                "KEY_SUBMIT_ARENA" to OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ARENA, "X").dropLast(1),
            )

        expected.forEach { (constant, prefix) ->
            assertEquals(
                "Приставка ключа в миграции 5 → 6 разошлась с OutboxOperations.mutationKey",
                prefix,
                source.constantValue(constant),
            )
        }
    }

    @Test
    fun migrationNamesTheSameOperationsAsTheWriters() {
        val source = migrationSource() ?: return

        assertEquals(OutboxOperations.SUBMIT_ATTEMPT, source.constantValue("OPERATION_SUBMIT_ATTEMPT"))
        assertEquals(OutboxOperations.SUBMIT_RATING, source.constantValue("OPERATION_SUBMIT_RATING"))
        assertEquals(OutboxOperations.SUBMIT_ARENA, source.constantValue("OPERATION_SUBMIT_ARENA"))
    }

    @Test
    fun everyOperationNameIsAcceptableInsideAMutationKey() {
        // Сервер принимает ключ только из [A-Za-z0-9_-]: точка в имени операции обязана быть
        // заменена, иначе приёмник отвергнет каждую перенесённую запись как некорректную.
        val allowed = Regex("^[A-Za-z0-9_-]+$")
        listOf(
            OutboxOperations.SUBMIT_ATTEMPT,
            OutboxOperations.SUBMIT_RATING,
            OutboxOperations.SUBMIT_ARENA,
            OutboxOperations.UNLOCK_LESSON,
        ).forEach { operation ->
            val key = OutboxOperations.mutationKey(operation, "0123abc-DEF_9")
            assertTrue("Ключ $key содержит символы, которых приёмник не принимает", allowed.matches(key))
        }
    }

    private fun migrationSource(): String? =
        File("src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/Migration5to6.kt")
            .takeIf { it.exists() }
            ?.readText()

    /** Значение `private const val NAME = "..."` — читается из исходника, а не из класса. */
    private fun String.constantValue(name: String): String {
        val match = Regex("""$name\s*=\s*"([^"]*)"""").find(this)
        return requireNotNull(match) { "Константа $name в Migration5to6.kt не найдена" }.groupValues[1]
    }
}
