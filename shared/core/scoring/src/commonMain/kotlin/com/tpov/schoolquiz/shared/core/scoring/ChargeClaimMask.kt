package com.tpov.schoolquiz.shared.core.scoring

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import kotlin.jvm.JvmInline

/**
 * Заявки на заряды — строка рядом с [CodeAnswer], той же длины, по символу на позицию.
 *
 * `.` — заявки нет, `S` — обычный заряд (подсказка на лёгком вопросе), `P` — плазменный (пропуск
 * сложного). Рядом, а не внутри: `codeAnswer` остаётся цифрами, и ни его разбор, ни зеркало на
 * сервере (`recomputePercentScore`), ни сохранённые попытки не трогаются. Попытка без маски ведёт
 * себя ровно как сегодня.
 *
 * Заявка — не транзакция (CAP-3). Клиент записывает, что знает: пропущенный вопрос — это `0`, и
 * только сервер, взяв оплату, поднимает позицию до `9`. Клиент, написавший `9` сам, заявляет ответ,
 * которого не давал, — и маска делает это различимым.
 *
 * Зеркало `functions/charge-claims.js`; общий набор фикстур держит их вместе.
 */
@JvmInline
value class ChargeClaimMask(val raw: String) {
    init {
        require(raw.all { it == NONE || it == STANDARD || it == PLASMA }) {
            "ChargeClaimMask chars must be one of '.SP', got '$raw'"
        }
    }

    /** Сколько заявок каждого вида. */
    fun count(kind: Char): Int = raw.count { it == kind }

    val hasClaims: Boolean get() = raw.any { it != NONE }

    /**
     * Заявка одного вида, с её символом в маске.
     *
     * Перечислением, а не голым `Char`: заявку ставит раннер, а вид её решает сложность попытки, и
     * перепутать `S` с `P` в вызове — это заявить пропуск там, где была подсказка.
     */
    enum class Claim(val symbol: Char) {
        /** Обычный заряд: подсказка на лёгком вопросе. */
        STANDARD_HINT(ChargeClaimMask.STANDARD),

        /** Плазменный заряд: пропуск сложного вопроса. */
        PLASMA_SKIP(ChargeClaimMask.PLASMA),
    }

    /** Ставит заявку [claim] на позицию [index]. Повторная заявка на ту же позицию не удваивается. */
    fun with(
        index: Int,
        claim: Claim,
    ): ChargeClaimMask {
        require(index in raw.indices) { "index $index is outside the mask of length ${raw.length}" }
        return ChargeClaimMask(raw.toCharArray().also { it[index] = claim.symbol }.concatToString())
    }

    companion object {
        const val NONE: Char = '.'
        const val STANDARD: Char = 'S'
        const val PLASMA: Char = 'P'

        /** Маска без единой заявки — то, что несёт попытка, где зарядов не тратили. */
        fun none(length: Int): ChargeClaimMask = ChargeClaimMask(NONE.toString().repeat(length))
    }
}

/**
 * Чем маска может быть испорчена.
 *
 * Испорченная маска — не перерасход, а искажённый payload: сервер отвергает пакет целиком, а не
 * платит по нему частично.
 */
enum class ClaimMaskFault {
    /** Маска и `codeAnswer` разной длины — непонятно, какая заявка к какому вопросу. */
    LENGTH_MISMATCH,

    /** Обычный заряд заявлен на сложной попытке или плазменный на лёгкой (CAP-1). */
    WRONG_DIFFICULTY,

    /**
     * Пропуск заявлен на позиции, у которой уже стоит ответ.
     *
     * Пропущенный вопрос не отвечают — у него `0`, и `9` ставит сервер после оплаты. Ненулевая цифра
     * под `P` значит, что клиент оценил себя сам и просит оплатить уже поставленное.
     */
    SKIP_ON_ANSWERED,
}

