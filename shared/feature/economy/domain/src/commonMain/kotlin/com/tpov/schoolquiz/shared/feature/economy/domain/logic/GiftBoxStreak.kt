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
