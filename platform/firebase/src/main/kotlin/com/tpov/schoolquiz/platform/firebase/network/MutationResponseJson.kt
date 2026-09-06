package com.tpov.schoolquiz.platform.firebase.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/** Deterministic JSON of the complete callable data, including unknown future fields. */
internal fun mutationResponseJson(value: Any?): String {
    require(value is Map<*, *>) { "Mutation response must be an object" }
    return value.toReceiptJson().toString()
}

private fun Any?.toReceiptJson(): JsonElement =
    when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> {
            // Firebase may deliver 1 as an Integer, Long or Double. Canonicalize its JSON number,
            // without converting a large Long through Double or accepting NaN/infinity.
            Json.parseToJsonElement(BigDecimal(toString()).stripTrailingZeros().toPlainString())
        }
        is Map<*, *> -> {
            require(keys.all { it is String }) { "Mutation result contains a non-string key" }
            JsonObject(entries.sortedBy { it.key as String }.associate { (k, v) -> k as String to v.toReceiptJson() })
        }
        is List<*> -> JsonArray(map { it.toReceiptJson() })
        else -> error("Unsupported mutation result value")
    }
