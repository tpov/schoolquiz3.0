package com.tpov.schoolquiz.platform.firebase.economy

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.syncErrorForCode
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftClaimRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimStatus
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException

/**
 * Presents a stored gift-claim body to `submitGiftBoxOperation` and reads the typed answer.
 *
 * A gift claim is direct and synchronous like a purchase (ADM-5's family): the player may be
 * looking at the button, and the server holds a permanent receipt fence for every operationId, so
 * an uncertain call is retried verbatim rather than re-decided. The connectivity check before the
 * call is not an optimisation — without it an offline claim holds the caller for the whole
 * callable timeout instead of saying at once that a connection is needed.
 */
class FirebaseGiftClaimRemoteDataSource(
    private val functions: FirebaseFunctions,
    private val networkMonitor: NetworkMonitor,
) : GiftClaimRemoteDataSource {
    override suspend fun submitGiftBoxOperation(payloadJson: String): GiftClaimOutcome {
        if (!networkMonitor.isOnline()) throw SyncFailure(SyncError.NoNetwork)
        val request = giftClaimRequestFrom(payloadJson)
        val data =
            try {
                functions
                    .getHttpsCallable(CALLABLE)
                    .withAppTimeout()
                    .call(request.toWireMap())
                    .await()
                    .data as? Map<*, *>
            } catch (e: FirebaseFunctionsException) {
                throw giftClaimFailureFrom(e.code.name, e)
            } catch (e: IOException) {
                // Never reached the server, so nothing was decided. Safe to present again.
                throw SyncFailure(SyncError.NoNetwork, e)
            }
        return giftClaimOutcomeFrom(data, request)
    }

    private companion object {
        const val CALLABLE = "submitGiftBoxOperation"
    }
}

/**
 * The request the stored body names, kept as plain values because identity is the contract.
 *
 * The body was written by `GiftClaimRepositoryImpl` and never edited afterwards; re-reading the
 * identity from it — rather than from separate fields — means the wire form and the replayed form
 * cannot drift apart: what was checked here is exactly what was sent.
 */
internal data class GiftClaimWireRequest(
    val kind: GiftClaimKind,
    val ownerUid: String,
    val operationId: String,
    val baseBoxStreakDays: Long?,
    val baseNextBoxAtMs: Long?,
    val claimedBoxStreakDays: Long?,
    val claimedBoxesEarned: Long?,
)

/**
 * Reads the request identity and its OFFLINE_STREAK halves out of the stored body.
 *
 * A body this reader refuses would have been refused by the server too (`invalid-argument`), but
 * refusing here keeps the failed intent where it was written instead of burning a round trip.
 * Every refusal here is a PRE-CALL refusal ([GiftClaimFailureReason.REQUEST_INVALID]): the request
 * never leaves the device, so no server decision for this body can exist — the repository
 * quarantines the intent instead of retrying it forever. This is the opposite direction from the
 * response-side [GiftClaimFailureReason.ENVELOPE_INVALID], which must keep the intent.
 */
internal fun giftClaimRequestFrom(payloadJson: String): GiftClaimWireRequest {
    val body = Json.parseToJsonElement(payloadJson) as? JsonObject ?: throw requestInvalid("Missing gift claim body")
    val kind = kindFrom(body.text("kind"))
    val request =
        GiftClaimWireRequest(
            kind = kind,
            ownerUid = body.text("ownerUid"),
            operationId = body.text("operationId"),
            baseBoxStreakDays = body.objectOrNull("base")?.longOrNullAt("boxStreakDays"),
            baseNextBoxAtMs = body.objectOrNull("base")?.longOrNullAt("nextBoxAtMs"),
            claimedBoxStreakDays = body.objectOrNull("claimed")?.longOrNullAt("boxStreakDays"),
            claimedBoxesEarned = body.objectOrNull("claimed")?.longOrNullAt("boxesEarned"),
        )
    if (kind != GiftClaimKind.OFFLINE_STREAK && request.baseBoxStreakDays != null) {
        throw requestInvalid("Only OFFLINE_STREAK carries a base")
    }
    // Половины офлайн-тела обязательны: тело пишет только GiftClaimRepositoryImpl и всегда с
    // базой и заявкой; неполное — испорченная строка, и отказать ей нужно здесь, по имени, ДО
    // вызова: решения сервера для такого тела не существует, и карантин правильнее вечного
    // повтора, в который голый IllegalStateException превращал намерение раньше.
    if (kind == GiftClaimKind.OFFLINE_STREAK && (request.baseBoxStreakDays == null || request.claimedBoxesEarned == null)) {
        throw requestInvalid("OFFLINE_STREAK requires both its base and claimed halves")
    }
    if (!operationIdShape.matches(request.operationId)) throw requestInvalid("Gift claim operation id is off-contract")
    return request
}

