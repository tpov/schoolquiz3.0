package com.tpov.schoolquiz.shared.core.scoring

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Расчёт заявок — одна формула на двух языках.
 *
 * Клиент показывает игроку, за что взяли и что поднялось до `9`; списывает сервер
 * (`functions/charge-claims.js`). Разойдись они — игрок увидел бы один результат, а сервер сохранил
 * другой, и объяснить это было бы нечем. Набор фикстур порождён сервером и читается здесь.
 *
 * JVM-тест, а не общий: файл с диска общий код не видит.
 */
class ChargeClaimParityTest {

    @Serializable
    private data class Settled(
        val codeAnswer: String,
        val paid: List<Int>,
        val unpaid: List<Int>,
        val standardChargesPaid: Int,
        val plasmaChargesPaid: Int,
    )

    @Serializable
    private data class Case(
        val name: String,
        val mask: String,
        val codeAnswer: String,
        val standard: Int,
        val plasma: Int,
        val difficulty: String,
        val order: List<Int>? = null,
        val fault: String? = null,
        val settled: Settled? = null,
    )

    private val fixtures: List<Case> by lazy {
        val file = File("../../../config/charge-claim-fixtures.json")
        assertTrue(file.exists(), "нет общего набора фикстур: ${file.absolutePath}")
        Json { ignoreUnknownKeys = true }.decodeFromString<List<Case>>(file.readText())
    }

    @Test
    fun `given the shared fixtures then every settlement matches the server`() {
        assertTrue(fixtures.isNotEmpty(), "набор фикстур пуст — значит проверять нечего")

        val mismatches =
            fixtures.mapNotNull { case ->
                val expected = case.settled ?: return@mapNotNull null
                val mask = ChargeClaimMask(case.mask)
                val settled =
                    settleClaims(
                        mask = mask,
                        codeAnswer = CodeAnswer(case.codeAnswer),
                        standardAvailable = case.standard,
                        plasmaAvailable = case.plasma,
                        order = case.order ?: mask.raw.indices.toList(),
                    )
                val ours = Settled(settled.codeAnswer.raw, settled.paid, settled.unpaid, settled.standardChargesPaid, settled.plasmaChargesPaid)
                if (ours == expected) null else "${case.name}: сервер $expected, клиент $ours"
            }

        assertEquals(emptyList(), mismatches, "клиент и сервер считают заявки по-разному:\n" + mismatches.joinToString("\n"))
    }

    @Test
    fun `given the shared fixtures then every refusal is refused here for the same reason`() {
        // Клиент, который принял бы маску, отвергнутую сервером, показал бы игроку оплату, которой
        // не будет. Символ вне алфавита клиент не строит вовсе — это единственная поломка, которую
        // сервер называет, а клиент не может воспроизвести.
        val mismatches =
            fixtures.filter { it.fault != null && it.fault != "BAD_CHAR" }.mapNotNull { case ->
                val ours = ChargeClaimMask(case.mask).validateAgainst(CodeAnswer(case.codeAnswer), Difficulty.valueOf(case.difficulty))
                if (ours?.name == case.fault) null else "${case.name}: сервер ${case.fault}, клиент $ours"
            }

        assertEquals(emptyList(), mismatches, mismatches.joinToString("\n"))
    }
}
