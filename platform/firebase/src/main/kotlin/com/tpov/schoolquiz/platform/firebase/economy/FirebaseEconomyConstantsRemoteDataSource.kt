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
private fun Map<*, *>.toEconomyConstants(): EconomyConstants {
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
    val ladder =
        (this["priceLadder"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong()?.takeIf { rung -> rung >= 0L } }
            ?.takeIf { it.isNotEmpty() }
            ?: fallback.priceLadder
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

/** Firebase отдаёт числа то `Int`, то `Long`, то `Double`; отрицательное здесь бессмысленно. */
private fun longOr(
    value: Any?,
    fallback: Long,
): Long = (value as? Number)?.toLong()?.takeIf { it >= 0L } ?: fallback
