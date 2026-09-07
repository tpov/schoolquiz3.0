package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogBounds
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPlacement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogShelf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** Strict SDK-unwrapped public data. Unknown/missing numbers are rejected, never replaced by zero. */
class ArenaLessonCatalogWireParser {
    fun parse(
        wire: String,
        request: ArenaLessonCatalogRequest,
    ): ArenaLessonCatalogPage =
        try {
            parsePage(wire, request)
        } catch (_: IllegalArgumentException) {
            malformed()
        }

    private fun parsePage(
        wire: String,
        request: ArenaLessonCatalogRequest,
    ): ArenaLessonCatalogPage {
        valid(wire.encodeToByteArray().size <= ArenaLessonCatalogBounds.MAX_RESPONSE_BYTES)
        val root = Json.parseToJsonElement(wire).objectValue()
        root.exactKeys(
            "status",
            "catalogVersion",
            "snapshotFingerprint",
            "serverNowMs",
            "entryAvailability",
            "totalEligibleLessons",
            "lessons",
            "nextCursor",
        )
        valid(root.string("status") == "OK")
        valid(root.string("catalogVersion") == ArenaLessonCatalogBounds.VERSION)
        valid(root.string("entryAvailability") == ArenaLessonCatalogBounds.PREPARE_REQUIRED)
        val fingerprint = root.string("snapshotFingerprint").also(::fingerprint)
        val now = root.integer("serverNowMs")
        val total =
            root.integer(
                "totalEligibleLessons",
                maximum = ArenaLessonCatalogBounds.MAX_LESSONS.toLong(),
            ).toInt()
        val rows = root.required("lessons") as? JsonArray ?: malformed()
        valid(rows.size <= request.pageSize && rows.size <= total)
        val cards = rows.map { card(it.objectValue()) }
        val boundaryPrice = validateOrder(cards, fingerprint, total, request)
        val seen = request.cursor?.seenLessonIds.orEmpty() + cards.map { it.lessonId }
        val token = root.nullableString("nextCursor")
        val next =
            if (token == null) {
                valid(seen.size == total)
                null
            } else {
                valid(cards.size == request.pageSize && seen.size < total)
                val last = cards.lastOrNull() ?: malformed()
                validCursor(token, fingerprint, last.lessonId)
                valid(token != request.cursor?.token)
                ArenaLessonCatalogCursor(token, request.ownerUid, fingerprint, last, total, seen.toSet(), boundaryPrice)
            }
        return ArenaLessonCatalogPage(request.ownerUid, fingerprint, now, total, cards.toList(), next)
    }

    private fun validateOrder(
        cards: List<ArenaLessonCatalogCard>,
        fingerprint: String,
        total: Int,
        request: ArenaLessonCatalogRequest,
    ): Int? {
        val cursor = request.cursor
        if (cursor != null) {
            valid(cursor.snapshotFingerprint == fingerprint && cursor.totalEligibleLessons == total)
            validCursor(cursor.token, fingerprint, cursor.after.lessonId)
            if (cursor.after.rating.count > 0) valid(cursor.ratedPriceAtBoundary == cursor.after.priceNolics)
        }
        val seen = cursor?.seenLessonIds.orEmpty().toMutableSet()
        var previous = cursor?.after
        var ratedPrice = cursor?.ratedPriceAtBoundary
        for (card in cards) {
            valid(seen.add(card.lessonId))
            val order =
                previous?.let { prior ->
                    prior.rating.weightedScore.compareTo(card.rating.weightedScore).also {
                        valid(it > 0 || it == 0 && prior.lessonId < card.lessonId)
                    }
                }
            if (order != 0) ratedPrice = null
            if (card.rating.count > 0) {
                if (ratedPrice != null) valid(ratedPrice == card.priceNolics)
                ratedPrice = card.priceNolics
            }
            previous = card
        }
        return ratedPrice
    }

    private fun card(raw: JsonObject): ArenaLessonCatalogCard {
        raw.exactKeys("lessonId", "contentVersion", "title", "authorUid", "rating", "priceNolics", "placement")
        val rating = rating(raw.required("rating").objectValue())
        val price = raw.integer("priceNolics", 1, ArenaLessonCatalogBounds.MAX_PRICE.toLong()).toInt()
        if (rating.count == 0L) valid(price == 1)
        val title = raw.nullableString("title")
        valid(title == null || title.isNotBlank())
        return ArenaLessonCatalogCard(
            raw.documentId("lessonId"),
            raw.integer("contentVersion", 1),
            title,
            raw.documentId("authorUid"),
            rating,
            price,
            placement(raw.required("placement").objectValue()),
        )
    }

