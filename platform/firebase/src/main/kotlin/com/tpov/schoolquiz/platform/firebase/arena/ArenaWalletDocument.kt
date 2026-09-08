package com.tpov.schoolquiz.platform.firebase.arena

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp

/** SDK metadata is mandatory; an offline/default-source snapshot cannot masquerade as a fresh read. */
internal data class ArenaWalletDocument(
    val path: String,
    val fields: Map<String, Any?>?,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean,
)

internal fun parseArenaWallet(
    document: ArenaWalletDocument,
    stamp: ArenaSessionStamp,
    observedAtMs: Long,
): ArenaWalletObservation {
    if (document.path != "users/${stamp.ownerUid}" || document.isFromCache || document.hasPendingWrites) malformed()
    val fields = document.fields ?: throw ArenaWalletFailure(ArenaWalletFailureKind.MISSING_PROFILE)
    // users documents are currently unversioned. Never silently accept an explicit future layout.
    if (fields.containsKey("schemaVersion") && walletInteger(fields["schemaVersion"]) != 1L) malformed()
    for (identity in listOf("uid", "userId", "id")) {
        if (fields.containsKey(identity) && fields[identity] != stamp.ownerUid) malformed()
    }
    val nolics = if (fields.containsKey("pointsNolics")) fields["pointsNolics"] else fields["nolics"]
    return ArenaWalletObservation(
        stamp = stamp,
        observedAtMs = walletInteger(observedAtMs),
        availableNolics = walletInteger(nolics),
        reservedNolics = legacyHold(fields, "reservedNolics"),
        storedLifePoints = walletInteger(fields["lifePoints"]),
        reservedLifePoints = legacyHold(fields, "reservedLifePoints"),
        lifePointsUpdatedAtMs = walletInteger(fields["lifePointsUpdatedAtMs"]),
    )
}

/** holdings(user) in arena-tickets.js defines missing legacy holds as zero; explicit corrupt null is rejected. */
private fun legacyHold(
    fields: Map<String, Any?>,
    key: String,
): Long = if (fields.containsKey(key)) walletInteger(fields[key]) else 0L

private fun walletInteger(value: Any?): Long {
    val result =
        when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> {
                if (!value.isFinite() || value < 0 || value > ArenaWalletObservation.MAX_SAFE_INTEGER.toDouble()) {
                    malformed()
                }
                val integral = value.toLong()
                if (value != integral.toDouble()) malformed()
                integral
            }
            else -> malformed()
        }
    if (result !in 0..ArenaWalletObservation.MAX_SAFE_INTEGER) malformed()
    return result
}

private fun malformed(): Nothing = throw ArenaWalletFailure(ArenaWalletFailureKind.NEEDS_REFRESH)
