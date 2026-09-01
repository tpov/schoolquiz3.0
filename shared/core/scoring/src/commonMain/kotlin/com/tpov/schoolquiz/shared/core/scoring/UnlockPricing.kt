package com.tpov.schoolquiz.shared.core.scoring

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Сколько стоит открыть урок, не заработав его.
 *
 * Зеркало `lessonUnlockPrice` и `unlockPrice` из `functions/lesson-reward.js` и
 * `functions/lesson-unlocks.js`. До сих пор формула жила только на сервере, и поле
 * `unlockPriceNolics` на клиенте не присваивалось нигде — то есть очередь не могла показать цену
 * до отправки, а AD-3 требует именно этого: отложенное действие обязано выводиться из
 * синхронизированного контента, а не из состояния, которого у клиента нет.
 *
 * Цена по-прежнему **назначается сервером**: он читает вопросы урока сам, потому что вызывающий,
 * который мог бы назвать свою цену, назвал бы ноль. Здесь она вычисляется, чтобы показать её
 * игроку и поставить действие в очередь; расхождение двух реализаций — это ошибка, и её ловит
 * общий набор фикстур.
 */
object UnlockPricing {

    /** Что именно покупается. */
    enum class Kind {
        /** Урок целиком: обе сложности, потому что открывает обе. */
        LESSON,

        /** Только сложный режим для уже открытого урока — половина цены. */
        HARD_MODE,
    }

    /**
     * Цена в нoликах.
     *
     * @param easyAllocatedSeconds суммарное отведённое время лёгких вопросов урока.
     * @param hardAllocatedSeconds то же для сложных.
     */
    fun price(
        kind: Kind,
        easyAllocatedSeconds: Long,
        hardAllocatedSeconds: Long,
    ): Long =
        when (kind) {
            Kind.LESSON -> lessonPrice(easyAllocatedSeconds, hardAllocatedSeconds)
            // Сложный режим для открытого урока — только сложная половина.
            Kind.HARD_MODE -> lessonPrice(easyAllocatedSeconds = 0L, hardAllocatedSeconds = hardAllocatedSeconds)
        }

    /**
     * Якорь всей шкалы: урок, пройденный идеально с первого раза на обеих сложностях, оплачивает
     * ровно одно открытие другого урока такого же размера. Цена и награда считаются из одного и
     * того же отведённого времени, поэтому масштаб сокращается — значение имеет только отношение.
     */
    private fun lessonPrice(
        easyAllocatedSeconds: Long,
        hardAllocatedSeconds: Long,
    ): Long {
        val perfect = weightedPercent(100) * NEW_PERCENT_MULTIPLIER
        val easy = perfect * sizeFactor(easyAllocatedSeconds)
        val hard = perfect * sizeFactor(hardAllocatedSeconds) * HARD_TARIFF
        // Не меньше одного нолика: дверь, которая открывается даром, — не дверь.
        return max(1L, ((easy + hard) / POINTS_PER_NOLIC).roundToLong())
    }

    /** Тарифные очки за процент, просуммированные по полосам, которые он пересекает. */
    internal fun weightedPercent(percent: Int): Double {
        val p = min(max(percent, 0), MAX_PERCENT).toDouble()
        var total = 0.0
        for ((from, to, multiplier) in TARIFF_BANDS) {
            total += max(0.0, min(p, to.toDouble()) - from.toDouble()) * multiplier
        }
        return total
    }

    /** Во сколько раз урок больше эталонного. */
    internal fun sizeFactor(allocatedSeconds: Long): Double =
        max(0L, allocatedSeconds).toDouble() / SMALL_LESSON_SECONDS

    /**
     * Предельные тарифные полосы: `[от, до, множитель]`.
     *
     * Полосами, а не подогнанной кривой, намеренно: ближайшее простое непрерывное приближение
     * ошибается процентов на сорок в районе 25%, где выплаты малы и один нолик заметен.
     */
    private val TARIFF_BANDS =
        listOf(
            Triple(0, 25, 1),
            Triple(25, 50, 2),
            Triple(50, 70, 3),
            Triple(70, 90, 4),
            Triple(90, 100, 5),
        )

    private const val MAX_PERCENT = 100

    /** Процент, взятый впервые. */
    private const val NEW_PERCENT_MULTIPLIER = 2

    /** Сложный вопрос стоит втрое против лёгкого при том же отведённом времени. */
    private const val HARD_TARIFF = 3

    /** Эталонный урок: двадцать вопросов примерно по сотне знаков, на лёгком. */
    private const val SMALL_LESSON_SECONDS = 720.0

    /** Тарифных очков в нолике: 10 очков (10% в полосе 1x) покупают один нолик. */
    private const val POINTS_PER_NOLIC = 10
}
