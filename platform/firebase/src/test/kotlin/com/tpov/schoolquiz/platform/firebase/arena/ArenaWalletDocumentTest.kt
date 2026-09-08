package com.tpov.schoolquiz.platform.firebase.arena

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArenaWalletDocumentTest {
    private val stamp = ArenaSessionStamp("A", 7)

    private fun parse(fields: Map<String, Any?>? = walletFields()) =
        parseArenaWallet(ArenaWalletDocument("users/A", fields, false, false), stamp, 1000)

    @Test fun readsAvailableAndReservedIndependentlyWithoutSubtractingTwice() {
        val result = parse()
        assertEquals(stamp, result.stamp)
        assertEquals(1000L, result.observedAtMs)
        assertEquals(70L, result.availableNolics)
        assertEquals(30L, result.reservedNolics)
        assertEquals(400L, result.storedLifePoints)
        assertEquals(100L, result.reservedLifePoints)
        assertEquals(123L, result.lifePointsUpdatedAtMs)
    }

    @Test fun repeatedServerObservationsReplaceTheViewWithoutApplyingLocalArithmetic() {
        val first = parse(walletFields() + mapOf("pointsNolics" to 100L, "reservedNolics" to 0L))
        val held = parse()
        val released = parse(walletFields() + mapOf("pointsNolics" to 100L, "reservedNolics" to 0L))
        assertEquals(100L, first.availableNolics)
        assertEquals(70L, held.availableNolics)
        assertEquals(30L, held.reservedNolics)
        assertEquals(100L, released.availableNolics)
        assertEquals(0L, released.reservedNolics)
    }

    @Test fun missingLegacyHoldsAreTheOnlyPermittedDefaultZeroes() {
        val result = parse(walletFields() - setOf("reservedNolics", "reservedLifePoints"))
        assertEquals(0L, result.reservedNolics)
        assertEquals(0L, result.reservedLifePoints)
        for (key in listOf("reservedNolics", "reservedLifePoints")) malformed(walletFields() + (key to null))
    }

    @Test fun absentProfileAndMissingBalancesAreNotZero() {
        assertEquals(
            ArenaWalletFailureKind.MISSING_PROFILE,
            (
                runCatching {
                    parse(null)
                }.exceptionOrNull() as ArenaWalletFailure
            ).kind,
        )
        for (key in listOf("pointsNolics", "lifePoints", "lifePointsUpdatedAtMs")) malformed(walletFields() - key)
        malformed(emptyMap())
        val zero = parse(walletFields().mapValues { 0L })
        assertEquals(0L, zero.availableNolics)
        assertEquals(0L, zero.storedLifePoints)
    }

    @Test fun authoritativeNolicsWinsOverLegacyAliasWithoutTreatingNullAsMissing() {
        assertEquals(70L, parse(walletFields() + ("nolics" to 999L)).availableNolics)
        assertEquals(41L, parse((walletFields() - "pointsNolics") + ("nolics" to 41L)).availableNolics)
        malformed(walletFields() + mapOf("pointsNolics" to null, "nolics" to 41L))
    }

    @Test fun corruptNumbersNeverTruncateClampCoerceOrDefault() {
        val invalid =
            listOf(
                null, "1", true, -1L, 0.5, Double.NaN, Double.POSITIVE_INFINITY,
                Long.MAX_VALUE, 9_007_199_254_740_992.0,
            )
        for (field in walletFields().keys) for (value in invalid) malformed(walletFields() + (field to value))
        assertEquals(
            ArenaWalletObservation.MAX_SAFE_INTEGER,
            parse(walletFields() + ("pointsNolics" to ArenaWalletObservation.MAX_SAFE_INTEGER)).availableNolics,
        )
        assertEquals(70L, parse(walletFields() + ("pointsNolics" to 70.0)).availableNolics)
    }

    @Test fun foreignIdentityAndFutureVersionAreNotAccepted() {
        for (key in listOf("id", "uid", "userId")) malformed(walletFields() + (key to "B"))
        malformed(walletFields() + ("schemaVersion" to 2L))
        malformed(walletFields() + ("schemaVersion" to null))
        assertEquals(70L, parse(walletFields() + ("schemaVersion" to 1L)).availableNolics)
    }

    @Test fun cachePendingWritesAndForeignDocumentPathAreRejected() {
        for (document in listOf(
            ArenaWalletDocument("users/B", walletFields(), false, false),
            ArenaWalletDocument("profiles/A", walletFields(), false, false),
            ArenaWalletDocument("users/A", walletFields(), true, false),
            ArenaWalletDocument("users/A", walletFields(), false, true),
        )) {
            val failure =
                runCatching {
                    parseArenaWallet(
                        document,
                        stamp,
                        1000,
                    )
                }.exceptionOrNull() as ArenaWalletFailure
            assertEquals(ArenaWalletFailureKind.NEEDS_REFRESH, failure.kind)
        }
    }

    @Test fun freshnessRejectsAccountSwitchEpochSwitchClockRollbackAndOldObservation() {
        val result = parse()
        assertTrue(result.isUsableFor(stamp, 1100, 100))
        assertFalse(result.isUsableFor(stamp, 1101, 100))
        assertFalse(result.isUsableFor(stamp, 999, 100))
        assertFalse(result.isUsableFor(stamp, 1000, -1))
        assertFalse(result.isUsableFor(stamp.copy(generation = 8), 1000, 100))
        assertFalse(result.isUsableFor(stamp.copy(ownerUid = "B"), 1000, 100))
        assertFalse(result.isUsableFor(null, 1000, 100))
    }

    private fun malformed(fields: Map<String, Any?>) {
        val failure = runCatching { parse(fields) }.exceptionOrNull()
        assertTrue("Expected closed malformed outcome", failure is ArenaWalletFailure)
        assertEquals(ArenaWalletFailureKind.NEEDS_REFRESH, (failure as ArenaWalletFailure).kind)
        assertNull(failure.cause)
    }
}

internal fun walletFields(): Map<String, Any?> =
    mapOf(
        "pointsNolics" to 70L,
        "reservedNolics" to 30L,
        "lifePoints" to 400L,
        "reservedLifePoints" to 100L,
        "lifePointsUpdatedAtMs" to 123L,
    )
