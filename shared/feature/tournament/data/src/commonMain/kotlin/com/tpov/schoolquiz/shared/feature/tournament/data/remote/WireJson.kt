package com.tpov.schoolquiz.shared.feature.tournament.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Общий строгий слой разбора проводных фактов соревнования.
//
// Стиль `ArenaTicketWireParser`: разрешён только allowlist ключей серверной проекции, лишний или
// недостающий ключ, неверный тип и испорченное число — одно и то же «испорчено» ([MalformedWire]),
// никогда не молчаливое значение по умолчанию. Числа сервера читаются только в безопасном
// целочисленном диапазоне; текст сообщения сервера нигде не читается — только структура.

/** Байтый предел провода: проекции сервера ограничены, всё больше — не проекция. */
internal const val MAX_WIRE_BYTES = 500_000

internal const val MAX_SAFE_SERVER_INTEGER = 9_007_199_254_740_991L

/** Разбор корневого объекта провода. */
internal fun wireRoot(wire: String): JsonObject {
    if (wire.encodeToByteArray().size > MAX_WIRE_BYTES) malformedWire()
    return Json.parseToJsonElement(wire) as? JsonObject ?: malformedWire()
}

/** Ровно этот набор ключей — ни лишнего, ни недостающего. */
internal fun JsonObject.exact(vararg keys: String) {
    val expected = keys.toSet()
    if (keys.size != expected.size || this.keys != expected) malformedWire()
}

/** Ключи — подмножество allowlist'а; лишние ключи дрейфа проекции не читаются молча. */
internal fun JsonObject.within(vararg keys: String) {
    val allowed = keys.toSet()
    if (!this.keys.all { it in allowed }) malformedWire()
}

/**
 * Необязательный скаляр: ключ отсутствует или несёт явный null — честная пустота; ключ есть, но
 * значение не того типа (или испорченное число) — [MalformedWire], как у обязательных вариантов.
 * Приведение к «отсутствует» здесь и было дырой: `"yes"` превращался в «факт не назван».
 */
private inline fun <T> JsonObject.scalar(
    key: String,
    read: (JsonPrimitive) -> T?,
): T? =
    when (val value = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> read(value) ?: malformedWire()
        else -> malformedWire()
    }

internal fun JsonObject.text(key: String): String? = scalar(key) { it.takeIf { it.isString }?.content }

internal fun JsonObject.int(key: String): Int? = scalar(key) { it.takeUnless { it.isString }?.intOrNull }

internal fun JsonObject.whole(key: String): Long? =
    scalar(key) { it.takeUnless { it.isString }?.longOrNull?.takeIf { it in 0..MAX_SAFE_SERVER_INTEGER } }

internal fun JsonObject.fraction(key: String): Double? =
    scalar(key) { it.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() } }

internal fun JsonObject.bit(key: String): Boolean? = scalar(key) { it.takeUnless { it.isString }?.booleanOrNull }

internal fun JsonObject.element(key: String): JsonElement? = this[key]

internal fun JsonObject.obj(key: String): JsonObject? =
    when (val value = this[key]) {
        null, JsonNull -> null
        else -> value as? JsonObject ?: malformedWire()
    }

internal fun JsonObject.rows(key: String): JsonArray? =
    when (val value = this[key]) {
        null, JsonNull -> null
        else -> value as? JsonArray ?: malformedWire()
    }

/** Непустой текст или «испорчено». */
internal fun JsonObject.nonBlank(key: String): String = text(key)?.takeIf { it.isNotBlank() } ?: malformedWire()

internal fun JsonObject.requiredInt(key: String): Int = int(key) ?: malformedWire()

internal fun JsonObject.requiredWhole(key: String): Long = whole(key) ?: malformedWire()

internal fun JsonObject.requiredFraction(key: String): Double = fraction(key) ?: malformedWire()

internal fun JsonObject.requiredBit(key: String): Boolean = bit(key) ?: malformedWire()

internal fun JsonObject.requiredObject(key: String): JsonObject = obj(key) ?: malformedWire()

internal fun JsonObject.requiredRows(key: String): JsonArray = rows(key) ?: malformedWire()

/** Ключ есть и несёт строку или явный null; отсутствующий ключ — «испорчено». */
internal fun JsonObject.nullableText(key: String): String? =
    when (val value = this[key]) {
        null -> malformedWire()
        JsonNull -> null
        is JsonPrimitive -> value.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } ?: malformedWire()
        else -> malformedWire()
    }

internal fun malformedWire(): Nothing = throw MalformedWire()

/** Маркер испорченной проводной формы; граница транспорта переводит его в типовой отказ. */
class MalformedWire : Exception()
