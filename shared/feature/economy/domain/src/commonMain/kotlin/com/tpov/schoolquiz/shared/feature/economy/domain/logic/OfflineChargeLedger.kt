package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules

/**
 * Одна строка офлайнового учёта — по строке на вид заряда.
 *
 * @property serverCharges целых зарядов по последнему слову сервера (после местного восстановления).
 * @property unsettledClaims заявок из **прежних** попыток, ещё не учтённых сервером.
 * @property runClaims заявок в **текущей** попытке. Они ещё не заявка сервера: до конца попытки
 *   расчёта не бывает, а сервер учитывает попытку целиком.
 */
data class ChargeLedgerLine(
    val serverCharges: Int,
    val unsettledClaims: Int = 0,
    val runClaims: Int = 0,
) {
    init {
        require(serverCharges >= 0) { "serverCharges must be non-negative, got $serverCharges" }
        require(unsettledClaims >= 0) { "unsettledClaims must be non-negative, got $unsettledClaims" }
        require(runClaims >= 0) { "runClaims must be non-negative, got $runClaims" }
    }

    /** Что показать и что разрешить: слово сервера минус всё, чего он ещё не видел, не ниже нуля. */
    val available: Int get() = (serverCharges - unsettledClaims - runClaims).coerceAtLeast(0)

    /** Есть ли заявки прежних попыток, о которых сервер ещё не сказал своего слова. */
    val hasUnsettled: Boolean get() = unsettledClaims > 0
}

/**
 * Офлайновый инвариант — словами владельца: *заряды, потраченные без связи, стоят на нуле, пока
 * сервер их не учтёт* (CAP-6).
 *
 * Устройство держит два числа на каждый вид: баланс, который сервер сообщил последним, и число
 * заявок, сделанных с тех пор и ещё не учтённых. Показывается и разрешается их разность, не ниже
 * нуля. Местное восстановление может поднимать первое число к потолку по тому же расписанию, что и
 * сервер, но поднять *показанное* выше того, что причитается неучтённому аккаунту, оно не может:
 * заявки вычитаются после.
 *
 * В этом весь смысл. Если бы клиент доливал оптимистично при висящих заявках, повтор бы работал:
 * потратить три, переждать восстановление офлайн, потратить три ещё, синхронизироваться — и сервер
 * видит шесть заявок против аккаунта на три. Вычитание неучтённого первым делает вторую трату
 * недоступной на устройстве, а запись о перерасходе (CAP-7) ловит того, кто это выпилил.
 *
 * Плазма строже (CAP-8): пока у **аккаунта** — любого вида — есть заявка прежней попытки, которую
 * сервер не учёл, плазма недоступна вовсе. Обычные заряды можно тратить офлайн и учитывать потом;
 * монетарный ресурс — нет. Заявки текущей попытки этому не мешают: расчёт бывает только в конце
 * попытки, и три пропуска за один забег — разрешённая спекой норма, а не неучтённый долг.
 */
data class OfflineChargeLedger(
    val standard: ChargeLedgerLine,
    val plasma: ChargeLedgerLine,
) {
    fun line(kind: ChargeKind): ChargeLedgerLine =
        when (kind) {
            ChargeKind.STANDARD -> standard
            ChargeKind.PLASMA -> plasma
        }

    /** Есть ли у аккаунта хоть что-то, чего сервер ещё не видел, — любого вида. */
    val hasUnsettledClaims: Boolean get() = standard.hasUnsettled || plasma.hasUnsettled

    /**
     * Записывает трату в текущей попытке. Отказ — с названной причиной: молча выключенная кнопка
     * ничего игроку не объясняет, а спека требует объяснения словами.
     */
    fun claim(
        kind: ChargeKind,
        rules: ChargeRules,
    ): ClaimOutcome {
        val current = line(kind)
        return when {
            rules.requiresSettledAccount && hasUnsettledClaims -> ClaimOutcome.Refused(ClaimRefusal.WAITING_FOR_SETTLEMENT)
            current.available <= 0 -> ClaimOutcome.Refused(ClaimRefusal.NOTHING_LEFT)
            else -> ClaimOutcome.Accepted(with(kind, current.copy(runClaims = current.runClaims + 1)))
        }
    }

    /**
     * Попытка закончена: её заявки становятся долгом перед сервером.
     *
     * С этого момента они держат баланс на нуле до расчёта, а плазму — запертой (CAP-8).
     */
    fun runEnded(): OfflineChargeLedger =
        OfflineChargeLedger(
            standard = standard.copy(unsettledClaims = standard.unsettledClaims + standard.runClaims, runClaims = 0),
            plasma = plasma.copy(unsettledClaims = plasma.unsettledClaims + plasma.runClaims, runClaims = 0),
        )

    /**
     * Попытка брошена: её заявки никуда не уедут и ничего не стоят.
     *
     * Ничего не потрачено — нечего и учитывать (CAP-15: что не потрачено в итоге, не обнуляется).
     */
    fun runAbandoned(): OfflineChargeLedger =
        OfflineChargeLedger(standard.copy(runClaims = 0), plasma.copy(runClaims = 0))

    /**
     * Сервер учёл заявки и назвал баланс.
     *
     * Слово сервера замещает всё: и число зарядов, и счётчик неучтённых. Вычитать учтённые из
     * неучтённых по одной нельзя — сервер мог оплатить меньше, чем заявлено, и остаток не «ещё
     * ждёт», а уже отказан. Заявки идущей попытки остаются: их сервер ещё не видел.
     */
    fun settled(
        standardCharges: Int,
        plasmaCharges: Int,
    ): OfflineChargeLedger =
        OfflineChargeLedger(
            standard = standard.copy(serverCharges = standardCharges, unsettledClaims = 0),
            plasma = plasma.copy(serverCharges = plasmaCharges, unsettledClaims = 0),
        )

    /**
     * Местное восстановление подняло слово сервера по одному виду.
     *
     * Заявки при этом не трогаются — и потому показанное не растёт быстрее, чем аккаунту
     * причитается: из большего числа вычтется то же неучтённое. Отнять уже сказанное сервером
     * местный пересчёт не может.
     */
    fun regeneratedTo(
        kind: ChargeKind,
        serverCharges: Int,
    ): OfflineChargeLedger {
        val current = line(kind)
        return with(kind, current.copy(serverCharges = maxOf(current.serverCharges, serverCharges)))
    }

    private fun with(
        kind: ChargeKind,
        line: ChargeLedgerLine,
    ): OfflineChargeLedger =
        when (kind) {
            ChargeKind.STANDARD -> copy(standard = line)
            ChargeKind.PLASMA -> copy(plasma = line)
        }

    companion object {
        /** Ни одного заряда и ни одной заявки — гость или аккаунт, о котором ещё ничего не известно. */
        val EMPTY: OfflineChargeLedger = OfflineChargeLedger(ChargeLedgerLine(0), ChargeLedgerLine(0))
    }
}

/** Почему трата отказана — то, что показывают игроку словами. */
enum class ClaimRefusal {
    /** Заряды кончились — с учётом того, что сервер ещё не видел. */
    NOTHING_LEFT,

    /**
     * У аккаунта есть неучтённые заявки прежних попыток, а этот вид тратится только на учтённом.
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
