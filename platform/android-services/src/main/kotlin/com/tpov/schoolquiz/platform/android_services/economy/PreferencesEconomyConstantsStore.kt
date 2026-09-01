package com.tpov.schoolquiz.platform.android_services.economy

import android.content.Context
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyConstantsStore
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import org.json.JSONObject

/**
 * Приехавшая таблица настроек — в обычных настройках приложения, рядом с выбором каденции.
 *
 * Не в общей базе намеренно: это не содержимое и не состояние аккаунта. У неё нет ни курсора, ни
 * владельца, она не участвует в синхронизации содержимого и не переживает смену аккаунта иначе,
 * чем переживает её выбор темы, — то есть переживает целиком, потому что цены от того, кто вошёл,
 * не зависят.
 *
 * Хранится тем же JSON, каким приехала. Разбор терпимый: испорченная или неполная запись целиком
 * отбрасывается, и репозиторий берёт загрузочную копию. Играть по начальным значениям честнее, чем
 * по половине записи, у которой потолок ноль.
 */
class PreferencesEconomyConstantsStore(context: Context) : EconomyConstantsStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): EconomyConstants? {
        val raw = prefs.getString(KEY_TABLE, null) ?: return null
        return runCatching { JSONObject(raw).toConstants() }.getOrNull()
    }

    override fun write(constants: EconomyConstants) {
        prefs.edit().putString(KEY_TABLE, constants.toJson().toString()).apply()
    }

    private fun EconomyConstants.toJson(): JSONObject =
        JSONObject()
            .put("version", version)
            .put("standard", standard.toJson())
            .put("plasma", plasma.toJson())
            .put(
                "activityPrices",
                JSONObject().also { prices ->
                    activityPrices.forEach { (kind, price) -> prices.put(kind.name, price) }
                },
            )
            .put("clockSkewToleranceMs", clockSkewToleranceMs)
            .put("auditEnabled", auditEnabled)

    private fun ChargeRules.toJson(): JSONObject =
        JSONObject()
            .put("maxOwned", maxOwned)
            .put("regenMs", regenMs)
            .put("priceLadder", priceLadder.joinToString(","))
            .put("currency", currency.name)
            .put("requiresSettledAccount", requiresSettledAccount)

    private fun JSONObject.toConstants(): EconomyConstants =
        EconomyConstants(
            version = getLong("version"),
            standard = getJSONObject("standard").toRules(),
            plasma = getJSONObject("plasma").toRules(),
            activityPrices =
                getJSONObject("activityPrices").let { prices ->
                    ActivityKind.entries.associateWith { prices.getInt(it.name) }
                },
            clockSkewToleranceMs = getLong("clockSkewToleranceMs"),
            auditEnabled = getBoolean("auditEnabled"),
        )

    private fun JSONObject.toRules(): ChargeRules =
        ChargeRules(
            maxOwned = getInt("maxOwned"),
            regenMs = getLong("regenMs"),
            priceLadder = getString("priceLadder").split(",").map { it.trim().toLong() },
            currency = ShopCurrency.valueOf(getString("currency")),
            requiresSettledAccount = getBoolean("requiresSettledAccount"),
        )

    private companion object {
        const val PREFS_NAME = "schoolquiz_economy"
        const val KEY_TABLE = "economy_constants"
    }
}
