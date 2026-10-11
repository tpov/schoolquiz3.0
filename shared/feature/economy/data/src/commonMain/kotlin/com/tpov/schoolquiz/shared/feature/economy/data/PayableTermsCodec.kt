package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Запись согласия на платные условия одним куском текста.
 *
 * Живёт в data, а не в платформенном хранилище: отображение «условия ↔ строка» — чистая логика над
 * доменной моделью, и тестировать её надо на JVM без Robolectric, как всю остальную data экономики.
 * Платформенному хранилищу остаётся положить строку в настройки и достать её обратно.
 *
 * Разбор терпимый: испорченная, неполная или противоречащая доменным требованиям запись целиком
 * даёт `null`, и гейт читает «согласия не было». Спросить игрока заново дешевле, чем провести
 * платную операцию по половине согласия.
 */
object PayableTermsCodec {
    fun toJson(terms: PayableTerms): String =
        buildJsonObject {
            put("version", terms.version)
            put("standard", terms.standard.toJson())
            put("plasma", terms.plasma.toJson())
            put(
                "activityPrices",
                buildJsonObject {
                    ActivityKind.entries.forEach { kind -> put(kind.name, terms.priceOf(kind)) }
                },
            )
        }.toString()

    fun fromJson(raw: String): PayableTerms? =
        runCatching {
            val body = Json.parseToJsonElement(raw).jsonObject
            PayableTerms(
                version = body.getValue("version").jsonPrimitive.long,
                standard = body.getValue("standard").jsonObject.toRules(),
                plasma = body.getValue("plasma").jsonObject.toRules(),
                activityPrices =
                    body.getValue("activityPrices").jsonObject.let { prices ->
                        ActivityKind.entries.associateWith { kind ->
                            prices.getValue(kind.name).jsonPrimitive.int
                        }
                    },
            )
        }.getOrNull()

    private fun ChargeRules.toJson(): JsonObject =
        buildJsonObject {
            put("maxOwned", maxOwned)
            put("regenMs", regenMs)
            put("priceLadder", buildJsonArray { priceLadder.forEach { add(it) } })
            put("currency", currency.name)
            put("requiresSettledAccount", requiresSettledAccount)
            put("premiumRegenDivisor", premiumRegenDivisor)
        }

    private fun JsonObject.toRules(): ChargeRules =
        ChargeRules(
            maxOwned = getValue("maxOwned").jsonPrimitive.int,
            regenMs = getValue("regenMs").jsonPrimitive.long,
            priceLadder = getValue("priceLadder").jsonArray.map { it.jsonPrimitive.long },
            currency = ShopCurrency.valueOf(getValue("currency").jsonPrimitive.content),
            requiresSettledAccount = getValue("requiresSettledAccount").jsonPrimitive.boolean,
            // Записи до появления ручки читаются как «не быстрее»; отбрасывать их целиком незачем.
            premiumRegenDivisor = this["premiumRegenDivisor"]?.jsonPrimitive?.intOrNull ?: 1,
        )
}
