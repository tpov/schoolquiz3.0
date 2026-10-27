package com.tpov.schoolquiz.platform.firebase.economy

import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimStatus
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор конверта `{replayed, result}` вызываемого `submitGiftBoxOperation`.
 *
 * Тело заявки пишет `GiftClaimRepositoryImpl`, сервер — `planBoxOperation` в `functions/box-service.js`;
 * этот читатель — третий участник контракта, и его строгость проверяется здесь: чужой владелец,
 * чужой ключ, чужой вид, незнакомый статус и неточное число не могут стать «успехом без эффекта».
 */
class GiftClaimEnvelopeMappingTest {
    private val request =
        GiftClaimWireRequest(
            kind = GiftClaimKind.PREMIUM_DAILY,
            ownerUid = "owner",
            operationId = "daily-id-0000000001",
            baseBoxStreakDays = null,
            baseNextBoxAtMs = null,
            claimedBoxStreakDays = null,
            claimedBoxesEarned = null,
        )

    private fun wire(
        status: String = "CLAIMED",
        earned: Int = 1,
        kind: String = "PREMIUM_DAILY",
        extra: String = "",
    ) = """{"replayed":false,"result":{"schemaVersion":1,"ownerUid":"owner",
            "operationId":"daily-id-0000000001","kind":"$kind","status":"$status",
            "boxCount":2,"boxStreakDays":10,"nextBoxAtMs":3,"nextBonusBoxAtMs":25,
            "earnedBoxes":$earned,"premiumUntilMs":26$extra}}
        """

    private fun parse(json: String = wire()) = giftClaimOutcomeFrom(jsonToMap(json), request)

    private fun parseMap(
        map: Map<*, *>,
        request: GiftClaimWireRequest = this.request,
    ) = giftClaimOutcomeFrom(map, request)

    @Test fun freshAnswerAndReplayMapToTheSameTypedOutcome() {
        val fresh = parse()
        assertEquals(GiftClaimStatus.CLAIMED, fresh.status)
        assertEquals(1, fresh.earnedBoxes)
        assertEquals(2, fresh.boxCount)
        assertEquals(25L, fresh.nextBonusBoxAtMs)
        assertEquals(
            fresh.copy(replayed = true),
            parseMap(jsonToMap(wire().replace("\"replayed\":false", "\"replayed\":true"))),
        )
    }

    @Test fun everyStatusEachKindCanProduceIsAccepted() {
        assertEquals(
            GiftClaimStatus.NO_CHANGE,
            parseMap(
                jsonToMap(wire(status = "NO_CHANGE", earned = 0, kind = "VISIT")),
                GiftClaimWireRequest(GiftClaimKind.VISIT, "owner", "daily-id-0000000001", null, null, null, null),
            ).status,
        )
        assertEquals(
            GiftClaimStatus.STALE_BASE,
            parseMap(
                jsonToMap(wire(status = "STALE_BASE", earned = 0, kind = "OFFLINE_STREAK")),
                GiftClaimWireRequest(
                    GiftClaimKind.OFFLINE_STREAK, "owner", "daily-id-0000000001", 9L, 1000L, 10L, 1L,
                ),
            ).status,
        )
        assertEquals(GiftClaimStatus.DAILY_LIMIT, parse(wire(status = "DAILY_LIMIT", earned = 0)).status)
        assertEquals(
            GiftClaimStatus.PREMIUM_REQUIRED,
            parse(wire(status = "PREMIUM_REQUIRED", earned = 0)).status,
        )
    }

    @Test fun identitySchemaKindAndStatusAreExact() {
        assertThrows(RuntimeException::class.java) { parse(wire().replace("\"ownerUid\":\"owner\"", "\"ownerUid\":\"other\"")) }
        assertThrows(RuntimeException::class.java) { parse(wire().replace("daily-id-0000000001", "daily-id-0000000002")) }
        assertThrows(RuntimeException::class.java) { parse(wire().replace("\"kind\":\"PREMIUM_DAILY\"", "\"kind\":\"VISIT\"")) }
        assertThrows(RuntimeException::class.java) { parse(wire().replace("\"schemaVersion\":1", "\"schemaVersion\":2")) }
        // CLAIMED невозможно за визитом без коробок, а OPEN — не вид заявки вовсе.
        assertThrows(RuntimeException::class.java) { parse(wire(status = "OPENED")) }
        assertThrows(RuntimeException::class.java) { parse(wire(status = "UNKNOWN")) }
    }