/**
 * Reads the callable's answer: an envelope `{replayed, result}` with a typed status.
 *
 * Kept as a free function over plain values, deliberately: the Firebase SDK types do not
 * initialise in a plain JVM test (see the header of `network/CallableErrors.kt`), and the reading
 * of a gift answer is exactly the part that must be covered by tests. Statuses are outcomes, not
 * errors — each kind accepts only the statuses the server's planner can produce for it, and
 * `replayed:true` must map to the same typed outcome as a fresh answer.
 */
internal fun giftClaimOutcomeFrom(
    data: Map<*, *>?,
    request: GiftClaimWireRequest,
): GiftClaimOutcome {
    val replayed = (data?.get("replayed") as? Boolean) ?: throw unreadable("Missing replay marker")
    val result = data["result"] as? Map<*, *> ?: throw unreadable("Missing result")
    if (result.number("schemaVersion") != 1L) throw unreadable("Unknown result schema")
    if (result.text("ownerUid") != request.ownerUid) throw unreadable("Answer names another owner")
    if (result.text("operationId") != request.operationId) throw unreadable("Answer names another operation")
    if (result.text("kind") != request.kind.name) throw unreadable("Answer names another kind")
    val status =
        allowedStatuses.getValue(request.kind).firstOrNull { it.name == result.textOrNull("status") }
            ?: throw unreadable("Status ${result.textOrNull("status")} cannot follow a ${request.kind} claim")
    val earnedBoxes = result.number("earnedBoxes")
    // Офлайн-заявка честно насчитанных дней несёт по коробке за каждый день серии с десятого
    // (`boxAccrualVerdict`: allowedBoxes = min(дней серии сверх базы, прошедших суток)) — CLAIMED
    // с earnedBoxes > 1 для неё обычный ответ, а не нечитаемый конверт. Визит и суточный бонус
    // выдают строго одну.
    if (status == GiftClaimStatus.CLAIMED) {
        if (earnedBoxes < 1L) throw unreadable("earnedBoxes $earnedBoxes does not match status $status")
        if (request.kind != GiftClaimKind.OFFLINE_STREAK && earnedBoxes != 1L) {
            throw unreadable("earnedBoxes $earnedBoxes does not match status $status")
        }
    } else if (earnedBoxes != 0L) {
        throw unreadable("earnedBoxes $earnedBoxes does not match status $status")
    }
    return GiftClaimOutcome(
        kind = request.kind,
        status = status,
        replayed = replayed,
        boxCount = result.number("boxCount").toInt(),
        boxStreakDays = result.number("boxStreakDays").toInt(),
        nextBoxAtMs = result.number("nextBoxAtMs"),
        nextBonusBoxAtMs = result.number("nextBonusBoxAtMs"),
        earnedBoxes = earnedBoxes.toInt(),
        premiumUntilMs = result.number("premiumUntilMs"),
    )
}

/**
 * A refusal and a failed call are different things: the fence's `already-exists` means this
 * operationId is bound to a different body or owner — repeating it cannot help, and it must not
 * be mistaken for a transport hiccup that a verbatim retry would fix.
 */
internal fun giftClaimFailureFrom(
    code: String,
    cause: Exception,
): Exception =
    when (code) {
        // FirebaseFunctionsException.Code.ALREADY_EXISTS.name — ровно то значение, которым SDK
        // представляет серверный wire-код "already-exists" (fence: operationId скреплён с другим
        // телом или владельцем). Повтор такого отказа вслепую не помогает никогда.
        "ALREADY_EXISTS" -> GiftClaimException(GiftClaimFailureReason.CONFLICTING_REPLAY, cause = cause)
        else -> SyncFailure(syncErrorForCode(code, cause.message, null, causedByIo = false), cause)
    }

