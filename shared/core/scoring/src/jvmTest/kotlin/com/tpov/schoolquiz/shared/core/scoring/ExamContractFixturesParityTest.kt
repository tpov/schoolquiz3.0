package com.tpov.schoolquiz.shared.core.scoring

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Kotlin-половина исполняющегося EXAM-TECH-1: тот же файл приборов, что читает
 * `functions/exam-contract-fixtures.test.js`.
 *
 * Файл лежит в ресурсах jvmTest (копия контракта из `_bmad-output/.../assessment-contract-fixtures.json`,
 * обновляется вместе с ним) — общий код не видит файловой системы, и копия на класспате делает
 * прибор входом Gradle-задачи. Отсутствующий прибор — провал, не пропуск: паритет, который тихо
 * перестал сравнивать, хуже отсутствующего.
 *
 * Каждый случай выполняется по имени; расхождение называет случай и падает.
 */
class ExamContractFixturesParityTest {

    private val fixtures: ContractFixtures by lazy { loadFixtures() }

    @Serializable
    private data class ContractFixtures(
        val contractVersion: String,
        val weightedTariff: List<WeightedEndpoint> = emptyList(),
        val rewardCases: List<RewardCase> = emptyList(),
        val worthCases: List<WorthCase> = emptyList(),
        val correctionCases: List<CorrectionCase> = emptyList(),
        val arithmeticOnlyCases: List<RewardCase> = emptyList(),
        val invalidBasisCases: List<InvalidBasisCase> = emptyList(),
    )

    @Serializable
    private data class WeightedEndpoint(val percent: Int, val weighted: Int)

    @Serializable
    private data class ExpectedPayout(
        val nolics: Long,
        val skillPoints: Long,
        val bNext: Int,
    )

    @Serializable
    private data class RewardCase(
        val id: String,
        val mode: String,
        val p: Int,
        val b: Int = 0,
        val TE: Long = 0,
        val TH: Long = 0,
        val expected: ExpectedPayout,
        val arithmeticOnly: Boolean = false,
    )

    @Serializable
    private data class WorthCase(
        val id: String,
        val easySeconds: List<Int>,
        val hardSeconds: List<Int>,
        val drawEasy: Int,
        val drawHard: Int,
        val expected: ExpectedWorth,
    )

    @Serializable
    private data class ExpectedWorth(val TE: Long, val TH: Long, val Q: Long)

    @Serializable
    private data class ExpectedCorrection(
        val state: String? = null,
        val denominator: Int,
        val percent: Int? = null,
        val passed: Boolean? = null,
        val starsTenths: Int? = null,
    )

    @Serializable
    private data class CorrectionCase(
        val id: String,
        val digits: List<Int>,
        val exclude: List<Int> = emptyList(),
        val expected: ExpectedCorrection,
        val capturedPassMark: Int = 60,
    )

    @Serializable
    private data class InvalidBasisCase(
        val id: String,
        val mode: String,
        val TE: Long,
        val TH: Long,
        val expected: String,
    )

    private fun loadFixtures(): ContractFixtures {
        val file = File("src/jvmTest/resources/assessment-contract-fixtures.json")
        assertTrue(file.exists(), "нет файла приборов: ${file.absolutePath}")
        return Json { ignoreUnknownKeys = true }.decodeFromString(
            ContractFixtures.serializer(),
            file.readText(),
        )
    }

    private fun modeOf(raw: String): ExamAssessmentArithmetic.Mode = when (raw) {
        "ThemeTest" -> ExamAssessmentArithmetic.Mode.THEME_TEST
        "FinalExam" -> ExamAssessmentArithmetic.Mode.FINAL_EXAM
        else -> error("неизвестный режим прибора: $raw")
    }

    /** Согласная прибору замороженная основа — TE/TH из случая. */
    private fun basis(c: RewardCase): ExamAssessmentArithmetic.WorthBasis =
        ExamAssessmentArithmetic.WorthBasis(mode = modeOf(c.mode), TE = c.TE, TH = c.TH)

    @Test
    fun `the fixture file is the accepted contract`() {
        assertEquals("EXAM-TECH-1", fixtures.contractVersion)
    }

    @Test
    fun `every weighted tariff endpoint matches the Kotlin bands`() {
        assertTrue(fixtures.weightedTariff.isNotEmpty(), "полосы приборов пусты")
        for (endpoint in fixtures.weightedTariff) {
            assertEquals(
                endpoint.weighted.toLong(),
                ExamAssessmentArithmetic.weightedTariff(endpoint.percent),
                "W(${endpoint.percent})",
            )
        }
        assertEquals(265L, ExamAssessmentArithmetic.weightedTariff(100))
    }

