package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp

/**
 * One server-only read, not a reservation or a receipt. Nolics and life already exclude holds.
 * Life is the STORED balance at its server regeneration cursor, not a current regeneration projection.
 * observedAtMs is the client's reception clock, never a server timestamp. No persistent wallet cache.
 */
data class ArenaWalletObservation(
    val stamp: ArenaSessionStamp,
    val observedAtMs: Long,
    val availableNolics: Long,
    val reservedNolics: Long,
    val storedLifePoints: Long,
    val reservedLifePoints: Long,
    val lifePointsUpdatedAtMs: Long,
) {
    init {
        require(stamp.ownerUid.isNotBlank() && stamp.generation >= 0) { "Invalid wallet owner stamp" }
        require(
            listOf(
                observedAtMs,
                availableNolics,
                reservedNolics,
                storedLifePoints,
                reservedLifePoints,
                lifePointsUpdatedAtMs,
            ).all { it in 0..MAX_SAFE_INTEGER },
        ) { "Wallet values must be nonnegative safe integers" }
    }

    /** The caller supplies its freshness policy. A session switch or clock rollback invalidates this view. */
    fun isUsableFor(
        current: ArenaSessionStamp?,
        nowMs: Long,
        maxAgeMs: Long,
    ): Boolean =
        current == stamp && maxAgeMs >= 0 && nowMs >= observedAtMs &&
            nowMs - observedAtMs <= maxAgeMs

    companion object {
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    }
}

/** Missing, stale/malformed, offline and unauthenticated are not confirmed financial zeroes. */
enum class ArenaWalletFailureKind {
    ACCOUNT_UNAVAILABLE,
    ACCOUNT_CHANGED,
    INVALID_REQUEST,
    MISSING_PROFILE,
    NEEDS_REFRESH,
    NO_NETWORK,
    ACCESS_DENIED,
    UNAVAILABLE,
}

/** No raw document, SDK message or private cause crosses the domain boundary. */
class ArenaWalletFailure(val kind: ArenaWalletFailureKind) : Exception(kind.name)