/**
 * Проверяет маску против попытки.
 *
 * @param difficulty сложность попытки целиком: подсказки живут только на лёгких, пропуски — только
 *   на сложных, и у попытки одна сложность на все вопросы.
 */
fun ChargeClaimMask.validateAgainst(
    codeAnswer: CodeAnswer,
    difficulty: Difficulty,
): ClaimMaskFault? {
    if (raw.length != codeAnswer.raw.length) return ClaimMaskFault.LENGTH_MISMATCH
    val allowed = if (difficulty == Difficulty.EASY) ChargeClaimMask.STANDARD else ChargeClaimMask.PLASMA
    // По позициям, слева направо, — в том же порядке, что и сервер: маска с двумя пороками обязана
    // быть названа одним и тем же словом с обеих сторон.
    raw.forEachIndexed { index, claim ->
        if (claim != ChargeClaimMask.NONE && claim != allowed) return ClaimMaskFault.WRONG_DIFFICULTY
        if (claim == ChargeClaimMask.PLASMA && codeAnswer.raw[index] != '0') return ClaimMaskFault.SKIP_ON_ANSWERED
    }
    return null
}

/**
 * Итог расчёта заявок.
 *
 * @property codeAnswer строка с поднятыми до `9` оплаченными пропусками. Подсказка ничего не
 *   поднимает: она лишь показала ответ, а цифра — то, что игрок после этого ответил.
 * @property paid позиции, за которые взято.
 * @property unpaid позиции, на которые не хватило: их цифра осталась какой была — вопрос засчитан
 *   как неотвеченный, чем он и был.
 */
data class ClaimSettlement(
    val codeAnswer: CodeAnswer,
    val paid: List<Int>,
    val unpaid: List<Int>,
    val standardChargesPaid: Int,
    val plasmaChargesPaid: Int,
)

/**
 * Списывает заявки в пределах того, что есть, в порядке [order].
 *
 * Порядок обязателен: частичная оплата платит за самые ранние заявки, а не за произвольное
 * подмножество. По умолчанию — порядок позиций в строке; сервер, у которого есть время каждого
 * ответа, может передать порядок, в котором вопросы задавались.
 *
 * Подсказка и пропуск стоят по целому заряду — не по очкам: они неделимы (см. ограничения спеки).
 *
 * @param standardAvailable сколько целых обычных зарядов есть.
 * @param plasmaAvailable сколько целых плазменных.
 */
fun settleClaims(
    mask: ChargeClaimMask,
    codeAnswer: CodeAnswer,
    standardAvailable: Int,
    plasmaAvailable: Int,
    order: List<Int> = mask.raw.indices.toList(),
): ClaimSettlement {
    require(mask.raw.length == codeAnswer.raw.length) { "mask and codeAnswer must be the same length" }
    var standard = standardAvailable.coerceAtLeast(0)
    var plasma = plasmaAvailable.coerceAtLeast(0)
    val paid = mutableListOf<Int>()
    val unpaid = mutableListOf<Int>()
    val digits = codeAnswer.raw.toCharArray()
    val seen = HashSet<Int>()
    for (index in order + mask.raw.indices) {
        if (index !in mask.raw.indices || !seen.add(index)) continue
        when (mask.raw[index]) {
            ChargeClaimMask.STANDARD ->
                if (standard > 0) {
                    standard--
                    paid += index
                } else {
                    unpaid += index
                }
            ChargeClaimMask.PLASMA ->
                if (plasma > 0) {
                    plasma--
                    paid += index
                    // Только оплаченный пропуск засчитывается верным.
                    digits[index] = '9'
                } else {
                    unpaid += index
                }
            else -> Unit
        }
    }
    return ClaimSettlement(
        codeAnswer = CodeAnswer(String(digits)),
        paid = paid.sorted(),
        unpaid = unpaid.sorted(),
        standardChargesPaid = standardAvailable.coerceAtLeast(0) - standard,
        plasmaChargesPaid = plasmaAvailable.coerceAtLeast(0) - plasma,
    )
}
