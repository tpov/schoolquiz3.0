package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusedException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseVerification
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.PurchaseVerifier
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.ServerBalanceRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Turns a paid-for purchase into gold on the account — the one settlement path in the app.
 *
 * Used twice with no difference in behaviour: right after the player pays, and by the settler for
 * every purchase Play re-delivers because an earlier attempt never finished. That is deliberate —
 * "pay, then the process dies, then get the gold" only works if the recovery path is the same code
 * as the happy path, not a second implementation that drifts.
 *
 * **The order is verify → consume → refresh and it is not negotiable.** Consuming first would hand
 * the token back to Play before the account was credited, and Play does not return a consumed
 * token — the player's money would be gone with nothing to show for it. So nothing is consumed
 * without a `CREDITED`, and after `CREDITED` the balance is *re-read* from the server rather than
 * computed by adding [PurchaseVerification.Credited.goldGranted] to a local number (SYNC-AD-25).
 *
 * **One token settles once at a time, and the guard lives here** rather than in either caller.
 * Both callers are live at the same moment on the ordinary happy path: Play's purchase listener
 * resolves the buy flow *and* refreshes the store queue the settler watches, so the same token
 * arrives at both entry points within milliseconds. A guard that sat in only one of them would let
 * that pair run two verifications and race two consumes against each other on every single
 * purchase. The second caller waits for the first's answer instead of starting its own — it is the
 * same token, so it is the same answer, and the buy flow must still be able to report what the
 * player is owed.
 */
class SettlePurchaseUseCase(
    private val billing: BillingRepository,
    private val verifier: PurchaseVerifier,
    private val balanceRefresher: ServerBalanceRefresher,
    /**
     * Where a swallowed failure goes. Defaults to nowhere so the domain owes nothing to a logging
     * framework; the composition root hands it a real sink.
     */
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) {

    /** Settlements running right now, by token. The value is what a second caller waits on. */
    private val inFlight = mutableMapOf<String, CompletableDeferred<Result<PurchaseOutcome>>>()
    private val guard = Mutex()

    /**
     * Settles [purchase].
     *
     * A refusal comes back as `success(`[PurchaseOutcome.Refused]`)`, not as a failure: the call
     * worked and the server gave a clear answer, so there is nothing to retry — only something to
     * explain, by code. A failure means the answer never arrived, and the purchase stays in Play's
     * queue for the settler to try again.
     */
    suspend operator fun invoke(purchase: BillingPurchase): Result<PurchaseOutcome> {
        val token = purchase.purchaseToken
        val own = CompletableDeferred<Result<PurchaseOutcome>>()
        val running = guard.withLock { inFlight.getOrPut(token) { own } }
        if (running !== own) return running.await()

        var result: Result<PurchaseOutcome>? = null
        try {
            result = settleCatching(purchase)
            return result
        } finally {
            // Not cancellable: a settlement cancelled mid-flight (an account change, a screen
            // closing) must still give the token back, or it would be refused for the rest of the
            // process and the purchase would never be settled again.
            withContext(NonCancellable) { guard.withLock { inFlight.remove(token) } }
            // And a waiter must never hang on an owner that was cancelled.
            own.complete(result ?: Result.failure(SettlementInterrupted()))
        }
    }

    private suspend fun settleCatching(purchase: BillingPurchase): Result<PurchaseOutcome> =
        try {
            Result.success(settle(purchase))
        } catch (e: CancellationException) {
            throw e
        } catch (e: PurchaseRefusedException) {
            // The server looked and said no. Nothing was consumed and nothing will be; the caller
            // gets the code so it can say why in the player's own language.
            Result.success(PurchaseOutcome.Refused(e.code))
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun settle(purchase: BillingPurchase): PurchaseOutcome =
        when (val verification = verifier.verify(purchase.purchaseToken, purchase.productId)) {
            is PurchaseVerification.Pending -> PurchaseOutcome.Pending

            is PurchaseVerification.Credited -> {
                consume(purchase)
                // Runs even when the consume failed: the money is on the account either way, and
                // the player should see it there.
                refreshBalance()
                PurchaseOutcome.Credited(verification.goldGranted)
            }
        }

    /**
     * Hands the token back to Play, and treats failing to do so as survivable.
     *
     * A consume that fails after a credit is not an error the player needs to hear about: Play
     * keeps re-delivering the purchase, and the server answers `CREDITED` again for the same token
     * without moving anything, until one of the attempts finally consumes it. That loop *is* the
     * durability of this feature, so turning a failed consume into a failed settlement — and
     * telling the player their purchase did not work — would break the thing it is protecting.
     *
     * Both shapes of failure are caught, not just the returned one. The store adapter re-reads its
     * queue inside its own success path, so a consume that actually worked can still throw on the
     * way back; letting that escape would report a failure for money that has already moved.
     */
    private suspend fun consume(purchase: BillingPurchase) {
        try {
            billing.consume(purchase.purchaseToken)
                .onFailure { log(CONSUME_FAILED, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(CONSUME_FAILED, e)
        }
    }

    /** Same reasoning as [consume]: the credit already happened, so a failed re-read is survivable. */
    private suspend fun refreshBalance() {
        try {
            balanceRefresher.refresh()
                .onFailure { log(REFRESH_FAILED, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(REFRESH_FAILED, e)
        }
    }

    private companion object {
        const val CONSUME_FAILED = "purchase credited but not consumed; Play will re-deliver it"
        const val REFRESH_FAILED = "purchase settled but the balance could not be refreshed"
    }
}

/**
 * The settlement that owned this token was cancelled before it had an answer.
 *
 * Reported to whoever was waiting on it as an ordinary failure, because that is what it is worth to
 * them: nothing was decided, and presenting the purchase again is the right next step.
 */
class SettlementInterrupted : Exception("The settlement of this purchase was interrupted")
