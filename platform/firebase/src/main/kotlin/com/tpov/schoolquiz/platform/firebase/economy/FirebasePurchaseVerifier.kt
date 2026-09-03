package com.tpov.schoolquiz.platform.firebase.economy

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusalCode
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusedException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseVerification
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.PurchaseVerifier
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Presents a purchase token to `verifyPurchase` and reads the answer.
 *
 * A monetary call, so it is direct and synchronous rather than a deferred mutation (ADM-5): the
 * player is waiting on the screen, and a purchase that quietly settles later out of sight is how a
 * paying user ends up not knowing whether they were charged.
 *
 * The connectivity check before the call is not an optimisation — without it an offline settlement
 * holds the spinner for the whole callable timeout instead of saying immediately that a connection
 * is needed. It is not a substitute for handling [SyncError.NoNetwork] afterwards, since the
 * connection can drop between the two.
 */
class FirebasePurchaseVerifier(
    private val functions: FirebaseFunctions,
    private val networkMonitor: NetworkMonitor,
) : PurchaseVerifier {
    override suspend fun verify(
        purchaseToken: String,
        productId: StoreProductId,
    ): PurchaseVerification {
        if (!networkMonitor.isOnline()) throw SyncFailure(SyncError.NoNetwork)
        val data =
            try {
                functions
                    .getHttpsCallable(VERIFY_PURCHASE)
                    .withAppTimeout()
                    .call(verifyPurchaseRequest(purchaseToken, productId))
                    .await()
                    .data as? Map<*, *>
            } catch (e: FirebaseFunctionsException) {
                // A refusal and a failed call are different things: the first is an answer with a
                // reason, the second leaves the purchase unsettled and worth retrying.
                throw purchaseRefusalFrom(e.details, e.message) ?: SyncFailure(e.toSyncError(), e)
            } catch (e: IOException) {
                // Never reached the server, so nothing was decided. Safe to present again.
                throw SyncFailure(SyncError.NoNetwork, e)
            }
        return purchaseVerificationFrom(data, productId)
    }

    private companion object {
        const val VERIFY_PURCHASE = "verifyPurchase"
    }
}

/**
 * The request the callable is asked with.
 *
 * A free function over plain values because these two key names are a contract with the server —
 * `purchase-settlement.js` destructures exactly `purchaseToken` and `sku` — and a contract nothing
 * executes is a contract that can drift. Swapping the two values compiles, passes every other test,
 * and refuses every real purchase.
 */
internal fun verifyPurchaseRequest(
    purchaseToken: String,
    productId: StoreProductId,
): Map<String, Any> =
    mapOf(
        FIELD_PURCHASE_TOKEN to purchaseToken,
        FIELD_SKU to productId.playSku,
    )

/**
 * Reads the callable's answer.
 *
 * Kept as a free function over plain values, deliberately: the Firebase SDK types do not initialise
 * in a plain JVM test (see the header of `network/CallableErrors.kt`), and the reading of a monetary
 * answer is exactly the part that must be covered by tests.
 *
 * An unreadable or unknown status is a [SyncFailure], never a silent success: the one thing that
 * must never happen is consuming a purchase because the client could not tell what the server said.
 */
internal fun purchaseVerificationFrom(
    data: Map<*, *>?,
    productId: StoreProductId,
): PurchaseVerification {
    // One store purchase token can name more than one product, and a replayed settlement answers
    // with the product that settled first. Crediting the pack the player asked about because the
    // answer merely arrived would show them an amount they did not buy.
    val answeredSku = data?.get(FIELD_SKU) as? String
    if (answeredSku != null && answeredSku != productId.playSku) {
        throw unreadable("verifyPurchase answered about $answeredSku, not ${productId.playSku}")
    }

    return when (val status = data?.get(FIELD_STATUS) as? String) {
        STATUS_CREDITED ->
            PurchaseVerification.Credited(
                productId = productId,
                // Refusing to guess here for the same reason the unknown status refuses: an amount
                // the client cannot read would be reported as zero gold received, which is
                // indistinguishable from a real zero and is a lie either way. Retrying is safe —
                // the server replays the same answer for the same token.
                goldGranted =
                    data.longFieldOrNull(FIELD_GOLD_GRANTED)
                        ?: throw unreadable("verifyPurchase credited an unreadable amount"),
                settlementId = data[FIELD_SETTLEMENT_ID] as? String ?: "",
            )

        STATUS_PENDING -> PurchaseVerification.Pending(productId)

        else -> throw unreadable("verifyPurchase returned status=$status")
    }
}

private fun unreadable(message: String): SyncFailure = SyncFailure(SyncError.Unknown(IllegalStateException(message)))

/**
 * Turns a callable failure into a refusal, when the server named a reason.
 *
 * Returns `null` when it did not — an `UNAVAILABLE` or a timeout is not the server refusing, and
 * mistaking one for the other would abandon a purchase that only needed to be presented again.
 *
 * The English `message` travels with the exception for the log and never for a branch; the branch
 * is [PurchaseRefusalCode], which survives rewording and translation.
 */
internal fun purchaseRefusalFrom(
    details: Any?,
    message: String?,
): PurchaseRefusedException? {
    val reasonCode = (details as? Map<*, *>)?.get(FIELD_REASON_CODE) as? String ?: return null
    return PurchaseRefusedException(
        code = PurchaseRefusalCode.fromWire(reasonCode),
        message = message.orEmpty().ifBlank { reasonCode },
    )
}

/**
 * Numbers arrive as whatever type the SDK parsed the JSON into — `Integer`, `Long` or `Double`.
 *
 * Null for anything else, so the caller decides what an unreadable number is worth. For money it is
 * worth refusing.
 */
private fun Map<*, *>.longFieldOrNull(field: String): Long? =
    when (val value = this[field]) {
        is Long -> value
        is Int -> value.toLong()
        is Number -> value.toLong()
        else -> null
    }

private const val FIELD_PURCHASE_TOKEN = "purchaseToken"
private const val FIELD_SKU = "sku"
private const val FIELD_STATUS = "status"
private const val FIELD_GOLD_GRANTED = "goldGranted"
private const val FIELD_SETTLEMENT_ID = "settlementId"
private const val FIELD_REASON_CODE = "reasonCode"

private const val STATUS_CREDITED = "CREDITED"
private const val STATUS_PENDING = "PENDING"
