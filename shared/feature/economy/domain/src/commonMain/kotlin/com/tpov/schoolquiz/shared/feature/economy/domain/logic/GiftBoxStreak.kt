package com.tpov.schoolquiz.shared.feature.economy.domain.logic

/**
 * Серия ежедневных визитов и коробки, которые она даёт (CAP-12).
 *
 * Зеркало `functions/gift-boxes.js`; общий набор фикстур `config/gift-box-fixtures.json` держит их
 * вместе. Правила — из `box-economy.md`: визит продвигает серию не чаще раза в сутки; с десятого
 * дня подряд каждый визит даёт коробку; пропущенный день серию обрывает — «подряд» значит подряд.
 *
 * На устройстве это нужно затем, чтобы серия шла и без связи: игрок открывает приложение каждый
 * день, и серия растёт здесь, а при синхронизации сервер сверяет насчитанное со своими часами
 * (см. `boxAccrualVerdict` на сервере). Открыть коробку без связи нельзя — содержимое решает и
 * выдаёт только сервер.
 *
 * @property boxCount коробок на руках.
 * @property streakDays дней серии подряд.
 * @property nextBoxAtMs момент, с которого следующий визит засчитывается; ноль — серии ещё не было.
 */
data class GiftBoxStreak(
    val boxCount: Int,
    val streakDays: Int,
    val nextBoxAtMs: Long,
) {
    init {
        require(boxCount >= 0) { "boxCount must be non-negative, got $boxCount" }
        require(streakDays >= 0) { "streakDays must be non-negative, got $streakDays" }
        require(nextBoxAtMs >= 0L) { "nextBoxAtMs must be non-negative, got $nextBoxAtMs" }
    }

    /**
     * Визит в момент [nowMs].
     *
     * Сутки считаются от [nextBoxAtMs]: визит до него — повтор в тех же сутках; в течение
     * следующих суток — серия продлевается на день; позже — день пропущен, серия начинается заново.
     * Коробка — за визит с десятого дня, и только одна: дни без визита не копятся, они серию и
     * обрывают.
     */
    fun visited(nowMs: Long): GiftBoxStreak {
        val now = nowMs.coerceAtLeast(0L)
        if (nextBoxAtMs == 0L) return GiftBoxStreak(boxCount, streakDays = 1, nextBoxAtMs = now + DAY_MS)
        if (now < nextBoxAtMs) return this
        val missed = now >= nextBoxAtMs + DAY_MS
        val streak = if (missed) 1 else streakDays + 1
        val granted = if (streak >= STREAK_TARGET_DAYS) STREAK_BOXES_PER_DAY else 0
        return GiftBoxStreak(boxCount + granted, streak, now + DAY_MS)
    }

    companion object {
        const val DAY_MS: Long = 24L * 60 * 60 * 1000

        /** С какого дня серии визит даёт коробку. `GIFT_BOX_STREAK_TARGET_DAYS` в `box-economy.md`. */
        const val STREAK_TARGET_DAYS: Int = 10

        /** Сколько из дневного потолка может дать серия; остальное — реклама. */
        const val STREAK_BOXES_PER_DAY: Int = 1

        val NONE: GiftBoxStreak = GiftBoxStreak(boxCount = 0, streakDays = 0, nextBoxAtMs = 0L)
    }
}

/**
 * Заявка устройства на офлайн-накопление — ровно те два числа, что едет в `submitGiftBoxOperation`.
 *
 * `boxStreakDays` — какова серия по честному счёту устройства, `boxesEarned` — сколько коробок она
 * насчитала поверх последнего серверного слова. Оба зажаты потолками сервера ещё здесь, на
 * устройстве: излишек на сервере не начисляется, а записывается в аудит перерасхода (CAP-7), и
 * принятие такого излишка не делает завышенную заявку честной.
 */
data class OfflineAccrualClaim(
    val boxStreakDays: Int,
    val boxesEarned: Int,
) {
    init {
        require(boxStreakDays >= 0) { "boxStreakDays must be non-negative, got $boxStreakDays" }
        require(boxesEarned >= 0) { "boxesEarned must be non-negative, got $boxesEarned" }
    }
}

/**
 * Офлайн-накопление: зеркало `boxAccrualVerdict` из `functions/gift-boxes.js`.
 *
 * Сервер сверяет заявку со своими часами: серия не длиннее, чем было плюс прошедшие сутки,
 * коробка — за каждый день серии с десятого, не больше одной в сутки и только за дни, которые
 * устройство заявило как пройденные. Здесь те же потолки применяются к местному счёту ДО отправки:
 * честный игрок с честными часами получает ровно то, что сервер принял бы, а испорченные часы
 * не превращаются в завышенную заявку — прошедшие сутки считаются от тех же чисел, и расхождение
 * с серверными часами остаётся на стороне сервера, а не в теле заявки.
 *
 * Слово сервера без серии (`nextBoxAtMs == 0`) заявляет не больше одного дня и нуля коробок —
 * сервер на первом слове коробки не выдаёт вовсе.
 */
object GiftBoxAccrual {
    /**
     * Зажимает местный счёт [counted] (серия, растущая из [visited] поверх слова [base]) до
     * серверных потолков на момент [nowMs].
     *
     * Коробки считаются от базовой серии: [counted] несёт тот же `boxCount`, что и слово сервера,
     * плюс то, что устройство насчитало. Отрицательная разница — счёт от другого слова — коробкой
     * не считается: ноль, а не долг.
     */
    fun claim(
        base: GiftBoxStreak,
        counted: GiftBoxStreak,
        nowMs: Long,
    ): OfflineAccrualClaim {
        val now = nowMs.coerceAtLeast(0L)
        val claimedBoxes = (counted.boxCount - base.boxCount).coerceAtLeast(0)
        // Сколько суток могло начаться с последнего засчитанного визита. Без серии — с нуля.
        val lastVisitMs = if (base.nextBoxAtMs > 0L) base.nextBoxAtMs - GiftBoxStreak.DAY_MS else 0L
        val elapsedDays =
            if (base.nextBoxAtMs > 0L) {
                ((now - lastVisitMs).coerceAtLeast(0L) / GiftBoxStreak.DAY_MS).toInt()
            } else {
                // Без единого слова сервера серию не с чем сверить: принимается один день —
                // первый визит, — и ни одной коробки.
                1
            }
        val streakCeiling = base.streakDays + elapsedDays
        val allowedStreak = counted.streakDays.coerceAtLeast(0).coerceAtMost(streakCeiling)
        // Коробка — за каждый день серии начиная с десятого, по одной в день.
        val boxDays =
            (allowedStreak - maxOf(base.streakDays, GiftBoxStreak.STREAK_TARGET_DAYS - 1)).coerceAtLeast(0)
        val boxCeiling = minOf(boxDays, elapsedDays * GiftBoxStreak.STREAK_BOXES_PER_DAY)
        return OfflineAccrualClaim(allowedStreak, claimedBoxes.coerceAtMost(boxCeiling))
    }
}
