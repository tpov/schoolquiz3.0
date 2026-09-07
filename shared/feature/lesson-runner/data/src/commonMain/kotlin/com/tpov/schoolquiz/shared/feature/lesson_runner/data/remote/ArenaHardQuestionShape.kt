package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Validate the actual retained HARD bytes, not only an ignoreUnknownKeys display projection.
 * A redacted discriminator is not proof that arbitrary sibling/nested fields contain no key.
 * Do not scrub or re-encode: valid frozen IDs/order/payload stay byte-identical; unsafe ones fail.
 */
internal fun validateArenaHardQuestionShape(
    payload: String,
    content: JsonObject,
) {
    rejectDuplicateKeys(payload)
    val type = content.getValueOrFail("type").strictString()
    val additional =
        when (type) {
            "SingleChoiceRedacted", "MultipleChoiceRedacted" -> setOf("options")
            "OrderingRedacted" -> setOf("items")
            "FillBlankRedacted" -> setOf("blanks", "candidates", "protectedTextSegments")
            // Survey is deliberately unscored and has no redacted sibling. Its declared optional
            // author info is allowed; arbitrary extra answer/key fields are not.
            "Survey" -> setOf("options", "allowMultiple", "info")
            else -> invalidShape()
        }
    content.onlyFields(BASE_FIELDS + additional)
    content.getValueOrFail("text").strictString()
    content.getValueOrFail("imageUrl").nullableString()
    content["id"]?.strictString()
    content["difficulty"]?.strictString()
    when (type) {
        "SingleChoiceRedacted", "MultipleChoiceRedacted" -> content.getValueOrFail("options").publicRows()
        "OrderingRedacted" -> content.getValueOrFail("items").publicRows()
        "FillBlankRedacted" -> {
            content.getValueOrFail("blanks").stringArray()
            content.getValueOrFail("candidates").publicRows()
            content["protectedTextSegments"]?.stringArray()
        }
        "Survey" -> {
            content.getValueOrFail("options").publicRows()
            content["allowMultiple"]?.let {
                val boolean = it as? JsonPrimitive ?: invalidShape()
                if (boolean.isString || boolean.booleanOrNull == null) invalidShape()
            }
            content["info"]?.nullableString()
        }
    }
}

private val BASE_FIELDS = setOf("type", "id", "difficulty", "text", "imageUrl")
private val ROW_FIELDS = setOf("id", "text")

private fun JsonObject.onlyFields(allowed: Set<String>) {
    if (keys.any { it !in allowed }) invalidShape()
}

private fun JsonObject.getValueOrFail(key: String): JsonElement = get(key) ?: invalidShape()

private fun JsonElement.strictString(): String {
    val value = this as? JsonPrimitive ?: invalidShape()
    if (!value.isString) invalidShape()
    return value.content
}

private fun JsonElement.nullableString() {
    if (this != JsonNull) strictString()
}

private fun JsonElement.stringArray() {
    val rows = this as? JsonArray ?: invalidShape()
    rows.forEach { it.strictString() }
}

private fun JsonElement.publicRows() {
    val rows = this as? JsonArray ?: invalidShape()
    rows.forEach {
        val row = it as? JsonObject ?: invalidShape()
        row.onlyFields(ROW_FIELDS)
        row.getValueOrFail("id").strictString()
        row.getValueOrFail("text").strictString()
    }
}

/**
 * JsonObject keeps only the last duplicate member, but the retained raw string would still contain
 * the earlier value (possibly an entire private row). The payload was already parsed as valid JSON;
 * this pass only tracks decoded object member names, skipping string values and escaped quotes.
 */
private fun rejectDuplicateKeys(payload: String) {
    val stack = mutableListOf<KeyFrame>()
    var index = 0
    while (index < payload.length) {
        when (payload[index]) {
            '{' -> stack.add(KeyFrame(mutableSetOf(), expectingKey = true))
            '[' -> stack.add(KeyFrame(null, expectingKey = false))
            '}', ']' -> stack.removeAt(stack.lastIndex)
            ',' -> stack.lastOrNull()?.let { it.expectingKey = it.keys != null }
            '"' -> {
                val start = index
                index = closingQuoteIndex(payload, start)
                stack.lastOrNull()?.recordKey(payload, start, index + 1)
            }
        }
        index++
    }
}

private fun closingQuoteIndex(
    payload: String,
    start: Int,
): Int {
    var index = start + 1
    while (index < payload.length && payload[index] != '"') {
        index += if (payload[index] == '\\') 2 else 1
    }
    return index
}

private fun KeyFrame.recordKey(
    payload: String,
    start: Int,
    endExclusive: Int,
) {
    if (!expectingKey) return
    val key = Json.parseToJsonElement(payload.substring(start, endExclusive)).strictString()
    if (keys?.add(key) != true) invalidShape()
    expectingKey = false
}

private class KeyFrame(val keys: MutableSet<String>?, var expectingKey: Boolean)

private fun invalidShape(): Nothing = throw ArenaRemoteFailure(ArenaRemoteFailureKind.MALFORMED_RESPONSE)