    @Test fun earnedBoxesMustAgreeWithTheStatus() {
        assertThrows(RuntimeException::class.java) { parse(wire(earned = 0)) }
        assertThrows(RuntimeException::class.java) { parse(wire(status = "DAILY_LIMIT", earned = 1)) }
    }

    @Test fun offlineStreakClaimCarriesOneBoxPerStreakDayBeyondTheBase() {
        // Честная офлайн-неделя: `boxAccrualVerdict` принимает 7 коробок разом, и это обычный
        // ответ, а не нечитаемый конверт (см. GiftBoxAccrualTest `…seven days and seven boxes`).
        val week =
            parseMap(
                jsonToMap(wire(status = "CLAIMED", earned = 7, kind = "OFFLINE_STREAK")),
                GiftClaimWireRequest(
                    GiftClaimKind.OFFLINE_STREAK, "owner", "daily-id-0000000001", 10L, 1000L, 17L, 7L,
                ),
            )
        assertEquals(GiftClaimStatus.CLAIMED, week.status)
        assertEquals(7, week.earnedBoxes)

        // CLAIMED без коробок невозможен и у офлайн-заявки.
        assertThrows(RuntimeException::class.java) {
            parseMap(
                jsonToMap(wire(status = "CLAIMED", earned = 0, kind = "OFFLINE_STREAK")),
                GiftClaimWireRequest(
                    GiftClaimKind.OFFLINE_STREAK, "owner", "daily-id-0000000001", 10L, 1000L, 11L, 0L,
                ),
            )
        }
        // Семь коробок невозможны за визитом или суточным бонусом — там строго одна.
        assertThrows(RuntimeException::class.java) { parse(wire(status = "CLAIMED", earned = 7, kind = "VISIT")) }
    }

    @Test fun everyNumberMustBeAnExactNonNegativeSafeInteger() {
        for (bad in listOf("-1", "1.5", "null", "9007199254740992")) {
            assertThrows(RuntimeException::class.java) { parse(wire().replace("\"boxCount\":2", "\"boxCount\":$bad")) }
        }
        assertThrows(RuntimeException::class.java) { parse(wire().replace("\"nextBonusBoxAtMs\":25", "")) }
        assertThrows(RuntimeException::class.java) { parse(wire().replace("\"premiumUntilMs\":26", "\"premiumUntilMs\":\"26\"")) }
    }

    @Test fun envelopeShapeIsStrict() {
        assertThrows(RuntimeException::class.java) { parseMap(jsonToMap("""{"type":"addNolics"}""")) }
        assertThrows(RuntimeException::class.java) {
            parseMap(jsonToMap(wire().replace("\"result\"", "\"data\"")))
        }
    }

    @Test fun storedBodyIsReadBackIntoTheSameRequest() {
        val offline =
            giftClaimRequestFrom(
                """{"kind":"OFFLINE_STREAK","ownerUid":"owner","operationId":"offline-id-000000001",
                   "base":{"boxStreakDays":9,"nextBoxAtMs":1000},
                   "claimed":{"boxStreakDays":10,"boxesEarned":1}}""",
            )
        assertEquals(GiftClaimKind.OFFLINE_STREAK, offline.kind)
        assertEquals(9L, offline.baseBoxStreakDays)
        assertEquals(1L, offline.claimedBoxesEarned)
        assertEquals(
            mapOf(
                "kind" to "OFFLINE_STREAK",
                "ownerUid" to "owner",
                "operationId" to "offline-id-000000001",
                "base" to mapOf("boxStreakDays" to 9L, "nextBoxAtMs" to 1000L),
                "claimed" to mapOf("boxStreakDays" to 10L, "boxesEarned" to 1L),
            ),
            offline.toWireMap(),
        )

        val visit = giftClaimRequestFrom("""{"kind":"VISIT","ownerUid":"owner","operationId":"visit-id-0000000001"}""")
        assertEquals(mapOf("kind" to "VISIT", "ownerUid" to "owner", "operationId" to "visit-id-0000000001"), visit.toWireMap())
    }