    private fun rating(raw: JsonObject): ArenaLessonCatalogRating {
        raw.exactKeys("sum", "count", "weightedScore", "ratingVersion")
        val sum = raw.integer("sum")
        val count = raw.integer("count")
        // Even three JS-safe values fit in Long; cross-products of two such values do not.
        valid(sum >= count && sum <= MAX_RATING * count)
        val weighted = raw.required("weightedScore").objectValue()
        weighted.exactKeys("numerator", "denominator")
        val numerator = weighted.integer("numerator")
        val denominator = weighted.integer("denominator", 1)
        valid(numerator == sum + ArenaLessonCatalogBounds.PRIOR_SUM)
        valid(denominator == count + ArenaLessonCatalogBounds.PRIOR_COUNT)
        return ArenaLessonCatalogRating(
            sum,
            count,
            ArenaLessonCatalogScore(numerator, denominator),
            raw.integer("ratingVersion"),
        )
    }

    private fun placement(raw: JsonObject): ArenaLessonCatalogPlacement {
        raw.exactKeys("shelf", "previousShelf", "placementVersion", "updatedAtMs")
        valid(raw.string("shelf") == "arena")
        val previous =
            raw.nullableString("previousShelf")?.let { name ->
                ArenaLessonCatalogShelf.entries.singleOrNull { it.name.lowercase() == name } ?: malformed()
            }
        return ArenaLessonCatalogPlacement(
            previous,
            raw.nullableInteger("placementVersion", 1),
            raw.nullableInteger("updatedAtMs"),
        )
    }

    private fun validCursor(
        token: String,
        fingerprint: String,
        afterLessonId: String,
    ) {
        valid(token.length in 1..ArenaLessonCatalogBounds.MAX_CURSOR_BYTES)
        val expected =
            buildJsonObject {
                put("afterLessonId", afterLessonId)
                put("snapshot", fingerprint)
                put("v", CURSOR_VERSION)
            }
        // Server canonical JSON sorts these three keys. Encode rather than trust opaque decoded fields.
        valid(
            token ==
                Base64.UrlSafe.withPadding(
                    Base64.PaddingOption.ABSENT,
                ).encode(expected.toString().encodeToByteArray()),
        )
    }

    private fun fingerprint(value: String) {
        valid(value.length == SHA256_HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' })
    }

    private fun JsonObject.exactKeys(vararg keys: String) = valid(this.keys == keys.toSet())

    private fun JsonObject.required(key: String): JsonElement = get(key) ?: malformed()

    private fun JsonObject.string(key: String): String {
        val value = required(key) as? JsonPrimitive ?: malformed()
        valid(value.isString)
        return value.content
    }

    private fun JsonObject.nullableString(key: String): String? = if (required(key) == JsonNull) null else string(key)

    private fun JsonObject.documentId(key: String): String =
        string(key).also {
            valid(it.isNotBlank() && '/' !in it && it != "." && it != "..")
            valid(!(it.startsWith("__") && it.endsWith("__")) && it.encodeToByteArray().size <= MAX_DOCUMENT_ID_BYTES)
        }

    private fun JsonObject.nullableInteger(
        key: String,
        minimum: Long = 0,
    ): Long? = if (required(key) == JsonNull) null else integer(key, minimum)

    private fun JsonObject.integer(
        key: String,
        minimum: Long = 0,
        maximum: Long = ArenaLessonCatalogBounds.MAX_SAFE_INTEGER,
    ): Long {
        val value = required(key) as? JsonPrimitive ?: malformed()
        valid(!value.isString)
        return (value.longOrNull ?: malformed()).also { valid(it in minimum..maximum) }
    }

    private companion object {
        const val MAX_RATING = 3L
        const val MAX_DOCUMENT_ID_BYTES = 1500
        const val SHA256_HEX_LENGTH = 64
        const val CURSOR_VERSION = 1
    }
}

private fun JsonElement.objectValue(): JsonObject = this as? JsonObject ?: malformed()

private fun valid(condition: Boolean) {
    if (!condition) malformed()
}

private fun malformed(): Nothing = throw ArenaLessonCatalogFailure(ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE)