    @Test
    fun `every reward case pays the contract amounts`() {
        assertTrue(fixtures.rewardCases.size >= 13, "приборы награды неполны")
        for (case in fixtures.rewardCases) {
            val payout = ExamAssessmentArithmetic.assessmentReward(
                mode = modeOf(case.mode),
                percent = case.p,
                accountedPercent = case.b,
                basis = basis(case),
            )
            assertEquals(case.expected.nolics, payout.nolics, "${case.id}: nolics")
            assertEquals(case.expected.skillPoints, payout.skillPoints, "${case.id}: skillPoints")
            assertEquals(case.expected.bNext, payout.bNext, "${case.id}: bNext")
        }
    }

    @Test
    fun `every worth case prices the pool by ordinary seconds`() {
        for (case in fixtures.worthCases) {
            assertTrue(case.easySeconds.isNotEmpty(), "${case.id}: пустой EASY пул")
            assertTrue(case.hardSeconds.isNotEmpty(), "${case.id}: пустой HARD пул")
            val te = ExamAssessmentArithmetic.cappedPoolMean(
                case.drawEasy,
                case.easySeconds.sumOf { it.toLong() },
                case.easySeconds.size.toLong(),
            )
            val th = ExamAssessmentArithmetic.cappedPoolMean(
                case.drawHard,
                case.hardSeconds.sumOf { it.toLong() },
                case.hardSeconds.size.toLong(),
            )
            assertEquals(case.expected.TE, te, "${case.id}: TE")
            assertEquals(case.expected.TH, th, "${case.id}: TH")
            assertEquals(case.expected.Q, te + 3 * th, "${case.id}: Q")
        }
    }

    @Test
    fun `every correction case recomputes with nested truncation`() {
        assertTrue(fixtures.correctionCases.size >= 7, "приборы коррекции неполны")
        for (case in fixtures.correctionCases) {
            val outcome = ExamAssessmentArithmetic.correctedPercent(case.digits, case.exclude.toSet())
            if (case.expected.state == "UNSCORABLE_ALL_EXCLUDED") {
                assertEquals(ExamAssessmentArithmetic.CorrectionState.UNSCORABLE_ALL_EXCLUDED, outcome.state, case.id)
                assertEquals(null, outcome.percent, case.id)
                continue
            }
            assertEquals(ExamAssessmentArithmetic.CorrectionState.SCORED, outcome.state, case.id)
            assertEquals(case.expected.denominator, outcome.denominator, case.id)
            assertEquals(case.expected.percent, outcome.percent, case.id)
            assertEquals(
                case.expected.passed,
                ExamAssessmentArithmetic.passedAgainst(outcome.percent!!, case.capturedPassMark),
                "${case.id}: passed against captured ${case.capturedPassMark}",
            )
            assertEquals(
                case.expected.starsTenths,
                ExamAssessmentArithmetic.themeStarsTenths(outcome.percent),
                case.id,
            )
        }
    }

    @Test
    fun `the synthetic arithmetic-only case runs on the rounding and accounting seam`() {
        assertTrue(fixtures.arithmeticOnlyCases.isNotEmpty())
        for (case in fixtures.arithmeticOnlyCases) {
            assertTrue(case.arithmeticOnly, "${case.id}: помечен синтетическим")
            // Крошечная основа недостижима настоящему пулу: проверка минимумов её отвергает.
            assertFailsWith<IllegalArgumentException> { basis(case) }
            // Арифметика на шве проверяется напрямую, без проверки основы.
            val wP = ExamAssessmentArithmetic.weightedTariff(case.p)
            val wB = ExamAssessmentArithmetic.weightedTariff(case.b)
            val mode = modeOf(case.mode)
            val newPoints = (wP - wB).coerceAtLeast(0)
            val repeatedPoints = if (case.p < case.b) wP else wB
            val a = 10L * mode.c * newPoints + mode.r.raw.toLong() * repeatedPoints
            val q = case.TE + 3 * case.TH
            val n = a * q
            assertEquals(case.expected.skillPoints, ExamAssessmentArithmetic.roundHalfUp(n, 7_200), case.id)
            assertEquals(case.expected.nolics, ExamAssessmentArithmetic.roundHalfUp(n, 72_000), case.id)
            assertEquals(case.expected.bNext, maxOf(case.b, case.p), case.id)
        }
    }

    @Test
    fun `every invalid basis case is refused with no payout`() {
        assertTrue(fixtures.invalidBasisCases.size >= 2, "приборы испорченных основ неполны")
        for (case in fixtures.invalidBasisCases) {
            assertFailsWith<IllegalArgumentException> {
                ExamAssessmentArithmetic.WorthBasis(mode = modeOf(case.mode), TE = case.TE, TH = case.TH)
            }
        }
    }
}
