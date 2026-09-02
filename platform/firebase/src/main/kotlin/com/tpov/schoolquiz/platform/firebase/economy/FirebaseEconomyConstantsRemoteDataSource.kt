package com.tpov.schoolquiz.platform.firebase.economy

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsResponse
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import kotlinx.coroutines.tasks.await

/**
 * Таблица настроек экономики приезжает вызовом, а не чтением документа.
 *
 * Коллекция `configs/` закрыта правилами: если бы клиент читал её напрямую, таблицу можно было бы
 * подменить локально и торговаться с сервером о ценах.
 */
class FirebaseEconomyConstantsRemoteDataSource(
    private val functions: FirebaseFunctions,
) : EconomyConstantsRemoteDataSource {
    override suspend fun fetch(knownVersion: Long): EconomyConstantsResponse {
        val data =
            functions
                .getHttpsCallable(GET_ECONOMY_CONSTANTS)
                .withAppTimeout()
                .call(mapOf(KNOWN_VERSION to knownVersion))
                .await()
                .data as? Map<*, *>
                // Ответ не той формы — это неудача, а не «у тебя то же самое»: иначе сломанный
                // сервер выглядел бы как удачная синхронизация, и таблица не обновлялась бы никогда.
                ?: error("getEconomyConstants returned no table")

        if (data[UNCHANGED] == true) return EconomyConstantsResponse.Unchanged
        return EconomyConstantsResponse.Table(data.toEconomyConstants())
    }

    private companion object {
        const val GET_ECONOMY_CONSTANTS = "getEconomyConstants"
        const val KNOWN_VERSION = "knownVersion"
        const val UNCHANGED = "unchanged"
    }
}

/**
 * Разбор ответа.
 *
 * Каждое поле, которого нет или которое пришло не тем, чем ожидалось, падает на загрузочную копию,
 * а не на ноль: нулевой потолок запер бы аккаунт, а бесплатный турнир раздал бы попытки даром. Ту
 * же осторожность проявляет и сервер, читая документ, — здесь она вторая линия, на случай если
 * между ними окажется версия постарше.
 */
internal fun Map<*, *>.toEconomyConstants(): EconomyConstants {
    val fallback = EconomyConstants.BOOTSTRAP
    return EconomyConstants(
        version = longOr(this["version"], fallback.version),
        standard = (this["standard"] as? Map<*, *>).toRules(fallback.standard),
        plasma = (this["plasma"] as? Map<*, *>).toRules(fallback.plasma),
        activityPrices = (this["activityPrices"] as? Map<*, *>).toPrices(fallback.activityPrices),
        clockSkewToleranceMs = longOr(this["clockSkewToleranceMs"], fallback.clockSkewToleranceMs),
        auditEnabled = this["auditEnabled"] as? Boolean ?: fallback.auditEnabled,
    )
}

private fun Map<*, *>?.toRules(fallback: ChargeRules): ChargeRules {
    if (this == null) return fallback
    // Как на сервере: одна испорченная ступень отменяет всю лестницу, а не выпадает из неё. Иначе
    // устройство показывало бы цену по укороченной лестнице, а сервер списывал бы по начальной.
    val rungs = (this["priceLadder"] as? List<*>)?.map { wholeOrNull(it) }
    val ladder =
        if (rungs.isNullOrEmpty() || rungs.any { it == null || it < 0L }) fallback.priceLadder else rungs.map { it!! }
    val currency =
        (this["currency"] as? String)
            ?.let { name -> ShopCurrency.entries.firstOrNull { it.name == name } }
            ?: fallback.currency
    return ChargeRules(
        // Не `toInt()`: число выше Int.MAX_VALUE молча завернулось бы в маленькое, а потолок из
        // четырёх миллиардов — это опечатка оператора, а не пять зарядов.
        maxOwned = longOr(this["maxOwned"], fallback.maxOwned.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        regenMs = longOr(this["regenMs"], fallback.regenMs),
        priceLadder = ladder,
        currency = currency,
        requiresSettledAccount =
            this["requiresSettledAccount"] as? Boolean
                ?: fallback.requiresSettledAccount,
    )
}

private fun Map<*, *>?.toPrices(fallback: Map<ActivityKind, Int>): Map<ActivityKind, Int> =
    ActivityKind.entries.associateWith { kind ->
        longOr(this?.get(kind.name), fallback.getValue(kind).toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

/**
 * Firebase отдаёт числа то `Int`, то `Long`, то `Double`; отрицательное здесь бессмысленно.
 *
 * Дробное округляется вниз, как `Math.floor` на сервере: `-0.5` — это `-1` и отказ, а не ноль и
 * согласие. `toLong()` резал бы к нулю и принимал бы отрицательную дробь за пустой потолок.
 */
private fun longOr(
    value: Any?,
    fallback: Long,
): Long = wholeOrNull(value)?.takeIf { it >= 0L } ?: fallback

private fun wholeOrNull(value: Any?): Long? =
    when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Number -> value.toDouble().takeIf { it.isFinite() }?.let { kotlin.math.floor(it).toLong() }
        else -> null
    }
