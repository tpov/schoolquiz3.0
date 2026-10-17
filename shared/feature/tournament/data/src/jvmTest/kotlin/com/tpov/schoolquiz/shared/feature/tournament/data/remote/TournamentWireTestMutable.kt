package com.tpov.schoolquiz.shared.feature.tournament.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Мутилка проводных форм в тестах: JSON -> mutable -> JSON без лишних приведений. */
internal object TournamentWireTestMutable {
    fun deepCopy(element: JsonElement): MutableMap<String, Any?> =
        when (element) {
            is JsonObject -> element.entries.associate { it.key to unwrap(it.value) }.toMutableMap()
            else -> error("not an object")
        }

    fun build(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject((value as Map<String, Any?>).mapValues { build(it.value) })
            is List<*> -> JsonArray(value.map { build(it) })
            else -> error("unsupported $value")
        }

    private fun unwrap(element: JsonElement): Any? =
        when (element) {
            is JsonObject -> deepCopy(element)
            is JsonArray -> element.map { unwrap(it) }
            is JsonPrimitive ->
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.booleanOrNull
                    else -> element.longOrNull ?: element.content.toDoubleOrNull() ?: element.content
                }
            JsonNull -> null
        }
}
