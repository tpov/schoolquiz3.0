package com.tpov.schoolquiz.shared.core.scoring

/**
 * Арифметика экзаменационной оценки C1/C2 — вторая реализация контракта EXAM-TECH-1.
 *
 * Сервер несёт первую (`functions/exam-reward.js`, `functions/exam-scoring.js`); приборы
 * `assessment-contract-fixtures.json` держат обе стороны вместе, как держат `Scoring.kt` и
 * `assessment-scoring.js`. Обе меняются в одном коммите или не меняются вовсе.
 *
 * Три правила, продублированные здесь намеренно:
 *
 * - **Взвешенный предельный тариф** — те же полосы, что у обычной награды; режим меняет только
 *   коэффициент НОВОГО прогресса (тема ×3, финал ×10 нового / ×0.1 уже оплаченного). Экзаменного
 *   множителя на всю попытку не существует; HARD ×3 сидит внутри базы, поэтому HARD-финал —
 *   эффективные 30/0.3, а не ×30.
 * - **Независимое округление валют** — XP и нолики считаются от одного нераскруглённого N
 *   (`N = A·Q`, целочисленно, половину вверх), ноль после округления всё равно двигает учтённый
 *   максимум до `max(b, p)`.
 * - **Коррекция C2** — исключения уходят из числителя и знаменателя; вложенное усечение через тот
 *   же процентный расчёт; все-исключены — состояние без числа, не ноль.
 *
 * Всё целочисленно в [Long]: A ≤ 26 500, Q ограничена минимумами пула, N помещается с запасом.
 */
object ExamAssessmentArithmetic {

    /** Режимы C1: коэффициент нового прогресса и уже оплаченного. */
    enum class Mode(val c: Int, val r: Tenths) {
        THEME_TEST(c = 3, r = Tenths.ZERO),
        FINAL_EXAM(c = 10, r = Tenths.ONE_TENTH),
    }

    /** Десятые доли коэффициента r: у него одна значащая цифра после запятой. */
    @JvmInline
    value class Tenths(val raw: Int) {
        companion object {
            val ZERO = Tenths(0)
            val ONE_TENTH = Tenths(1)
        }
    }

    /** Полосы предельного тарифа: [от, до, множитель]. W(100) = 265. */
    val TARIFF_BANDS: List<Triple<Int, Int, Int>> = listOf(
        Triple(0, 25, 1),
        Triple(25, 50, 2),
        Triple(50, 70, 3),
        Triple(70, 90, 4),
        Triple(90, 100, 5),
    )

    /** Взвешенный тариф W(p) по полосам. */
    fun weightedTariff(percent: Int): Long {
        require(percent in 0..100) { "percent must be 0..100, got $percent" }
        var total = 0L
        for ((from, to, multiplier) in TARIFF_BANDS) {
            total += (minOf(percent, to) - from).coerceAtLeast(0).toLong() * multiplier
        }
        return total
    }

    /** Половина вверх для неотрицательных целых: floor((2n+d)/(2d)). */
    fun roundHalfUp(numerator: Long, denominator: Long): Long {
        require(numerator >= 0) { "numerator must be non-negative: $numerator" }
        require(denominator > 0) { "denominator must be positive: $denominator" }
        return (2 * numerator + denominator) / (2 * denominator)
    }

    /**
     * Замороженная основа выплаты C1: ограниченные средние пула по обычному времени.
     *
     * @property TE ограниченное среднее EASY пула × 5 (0 у финала);
     * @property TH ограниченное среднее HARD пула × 15 (тема) или × 20 (финал);
     * @property Q = TE + 3·TH.
     */
    data class WorthBasis(
        val mode: Mode,
        val TE: Long,
        val TH: Long,
        val Q: Long = TE + 3 * TH,
        val ruleVersion: Int = 1,
    ) {
        init {
            require(ruleVersion == 1) { "unknown reward rule version $ruleVersion" }
            when (mode) {
                Mode.THEME_TEST -> require(TE >= 25 && TH >= 75) {
                    "INVALID_REWARD_BASIS: ThemeTest TE=$TE TH=$TH violates per-question minimums"
                }
                Mode.FINAL_EXAM -> {
                    require(TE == 0L) { "INVALID_REWARD_BASIS: FinalExam requires TE=0, got $TE" }
                    require(TH >= 100) {
                        "INVALID_REWARD_BASIS: FinalExam TH=$TH violates the per-question minimum"
                    }
                }
            }
        }
    }

