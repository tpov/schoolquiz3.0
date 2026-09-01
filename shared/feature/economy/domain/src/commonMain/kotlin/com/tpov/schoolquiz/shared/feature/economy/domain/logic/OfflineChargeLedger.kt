package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules

/**
 * Офлайновый инвариант — словами владельца: *заряды, потраченные без связи, стоят на нуле, пока
 * сервер их не учтёт* (CAP-6).
 *
 * Устройство держит два числа: баланс, который сервер сообщил последним, и число заявок, сделанных
 * с тех пор и ещё не учтённых. Показывается и разрешается `serverBalance − unsettled`, не ниже нуля.
 * Местное восстановление может поднимать первое число к потолку по тому же расписанию, что и
 * сервер, но поднять *показанное* выше того, что причитается неучтённому аккаунту, оно не может:
 * заявки вычитаются после.
 *
 * В этом весь смысл. Если бы клиент доливал оптимистично при висящих заявках, повтор бы работал:
 * потратить три, переждать восстановление офлайн, потратить три ещё, синхронизироваться — и сервер
 * видит шесть заявок против аккаунта на три. Вычитание неучтённого первым делает вторую трату
 * недоступной на устройстве, а запись о перерасходе (CAP-7) ловит того, кто это выпилил.
 *
 * Плазма строже (CAP-8): при любой неучтённой заявке она недоступна вовсе. Обычные заряды можно
 * тратить офлайн и учитывать потом; монетарный ресурс — нет.
 *
 * @property serverCharges целых зарядов по последнему слову сервера (после местного восстановления).
 * @property unsettledClaims заявок этого вида с тех пор, ещё не учтённых сервером.
 */
data class OfflineChargeLedger(
    val kind: ChargeKind,
    val serverCharges: Int,
    val unsettledClaims: Int,
) {
    init {
        require(serverCharges >= 0) { "serverCharges must be non-negative, got $serverCharges" }
        require(unsettledClaims >= 0) { "unsettledClaims must be non-negative, got $unsettledClaims" }
    }

    /** Что показать и что разрешить: слово сервера минус ещё не учтённое, не ниже нуля. */
    val available: Int get() = (serverCharges - unsettledClaims).coerceAtLeast(0)

    /** Есть ли что-то, чего сервер ещё не видел. */
    val hasUnsettled: Boolean get() = unsettledClaims > 0

    /**
     * Записывает трату. Отказ — с названной причиной: молча выключенная кнопка ничего игроку не
     * объясняет, а спека требует объяснения словами.
     */
    fun claim(rules: ChargeRules): ClaimOutcome =
        when {
            rules.requiresSettledAccount && hasUnsettled -> ClaimOutcome.Refused(ClaimRefusal.WAITING_FOR_SETTLEMENT)
            available <= 0 -> ClaimOutcome.Refused(ClaimRefusal.NOTHING_LEFT)
            else -> ClaimOutcome.Accepted(copy(unsettledClaims = unsettledClaims + 1))
        }

    /**
     * Сервер учёл заявки и назвал баланс.
     *
     * Слово сервера замещает всё: и число зарядов, и счётчик заявок. Вычитать учтённые из
     * неучтённых по одной нельзя — сервер мог оплатить меньше, чем заявлено, и остаток не «ещё
     * ждёт», а уже отказан.
     */
    fun settled(serverCharges: Int): OfflineChargeLedger = copy(serverCharges = serverCharges, unsettledClaims = 0)

    /**
     * Местное восстановление подняло слово сервера.
     *
     * Заявки при этом не трогаются — и потому показанное не растёт быстрее, чем аккаунту
     * причитается: из большего числа вычтется то же неучтённое.
     */
    fun regeneratedTo(serverCharges: Int): OfflineChargeLedger = copy(serverCharges = maxOf(this.serverCharges, serverCharges))
}

/** Почему трата отказана — то, что показывают игроку словами. */
enum class ClaimRefusal {
    /** Заряды кончились — с учётом того, что сервер ещё не видел. */
    NOTHING_LEFT,

    /**
     * Есть неучтённые заявки, а этот вид тратится только на учтённом аккаунте.
     *
     * Так плазма и становится онлайновой: не «нет сети», а «сервер ещё не сказал своё слово».
     * Вернётся после удачной синхронизации.
     */
    WAITING_FOR_SETTLEMENT,
}

sealed interface ClaimOutcome {
    data class Accepted(val ledger: OfflineChargeLedger) : ClaimOutcome

    data class Refused(val reason: ClaimRefusal) : ClaimOutcome
}
