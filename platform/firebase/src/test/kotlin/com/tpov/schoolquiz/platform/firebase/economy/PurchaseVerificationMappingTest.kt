package com.tpov.schoolquiz.platform.firebase.economy

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusalCode
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseVerification
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reading the server's answer about money.
 *
 * Driven through plain values rather than the Firebase SDK on purpose — the SDK's static
 * initialisers do not run in a JVM unit test, and this is the reading that decides whether a
 * purchase gets consumed.
 */
class PurchaseVerificationMappingTest {

    @Test
    fun `a credited answer carries the granted amount and the settlement id`() {
        val verification =
            purchaseVerificationFrom(
                mapOf(
                    "status" to "CREDITED",
                    "sku" to "gold_pack_medium",
                    "goldGranted" to 60,
                    "gold" to 260,
                    "settlementId" to "s-1",
                ),
                StoreProductId.GOLD_PACK_MEDIUM,
            )

        assertEquals(
            PurchaseVerification.Credited(StoreProductId.GOLD_PACK_MEDIUM, 60L, "s-1"),
            verification,
        )
    }

    /** JSON numbers arrive as whatever the SDK parsed them into; all of them mean the same amount. */
    @Test
    fun `the granted amount is read whatever numeric type the SDK produced`() {
        listOf<Any>(60, 60L, 60.0).forEach { raw ->
            val verification =
                purchaseVerificationFrom(
                    mapOf("status" to "CREDITED", "goldGranted" to raw, "settlementId" to "s-1"),
                    StoreProductId.GOLD_PACK_SMALL,
                )
            assertEquals(
                "Failed for ${raw::class.simpleName}",
                60L,
                (verification as PurchaseVerification.Credited).goldGranted,
            )
        }
    }

    @Test
    fun `a pending answer names the product and grants nothing`() {
        val verification =
            purchaseVerificationFrom(
                mapOf("status" to "PENDING", "sku" to "gold_pack_small"),
                StoreProductId.GOLD_PACK_SMALL,
            )

        assertEquals(PurchaseVerification.Pending(StoreProductId.GOLD_PACK_SMALL), verification)
    }

    /**
     * The single most dangerous outcome would be reading an answer the client does not understand
     * as a success — that consumes a purchase the server may never have credited.
     */
    @Test
    fun `an unknown status is a failure, never a silent credit`() {
        listOf<Map<*, *>?>(
            mapOf("status" to "SOMETHING_NEW"),
            mapOf("sku" to "gold_pack_small"),
            emptyMap<String, Any>(),
            null,
        ).forEach { data ->
            val error =
                runCatching {
                    purchaseVerificationFrom(data, StoreProductId.GOLD_PACK_SMALL)
                }.exceptionOrNull()

            assertTrue("Expected SyncFailure for $data, got $error", error is SyncFailure)
            assertTrue((error as SyncFailure).error is SyncError.Unknown)
        }
    }

    @Test
    fun `a named reason code becomes a refusal carrying that code`() {
        val refusal = purchaseRefusalFrom(mapOf("reasonCode" to "SKU_NOT_SOLD"), "sku is not sold")

        assertEquals(PurchaseRefusalCode.SKU_NOT_SOLD, refusal?.code)
        assertEquals("sku is not sold", refusal?.message)
    }

    /** Every code the server can send has a branch on this side. */
    @Test
    fun `every reason code the server can send is one this build knows`() {
        // Read from the server, not from a copy pasted beside the enum. A list maintained by hand
        // can only fail when someone edits both halves inconsistently; it cannot fail because the
        // server renamed a code — which is the drift that matters, and which would silently turn a
        // named refusal into UNKNOWN. Same trick the economy constants use to stay in step.
        val source = File("../../functions/purchase-verification.js")
        assertTrue("server module not found at ${source.absolutePath}", source.exists())

        val block =
            Regex("""const REASON_CODES = Object\.freeze\(\[(.*?)]\)""", RegexOption.DOT_MATCHES_ALL)
                .find(source.readText())?.groupValues?.get(1)
                ?: error("REASON_CODES not found in purchase-verification.js")
        val constants = Regex("""REASON_([A-Z_]+)""").findAll(block).map { it.groupValues[1] }.toSet()

        assertTrue("only ${constants.size} codes parsed — the regex has drifted", constants.size >= 11)
        assertEquals(
            "the client and the server disagree about the set of refusal reasons",
            constants.sorted(),
            (PurchaseRefusalCode.entries - PurchaseRefusalCode.UNKNOWN).map { it.name }.sorted(),
        )
        constants.forEach {
            assertEquals("$it fell through to UNKNOWN", it, PurchaseRefusalCode.fromWire(it).name)
        }
    }