    /** Ограниченное среднее пула: roundHalfUp(draw · sum / count) по всему подходящему пулу. */
    fun cappedPoolMean(draw: Int, sum: Long, count: Long): Long {
        require(draw > 0 && sum >= 0 && count > 0) { "pool mean inputs out of range" }
        return roundHalfUp(draw.toLong() * sum, count)
    }

    /**
     * Выплата C1: `A = 10·c·max(W(p)−W(b),0) + (10·r)·W(min(p,b))`, `N = A·Q`;
     * XP = roundHalfUp(N/7200), нолики = roundHalfUp(N/72000) независимо.
     *
     * @property bNext учтённый максимум после выплаты: `max(b, p)` даже при нулевых суммах.
     */
    data class Payout(
        val nolics: Long,
        val skillPoints: Long,
        val bNext: Int,
        val unroundedN: Long,
    )

    fun assessmentReward(
        mode: Mode,
        percent: Int,
        accountedPercent: Int,
        basis: WorthBasis,
    ): Payout {
        require(percent in 0..100) { "percent must be 0..100, got $percent" }
        require(accountedPercent in 0..100) { "accounted percent out of range: $accountedPercent" }
        require(basis.mode == mode) { "basis mode ${basis.mode} does not match $mode" }
        val wP = weightedTariff(percent)
        val wB = weightedTariff(accountedPercent)
        val newPoints = (wP - wB).coerceAtLeast(0)
        // W(min(p, b)): ниже учтённого максимума платится повторный прогресс по текущему p.
        val repeatedPoints = if (percent < accountedPercent) wP else wB
        // r хранится в десятых: 10·r для r=0.1 — ровно raw=1.
        val a = 10L * mode.c * newPoints + mode.r.raw.toLong() * repeatedPoints
        val n = a * basis.Q
        return Payout(
            nolics = roundHalfUp(n, 72_000),
            skillPoints = roundHalfUp(n, 7_200),
            bNext = maxOf(accountedPercent, percent),
            unroundedN = n,
        )
    }

    /** Состояния C2 для неоценимого результата: без числа, без выдуманных нуля или сотни. */
    enum class CorrectionState { SCORED, UNSCORABLE_ALL_EXCLUDED, PENDING_EVIDENCE }

    /**
     * Пересчёт C2: из исходных цифр минус накопленные исключения.
     *
     * Исключённая позиция уходит и из числителя, и из знаменателя; порядок исключений не важен.
     * Пустой знаменатель — отдельное состояние, не ноль.
     */
    fun correctedPercent(digits: List<Int>, excludeIndexes: Set<Int>): CorrectionOutcome {
        require(digits.size == 20) { "exam sitting scores exactly 20 slots, got ${digits.size}" }
        require(digits.all { it in 1..9 }) { "corrupt evidence: slot digit out of 1..9" }
        require(excludeIndexes.all { it in digits.indices }) {
            "exclusion index out of range: $excludeIndexes"
        }
        var numerator = 0L
        var denominator = 0L
        for ((index, digit) in digits.withIndex()) {
            if (index in excludeIndexes) continue
            numerator += (digit - 1).toLong() * 100 / 8
            denominator += 1
        }
        if (denominator == 0L) {
            return CorrectionOutcome(CorrectionState.UNSCORABLE_ALL_EXCLUDED, percent = null, denominator = 0)
        }
        return CorrectionOutcome(CorrectionState.SCORED, percent = (numerator / denominator).toInt(), denominator = denominator.toInt())
    }

    data class CorrectionOutcome(
        val state: CorrectionState,
        val percent: Int?,
        val denominator: Int,
    )

    /** Линейные звёзды темы в десятых: floor((30·p + 50)/100). Сервер возвращает их сам. */
    fun themeStarsTenths(percent: Int): Int {
        require(percent in 0..100) { "percent must be 0..100, got $percent" }
        return (30 * percent + 50) / 100
    }

    /** Проход — сырой процент против захваченного балла, не звёзды. */
    fun passedAgainst(percent: Int, capturedPassMark: Int): Boolean {
        require(percent in 0..100) { "percent must be 0..100, got $percent" }
        require(capturedPassMark in 0..100) { "pass mark out of range: $capturedPassMark" }
        return percent >= capturedPassMark
    }
}
