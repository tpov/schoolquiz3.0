package com.tpov.schoolquiz.shared.core.scoring

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Копия контракта в ресурсах — ТОТ ЖЕ файл, что канонический.
 *
 * `src/jvmTest/resources/assessment-contract-fixtures.json` — ручная копия прибора из
 * `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/`.
 * Без проверки равенства паритет мог бы молча сверяться с протухшим оракулом: прибор поправят,
 * копия останется старой, и оба набора тестов позеленеют против разных файлов. Этот тест
 * сравнивает копию с каноном, нормализовав JSON (порядок ключей и пробелы не считаются).
 */
class ExamContractFixturesOracleCopyTest {

    private fun findAncestorWith(relative: String): File {
        val start = File(checkNotNull(System.getProperty("user.dir")))
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, relative).isFile }
            ?.let { File(it, relative) }
            ?: fail("не найден $relative от $start")
    }

    @Test
    fun `the resource copy equals the canonical contract file, json-normalized`() {
        val canonical =
            findAncestorWith(
                "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-contract-fixtures.json",
            )
        val copy = File("src/jvmTest/resources/assessment-contract-fixtures.json")
        assertTrue(copy.exists(), "нет копии прибора в ресурсах: ${copy.absolutePath}")

        // Нормализация: тот же разбор, что у паритет-теста, без учёта порядка ключей и пробелов.
        val normalized = Json.parseToJsonElement(canonical.readText()).toString()
        val normalizedCopy = Json.parseToJsonElement(copy.readText()).toString()
        assertTrue(
            normalized == normalizedCopy,
            "копия assessment-contract-fixtures.json в ресурсах протухла против канонического " +
                "прибора ($canonical); синхронизируйте файл — иначе паритет сверяется со старым оракулом",
        )
    }
}