    @Test fun malformedStoredBodiesAreRefusedAsATypedPreCallInvalid() {
        // Направление запинено типом (retro e7 F5b): отказ разбора ТЕЛА — REQUEST_INVALID ДО
        // вызова: запрос не покидал устройство, решения сервера не существует — карантин.
        for (body in listOf(
            "not-json",
            """{"kind":"VISIT","ownerUid":"owner"}""",
            """{"kind":"WAT","ownerUid":"owner","operationId":"visit-id-0000000001"}""",
            """{"kind":"VISIT","ownerUid":"owner","operationId":"short"}""",
            """{"kind":"VISIT","ownerUid":"owner","operationId":"visit-id-0000000001","base":{"boxStreakDays":1}}""",
            // Неполное офлайн-тело отказывает здесь, по имени, — не позже голым
            // IllegalStateException из toWireMap.
            """{"kind":"OFFLINE_STREAK","ownerUid":"owner","operationId":"offline-id-000000001"}""",
        )) {
            val error = assertThrows(GiftClaimException::class.java) { giftClaimRequestFrom(body) }
            assertEquals(GiftClaimFailureReason.REQUEST_INVALID, error.reason)
        }
    }

    @Test fun unreadableAnswersAreEnvelopeInvalidWhichMustKeepTheIntent() {
        // Вторая сторона направления: ОТВЕТ не разобрался — решение сервера могло быть принято,
        // отказ обязан быть ENVELOPE_INVALID (держит намерение), а не REQUEST_INVALID (карантин).
        val error =
            assertThrows(GiftClaimException::class.java) {
                parseMap(jsonToMap(wire().replace("\"result\"", "\"data\"")))
            }
        assertEquals(GiftClaimFailureReason.ENVELOPE_INVALID, error.reason)
    }

    @Test fun fenceRefusalIsNamedNotSwallowedIntoTransport() {
        // Ровно то значение, которое production передаёт: FirebaseFunctionsException.Code
        // .ALREADY_EXISTS.name (SDK представляет серверный "already-exists" gRPC-именем).
        val failure = giftClaimFailureFrom("ALREADY_EXISTS", IllegalStateException("boom"))
        assertEquals(GiftClaimFailureReason.CONFLICTING_REPLAY, (failure as GiftClaimException).reason)
        val transport = giftClaimFailureFrom("unavailable", IllegalStateException("boom"))
        assertTrue(transport !is GiftClaimException)
    }

    @Test fun fractionalSdkDoublesAreRefusedNotTruncated() {
        // SDK отдаёт нецелые JSON-числа Double'ом; усечение 1.5 → 1 выглядело бы как принятие
        // неточного слова сервера.
        val fractional =
            mapOf(
                "replayed" to false,
                "result" to
                    mapOf(
                        "schemaVersion" to 1L, "ownerUid" to "owner",
                        "operationId" to "daily-id-0000000001", "kind" to "PREMIUM_DAILY",
                        "status" to "CLAIMED", "boxCount" to 2.5, "boxStreakDays" to 10L,
                        "nextBoxAtMs" to 3L, "nextBonusBoxAtMs" to 25L, "earnedBoxes" to 1L,
                        "premiumUntilMs" to 26L,
                    ),
            )
        assertThrows(RuntimeException::class.java) { parseMap(fractional) }
    }

    private fun jsonToMap(json: String): Map<*, *> = JsonFacade.parse(json)
}

/** Мост из JSON-строки в Map без Firebase SDK: тест живёт на чистой JVM. */
private object JsonFacade {
    fun parse(json: String): Map<*, *> {
        val element = kotlinx.serialization.json.Json.parseToJsonElement(json.trim())
        return element.toMap()
    }

    private fun kotlinx.serialization.json.JsonElement.toMap(): Map<*, *> =
        when (this) {
            is kotlinx.serialization.json.JsonObject -> mapValues { (_, v) -> v.toAny() }
            else -> error("Not an object")
        }

    private fun kotlinx.serialization.json.JsonElement.toAny(): Any? =
        when (this) {
            is kotlinx.serialization.json.JsonObject -> toMap()
            is kotlinx.serialization.json.JsonArray -> map { it.toAny() }
            is kotlinx.serialization.json.JsonPrimitive ->
                when {
                    isString -> content
                    booleanOrNull != null -> booleanOrNull
                    else -> longOrNull ?: error("Not a wire number: $content")
                }
            null -> null
        }
}