    /** A newer server refusing for a newer reason is still a refusal. */
    @Test
    fun `an unrecognised reason code is still a refusal`() {
        val refusal = purchaseRefusalFrom(mapOf("reasonCode" to "INVENTED_LATER"), "nope")

        assertEquals(PurchaseRefusalCode.UNKNOWN, refusal?.code)
    }

    /**
     * Without a reason code the server did not refuse — it failed to answer. Reading that as a
     * refusal would abandon a purchase that only needed presenting again.
     */
    @Test
    fun `a failure with no reason code is not a refusal`() {
        assertNull(purchaseRefusalFrom(null, "UNAVAILABLE"))
        assertNull(purchaseRefusalFrom("precondition-pending", "FAILED_PRECONDITION"))
        assertNull(purchaseRefusalFrom(mapOf("code" to "version-conflict"), "ABORTED"))
        assertNull(purchaseRefusalFrom(mapOf("reasonCode" to 7), "not a string"))
    }

    /** The code, not the sentence, is what a caller branches on — but the sentence must survive. */
    @Test
    fun `a blank message falls back to the code rather than being empty`() {
        val refusal = purchaseRefusalFrom(mapOf("reasonCode" to "TOKEN_UNKNOWN"), "  ")

        assertEquals("TOKEN_UNKNOWN", refusal?.message)
    }

    /**
     * The two key names are a contract with the server, which destructures exactly these. Swapping
     * the values compiles and passes everything else, and refuses every real purchase.
     */
    @Test
    fun `the request names the token and the sku the server destructures`() {
        val request = verifyPurchaseRequest("token-abc", StoreProductId.GOLD_PACK_LARGE)

        assertEquals(setOf("purchaseToken", "sku"), request.keys)
        assertEquals("token-abc", request["purchaseToken"])
        assertEquals("gold_pack_large", request["sku"])
    }

    /**
     * One store token can name several products, and a replayed settlement answers about whichever
     * settled first. Crediting the pack that was asked about would show an amount nobody bought.
     */
    @Test
    fun `an answer about a different product is refused, not credited`() {
        val error =
            runCatching {
                purchaseVerificationFrom(
                    mapOf("status" to "CREDITED", "sku" to "gold_pack_small", "goldGranted" to 10),
                    StoreProductId.GOLD_PACK_LARGE,
                )
            }.exceptionOrNull()

        assertTrue("Expected SyncFailure, got $error", error is SyncFailure)
        assertTrue((error as SyncFailure).error is SyncError.Unknown)
    }

    /**
     * Zero gold received is indistinguishable from a real zero and is a lie either way. Retrying is
     * safe: the server replays the same answer for the same token.
     */
    @Test
    fun `a credited answer whose amount cannot be read is a failure, not zero gold`() {
        listOf<Map<String, Any?>>(
            mapOf("status" to "CREDITED", "settlementId" to "s-1"),
            mapOf("status" to "CREDITED", "goldGranted" to "60", "settlementId" to "s-1"),
            mapOf("status" to "CREDITED", "goldGranted" to null, "settlementId" to "s-1"),
        ).forEach { data ->
            val error =
                runCatching {
                    purchaseVerificationFrom(data, StoreProductId.GOLD_PACK_SMALL)
                }.exceptionOrNull()

            assertTrue("Expected SyncFailure for $data, got $error", error is SyncFailure)
        }
    }
}
