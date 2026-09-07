@file:Suppress("MagicNumber") // Exact generated protocol values and independent arithmetic oracle.

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogBounds
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogShelf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaLessonCatalogWireParserTest {
    private val fixtures =
        requireNotNull(javaClass.classLoader.getResourceAsStream("arena-catalog-client-fixtures.json"))
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    private val parser = ArenaLessonCatalogWireParser()
    private val request = ArenaLessonCatalogRequest("player")

    @Test
    fun actualProducerReturnsOnlyTypedPublicMetadataWithPriorInclusiveOrder() {
        val page = parser.parse(wire("full"), request)
        assertEquals("player", page.ownerUid)
        assertEquals("arena-lesson-catalog-v1", page.catalogVersion)
        assertEquals("PREPARE_REQUIRED", page.entryAvailability)
        assertEquals(1_788_739_200_000L, page.serverNowMs)
        assertEquals(7, page.totalEligibleLessons)
        assertEquals(
            listOf("high", "z-near", "a-near", "a-unrated", "b-mid", "c-mid", "low"),
            page.lessons.map { it.lessonId },
        )
        assertEquals(listOf(100, 75, 51, 1, 26, 26, 1), page.lessons.map { it.priceNolics })
        val high = page.lessons.first()
        assertEquals("Title high", high.title)
        assertEquals("author", high.authorUid)
        assertEquals(1L, high.contentVersion)
        assertEquals(3L, high.rating.sum)
        assertEquals(1L, high.rating.count)
        assertEquals(1L, high.rating.version)
        assertEquals(ArenaLessonCatalogScore(43, 21), high.rating.weightedScore)
        assertEquals(ArenaLessonCatalogShelf.ARENA, high.placement.shelf)
        assertEquals(ArenaLessonCatalogShelf.REVIEW, high.placement.previousShelf)
        assertEquals(1L, high.placement.version)
        assertEquals(page.serverNowMs - 1, high.placement.updatedAtMs)
        assertNull(page.nextCursor)
    }

    @Test
    fun completePaginationBindsFingerprintOwnerSeenIdsAndExactBoundary() {
        val first = parser.parse(wire("first"), request.copy(pageSize = 3))
        val cursor = requireNotNull(first.nextCursor)
        assertEquals("player", cursor.ownerUid)
        assertEquals("a-near", cursor.after.lessonId)
        assertEquals(first.snapshotFingerprint, cursor.snapshotFingerprint)
        assertEquals(setOf("high", "z-near", "a-near"), cursor.seenLessonIds)
        val second = parser.parse(wire("second"), request.copy(pageSize = 3, cursor = cursor))
        val last = parser.parse(wire("last"), request.copy(pageSize = 3, cursor = second.nextCursor))
        assertNull(last.nextCursor)
        assertEquals(parser.parse(wire("full"), request).lessons, first.lessons + second.lessons + last.lessons)
    }

    @Test
    fun entirelyAbsentRatingAndUnavailableOptionalMetadataRemainExplicit() {
        val card = parser.parse(wire("nullable"), request).lessons.single()
        assertNull(card.title)
        assertNull(card.placement.previousShelf)
        assertNull(card.placement.version)
        assertNull(card.placement.updatedAtMs)
        assertEquals(0L, card.rating.count)
        assertEquals(0L, card.rating.sum)
        assertEquals(0L, card.rating.version)
        assertEquals(ArenaLessonCatalogScore(40, 20), card.rating.weightedScore)
        assertEquals(1, card.priceNolics)
        val empty = parser.parse(wire("empty"), request)
        assertEquals(0, empty.totalEligibleLessons)
        assertTrue(empty.lessons.isEmpty())
        assertNull(empty.nextCursor)
    }

    @Test
    fun ratedEqualScorePriceIsCheckedAcrossInterveningUnratedBoundary() {
        val first = parser.parse(wire("tieFirst"), request.copy(pageSize = 2))
        val cursor = requireNotNull(first.nextCursor)
        assertEquals(1, cursor.after.priceNolics)
        assertEquals(0L, cursor.after.rating.count)
        assertEquals(100, cursor.ratedPriceAtBoundary)
        val next = request.copy(pageSize = 2, cursor = cursor)
        assertEquals(100, parser.parse(wire("tieLast"), next).lessons.first().priceNolics)
        reject(changed("tieLast", "lessons/0/priceNolics", JsonPrimitive(99)), next)
    }

    @Test
    fun exactComparisonDoesNotCollapseNearbyScoresOrOverflowCrossProducts() {
        val rows = parser.parse(wire("full"), request).lessons
        val higher = rows[1].rating.weightedScore
        val lower = rows[2].rating.weightedScore
        assertTrue(higher > lower)
        assertEquals(higher.numerator.toDouble() / higher.denominator, lower.numerator.toDouble() / lower.denominator)
        assertTrue(
            BigInteger.valueOf(
                higher.numerator,
            ).multiply(BigInteger.valueOf(lower.denominator)) > BigInteger.valueOf(Long.MAX_VALUE),
        )
        assertEquals(0, ArenaLessonCatalogScore(42, 21).compareTo(ArenaLessonCatalogScore(44, 22)))
        assertEquals(0, ArenaLessonCatalogScore(0, 1).compareTo(ArenaLessonCatalogScore(0, 999)))
    }

    @Test
    fun continuedFractionMatchesIndependentJvmBigIntegerOracle() {
        val random = Random(20260907)
        val max = ArenaLessonCatalogBounds.MAX_SAFE_INTEGER
        val values =
            mutableListOf(
                ArenaLessonCatalogScore(0, max),
                ArenaLessonCatalogScore(max, 1),
                ArenaLessonCatalogScore(max, max),
            )
        repeat(500) { values += ArenaLessonCatalogScore(random.nextLong(max + 1), random.nextLong(1, max + 1)) }
        values += ArenaLessonCatalogScore(7_540_113_804_746_346, 4_660_046_610_375_530)
        values += ArenaLessonCatalogScore(4_660_046_610_375_530, 2_880_067_194_370_816)
        for ((left, right) in values.zipWithNext()) {
            val oracle =
                BigInteger.valueOf(left.numerator).multiply(BigInteger.valueOf(right.denominator))
                    .compareTo(BigInteger.valueOf(right.numerator).multiply(BigInteger.valueOf(left.denominator)))
            assertEquals(oracle, left.compareTo(right))
            assertEquals(-oracle, right.compareTo(left))
        }
    }

    @Test
    fun requiredNumericAuthorityNeverDefaultsOrCoercesStrings() {
        val paths =
            listOf(
                "serverNowMs", "totalEligibleLessons", "lessons/0/contentVersion", "lessons/0/priceNolics",
                "lessons/0/rating/sum", "lessons/0/rating/count", "lessons/0/rating/ratingVersion",
                "lessons/0/rating/weightedScore/numerator", "lessons/0/rating/weightedScore/denominator",
            )
        for (path in paths) {
            for (value in listOf(
                null,
                JsonNull,
                JsonPrimitive("1"),
                JsonPrimitive(-1),
                JsonPrimitive(0.5),
                JsonPrimitive(9_007_199_254_740_992L),
            )) {
                reject(changed("full", path, value))
            }
        }
    }

    @Test
    fun invalidPriceAndInconsistentAggregateOrWeightFailClosed() {
        for (price in listOf(0, 101)) reject(changed("full", "lessons/0/priceNolics", JsonPrimitive(price)))
        reject(changed("full", "lessons/3/priceNolics", JsonPrimitive(26)))
        reject(changed("full", "lessons/5/priceNolics", JsonPrimitive(27)))
        reject(changed("full", "lessons/0/rating/count", JsonPrimitive(4)))
        reject(changed("full", "lessons/0/rating/weightedScore/numerator", JsonPrimitive(42)))
        reject(changed("full", "lessons/0/rating/weightedScore/denominator", JsonPrimitive(0)))
    }

    @Test
    fun unknownKeysPrivateContentAndWrongMetadataAreRejected() {
        for (path in listOf(
            "questions",
            "lessons/0/history",
            "lessons/0/privateKey",
            "lessons/0/rating/secret",
            "lessons/0/placement/reviewerUid",
        )) {
            reject(changed("full", path, JsonPrimitive("private")))
        }
        for (path in listOf(
            "catalogVersion",
            "status",
            "entryAvailability",
            "lessons/0/placement/shelf",
            "lessons/0/placement/previousShelf",
        )) {
            reject(changed("full", path, JsonPrimitive("UNKNOWN")))
        }
        for (path in listOf("lessons/0/lessonId", "lessons/0/authorUid")) {
            for (id in listOf(
                "",
                "a/b",
                ".",
                "..",
                "__reserved__",
                "x".repeat(1501),
            )) reject(changed("full", path, JsonPrimitive(id)))
        }
        reject(changed("full", "lessons/0/title", JsonPrimitive(" ")))
        reject(changed("full", "lessons/0/placement/placementVersion", JsonPrimitive(0)))
        reject(changed("full", "lessons/0/placement/updatedAtMs", JsonPrimitive(-1)))
    }

    @Test
    fun reorderedAndDuplicatedCardsAreRejectedWithinAndAcrossPages() {
        val full = fixtures.getValue("full").jsonObject
        val rows = full.getValue("lessons").jsonArray
        reject(JsonObject(full + ("lessons" to JsonArray(rows.reversed()))).toString())
        reject(changed("full", "lessons/1", rows[0]))
        val first = parser.parse(wire("first"), request.copy(pageSize = 3))
        reject(changed("second", "lessons/0", rows[1]), request.copy(pageSize = 3, cursor = first.nextCursor))
    }

    @Test
    fun cursorAndSnapshotChangesAreNotSilentlyAcceptedAsAnotherPage() {
        val first = parser.parse(wire("first"), request.copy(pageSize = 3))
        val next = request.copy(pageSize = 3, cursor = first.nextCursor)
        reject(changed("second", "snapshotFingerprint", JsonPrimitive("f".repeat(64))), next)
        reject(changed("second", "totalEligibleLessons", JsonPrimitive(8)), next)
        reject(changed("first", "nextCursor", JsonPrimitive("not-canonical")), request.copy(pageSize = 3))
        reject(
            changed("first", "nextCursor", fixtures.getValue("second").jsonObject.getValue("nextCursor")),
            request.copy(pageSize = 3),
        )
        reject(changed("first", "nextCursor", JsonNull), request.copy(pageSize = 3))
        reject(wire("full"), request.copy(pageSize = 3))
        reject(changed("full", "totalEligibleLessons", JsonPrimitive(1001)))
        reject(changed("full", "lessons/0/title", JsonPrimitive("x".repeat(900001))))
    }

    @Test
    fun malformedEnvelopeAndMissingExplicitNullablesAreRefused() {
        for (wire in listOf("", "{", "[]", "null")) reject(wire)
        for (value in listOf(JsonNull, JsonPrimitive("F".repeat(64)), JsonPrimitive("f".repeat(63)))) {
            reject(changed("full", "snapshotFingerprint", value))
        }
        for (path in listOf(
            "nextCursor",
            "lessons/0/title",
            "lessons/0/placement/previousShelf",
            "lessons/0/placement/placementVersion",
            "lessons/0/placement/updatedAtMs",
        )) {
            reject(changed("nullable", path, null))
        }
        reject(changed("full", "lessons/0/rating", JsonNull))
        reject(changed("full", "lessons/0/rating", JsonObject(emptyMap())))
    }

    @Test
    fun localRequestCannotCrossOwnerOrAcceptInvalidBounds() {
        val cursor = parser.parse(wire("first"), request.copy(pageSize = 3)).nextCursor
        assertFailsWith<IllegalArgumentException> { ArenaLessonCatalogRequest("other", cursor = cursor) }
        for (size in listOf(0, 51)) assertFailsWith<IllegalArgumentException> { request.copy(pageSize = size) }
        assertFailsWith<IllegalArgumentException> { request.copy(ownerUid = "a/b") }
        assertFailsWith<IllegalArgumentException> { ArenaLessonCatalogScore(1, 0) }
        assertFailsWith<IllegalArgumentException> { ArenaLessonCatalogScore(Long.MAX_VALUE, 1) }
    }

    private fun wire(name: String): String = fixtures.getValue(name).toString()

    private fun changed(
        name: String,
        path: String,
        value: JsonElement?,
    ): String =
        rewrite(
            fixtures.getValue(name),
            path.split('/'),
            value,
        ).toString()

    private fun rewrite(
        element: JsonElement,
        path: List<String>,
        value: JsonElement?,
    ): JsonElement =
        when (element) {
            is JsonObject ->
                JsonObject(
                    element.toMutableMap().apply {
                        if (path.size == 1) {
                            if (value == null) remove(path[0]) else put(path[0], value)
                        } else {
                            put(path[0], rewrite(getValue(path[0]), path.drop(1), value))
                        }
                    },
                )
            is JsonArray ->
                JsonArray(
                    element.mapIndexed { index, child ->
                        if (index == path[0].toInt()) {
                            if (path.size == 1) requireNotNull(value) else rewrite(child, path.drop(1), value)
                        } else {
                            child
                        }
                    },
                )
            else -> error("Test path must target an object or array")
        }

    private fun reject(
        wire: String,
        request: ArenaLessonCatalogRequest = this.request,
    ) {
        assertEquals(
            ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE,
            assertFailsWith<ArenaLessonCatalogFailure> { parser.parse(wire, request) }.kind,
        )
    }
}