private fun unreadable(message: String): GiftClaimException =
    GiftClaimException(GiftClaimFailureReason.ENVELOPE_INVALID, cause = IllegalStateException(message))

/** Pre-call отказ разбора сохранённого ТЕЛА: вызова не было, решения не существует — карантин. */
private fun requestInvalid(message: String): GiftClaimException =
    GiftClaimException(GiftClaimFailureReason.REQUEST_INVALID, cause = IllegalStateException(message))

/** Статусы, которые планировщик сервера способен вернуть на каждый вид заявки. */
private val allowedStatuses: Map<GiftClaimKind, Set<GiftClaimStatus>> =
    mapOf(
        GiftClaimKind.VISIT to setOf(GiftClaimStatus.CLAIMED, GiftClaimStatus.NO_CHANGE),
        GiftClaimKind.OFFLINE_STREAK to
            setOf(GiftClaimStatus.CLAIMED, GiftClaimStatus.NO_CHANGE, GiftClaimStatus.STALE_BASE),
        GiftClaimKind.PREMIUM_DAILY to
            setOf(GiftClaimStatus.CLAIMED, GiftClaimStatus.DAILY_LIMIT, GiftClaimStatus.PREMIUM_REQUIRED),
    )

private val operationIdShape = Regex("[A-Za-z0-9_-]{16,128}")

private fun kindFrom(name: String): GiftClaimKind =
    GiftClaimKind.entries.firstOrNull { it.name == name } ?: throw requestInvalid("Unknown gift claim kind $name")

internal fun GiftClaimWireRequest.toWireMap(): Map<String, Any> {
    val wire =
        linkedMapOf<String, Any>(
            "kind" to kind.name,
            "ownerUid" to ownerUid,
            "operationId" to operationId,
        )
    if (kind == GiftClaimKind.OFFLINE_STREAK) {
        wire["base"] =
            mapOf(
                "boxStreakDays" to checkNotNull(baseBoxStreakDays),
                "nextBoxAtMs" to checkNotNull(baseNextBoxAtMs),
            )
        wire["claimed"] =
            mapOf(
                "boxStreakDays" to checkNotNull(claimedBoxStreakDays),
                "boxesEarned" to checkNotNull(claimedBoxesEarned),
            )
    }
    return wire
}

private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.text(key: String): String = textOrNull(key) ?: throw requestInvalid("Missing $key")

private fun JsonObject.textOrNull(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    if (!value.isString) throw requestInvalid("$key must be a string")
    return value.content.takeIf { it.isNotBlank() } ?: throw requestInvalid("Blank $key")
}

private fun JsonObject.longOrNullAt(key: String): Long? {
    val value = this[key] as? JsonPrimitive ?: return null
    if (value.isString) throw requestInvalid("$key must be a number")
    return value.longOrNull
}

// Читатели ответа: SDK разбирает вызываемый в Map, и только такие помощники к нему подходят.
private fun Map<*, *>.text(key: String): String = textOrNull(key) ?: throw unreadable("Missing $key")

private fun Map<*, *>.textOrNull(key: String): String? {
    val value = this[key] ?: return null
    if (value !is String || value.isBlank()) throw unreadable("$key must be a non-blank string")
    return value
}

/** Сервер отвечает точными неотрицательными безопасными числами; всё прочее — нечитаемый ответ. */
private fun Map<*, *>.number(key: String): Long {
    val value = this[key] ?: throw unreadable("Missing $key")
    val number =
        when (value) {
            is Long -> value
            is Int -> value.toLong()
            // SDK отдаёт нецелые JSON-числа Double'ом; молчаливое усечение 1.5 → 1 выглядело бы
            // как принятие неточного слова — нечитаемый ответ вместо этого.
            is Double ->
                if (value % 1.0 != 0.0) throw unreadable("$key is not an exact number")
                else value.toLong()
            is Number -> value.toLong()
            else -> throw unreadable("$key is not a number")
        }
    if (number < 0 || number > MAX_SAFE_INTEGER) throw unreadable("$key is out of range")
    return number
}

private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
