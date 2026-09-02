package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants

/**
 * Заряд, каким он лежит в базе: очки и момент, на который они посчитаны.
 *
 * Очками, а не целыми зарядами, потому что восстановление непрерывно: игрок, потративший заряд и
 * зашедший через сорок минут, обязан увидеть, что две трети уже вернулись. Целое число зарядов —
 * это то, что показывают, а не то, что хранят.
 *
 * @property points накопленные очки. Могут быть выше потолка — см. [regenerated].
 * @property updatedAtMs момент, на который очки посчитаны.
 */
data class ChargeBalance(
    val points: Int,
    val updatedAtMs: Long,
) {
    init {
        require(points >= 0) { "ChargeBalance.points must be non-negative, got $points" }
        require(updatedAtMs >= 0L) { "ChargeBalance.updatedAtMs must be non-negative, got $updatedAtMs" }
    }

    /** Сколько целых зарядов показать игроку. */
    val wholeCharges: Int get() = points / EconomyConstants.POINTS_PER_CHARGE
}

/**
 * Приводит накопленное к текущему моменту.
 *
 * Время двигается только на выданные целые очки, поэтому прогресс к следующему очку не теряется;
 * у потолка оно прыгает на сейчас — иначе простаивающий аккаунт копил бы задолженность и
 * мгновенно наполнялся после траты. Это поведение унаследовано и намеренно не изменилось.
 *
 * **Понижение потолка не конфискует.** Прежняя серверная реализация зажимала прочитанное значение
 * потолком (`Math.min` на чтении), то есть уменьшение `maxOwned` молча отбирало у игрока лишние
 * заряды. Теперь баланс выше потолка остаётся как есть: он просто не растёт, пока не опустится
 * под потолок сам. Разница видна только в момент, когда потолок понизили, — но именно в этот
 * момент и обидно.
 */
fun ChargeBalance.regenerated(
    rules: ChargeRules,
    nowMs: Long,
    /**
     * Сколько слотов у аккаунта куплено.
     *
     * Восстанавливается то, чем аккаунт владеет, а не всё, что можно купить: лестница цен продаёт
     * слоты по одному, и `maxOwned` — предел покупки, а не размер бака у каждого. Ниже потолка
     * бак ограничен слотами; выше — потолком (понижение не конфискует, но и не доливает).
     */
    ownedSlots: Int = rules.maxOwned,
    /** Премиум восстанавливается быстрее — если таблица так говорит (`premiumRegenDivisor`). */
    hasPremium: Boolean = false,
): ChargeBalance {
    val ceiling = minOf(ownedSlots, rules.maxOwned).coerceAtLeast(0) * EconomyConstants.POINTS_PER_CHARGE
    // Не зажимаем вниз: то, что уже есть, принадлежит игроку.
    if (points >= ceiling) return copy(updatedAtMs = maxOf(updatedAtMs, nowMs))
    if (!rules.regenerates || nowMs <= updatedAtMs) return this

    val intervalMs = rules.regenMsFor(hasPremium) / EconomyConstants.POINTS_PER_CHARGE
    // Заряд, восстанавливающийся быстрее, чем очко в миллисекунду, — не ускорение, а деление на
    // ноль. Такой настройке отвечаем мгновенным полным баком, а не падением.
    if (intervalMs <= 0L) return copy(points = ceiling, updatedAtMs = nowMs)

    val gained = ((nowMs - updatedAtMs) / intervalMs).coerceAtMost((ceiling - points).toLong()).toInt()
    if (gained <= 0) return this

    val grown = points + gained
    return if (grown >= ceiling) {
        copy(points = ceiling, updatedAtMs = nowMs)
    } else {
        copy(points = grown, updatedAtMs = updatedAtMs + gained.toLong() * intervalMs)
    }
}

/**
 * Итог попытки списать очки.
 *
 * @property affordable хватило ли. Игра офлайн-первая, и попытка может честно приехать после того,
 *   как очки уже потрачены в другом месте: сервер остаётся источником истины и просто не платит.
 * @property balance что осталось. При нехватке — нетронутое.
 */
data class ChargeSpend(
    val affordable: Boolean,
    val balance: ChargeBalance,
)

/** Списывает [cost] очков. Отрицательная цена невозможна, нулевая — бесплатно и разрешено. */
fun ChargeBalance.spend(cost: Int): ChargeSpend {
    require(cost >= 0) { "цена не может быть отрицательной, получено $cost" }
    return if (points < cost) {
        ChargeSpend(affordable = false, balance = this)
    } else {
        ChargeSpend(affordable = true, balance = copy(points = points - cost))
    }
}

/**
 * Можно ли купить ещё один слот.
 *
 * Понижённый потолок не отбирает уже купленное, но и покупать сверх него не даёт — иначе понижение
 * не значило бы ничего.
 */
fun ChargeRules.canBuySlot(owned: Int): Boolean = owned < maxOwned
