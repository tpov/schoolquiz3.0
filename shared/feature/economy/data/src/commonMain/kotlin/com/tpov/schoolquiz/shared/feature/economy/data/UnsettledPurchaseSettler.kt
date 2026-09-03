package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.SettlePurchaseUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Finishes purchases that were paid for and never credited (Story 1.2).
 *
 * The queue is Play's, not ours: a process killed between paying and crediting leaves the purchase
 * unconsumed, and Play re-delivers it on the next connection. This watches that queue for the whole
 * life of the process and puts everything in it through the same [SettlePurchaseUseCase] the buy
 * flow uses, so recovery is not a second implementation that can drift from the happy path. That
 * use case also owns the one-settlement-per-token guard, which is why this class keeps no such set
 * of its own — the buy flow would have bypassed it.
 *
 * Three things wake it: the process starting, the account changing, and the network coming back.
 * All three are the same event as far as the code is concerned — "there may be an account with an
 * unsettled purchase and a way to reach the server" — so they are one flow rather than three
 * triggers.
 *
 * Both scopes are `collectLatest`, not `flatMapLatest`, and that is load-bearing: it cancels the
 * settlements *already running* for the previous account, not merely the subscription that found
 * them. Cancelling only the subscription would leave a settlement started under one account to
 * finish under the next one, refreshing the wrong player's balance.
 */
class UnsettledPurchaseSettler(
    private val currentUidFlow: () -> Flow<String?>,
    private val networkMonitor: NetworkMonitor,
    private val billing: BillingRepository,
    private val settlePurchase: SettlePurchaseUseCase,
    private val scope: CoroutineScope,
    private val retryDelayMs: Long = INITIAL_RETRY_DELAY_MS,
    private val maxRetryDelayMs: Long = MAX_RETRY_DELAY_MS,
    /**
     * How many times in a row a round may fail before this account stops trying on a timer.
     *
     * Without a bound a token the server keeps refusing to settle — or a server that keeps
     * answering "try later" — becomes a call generator that never gives up. The next account
     * change, reconnection or queue change starts it over, which is the right moment: something
     * about the situation actually changed.
     */
    private val maxRetryRounds: Int = MAX_RETRY_ROUNDS,
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) {

    /**
     * Tokens the server has refused, per account.
     *
     * A refusal is an answer, not a hiccup: presenting the same token again gets the same refusal
     * for as long as the install lives. Without this the settler would re-present a permanently
     * refused purchase on every start, every account change and every connectivity flap — a paid
     * callable and a Play Developer API read each time, with no cap and nothing that ever gives up.
     * Kept per account because the commonest refusal, `TOKEN_OWNED_BY_ANOTHER_ACCOUNT`, is about
     * *who* is asking: the same token is refused for this player and perfectly settleable for the
     * one who paid.
     *
     * In memory only. The cost of forgetting is one wasted call after a restart; the cost of
     * persisting it would be a second store to keep in step with Play's.
     */
    private val refused = mutableSetOf<Pair<String, String>>()
    private val refusedGuard = Mutex()

    /** Runs the settler for the life of the process. */
    fun start(): Job =
        scope.launch {
            currentUidFlow()
                .distinctUntilChanged()
                .collectLatest { uid ->
                    if (uid.isNullOrBlank()) return@collectLatest
                    networkMonitor.observeOnline()
                        .distinctUntilChanged()
                        .collectLatest { online ->
                            if (online) settleWhileOnline(uid)
                        }
                }
        }

    /**
     * Settles for one account while it is online, and keeps its own clock.
     *
     * The store's queue is a conflating flow of a list: re-reading it when nothing changed produces
     * an equal list and therefore no emission at all. So the queue cannot be the retry clock — a
     * settlement that failed on a stable, online device would otherwise never be attempted again
     * until the process restarted. This loop is that clock: it backs off after a failure and,
     * when there is nothing to retry, waits for the queue to actually change before looking again.
     */
    private suspend fun settleWhileOnline(uid: String) = coroutineScope {
        val queue = billing.observeUnsettledPurchases()
        var backoffMs = 0L
        var failedRounds = 0

        while (isActive) {
            if (backoffMs > 0L) delay(backoffMs)

            // Ask the store again before reading its answer: an account change or a reconnection is
            // exactly when a purchase nobody has seen yet becomes visible. A refresh that could not
            // run leaves a stale — usually empty — queue, so it is worth another go rather than
            // reading past it.
            val refreshed = refreshQueue()
            val snapshot = queue.first()
            val settleable = if (refreshed) snapshot.filterNot { isRefused(uid, it.purchaseToken) } else emptyList()
            val worthRetrying = !refreshed || settleAll(uid, settleable)

            // Something arrived while we were working: go again now rather than sleeping through it.
            if (queue.first() != snapshot) {
                backoffMs = 0L
                failedRounds = 0
                continue
            }

            if (worthRetrying && ++failedRounds < maxRetryRounds) {
                backoffMs = nextBackoff(backoffMs)
                continue
            }

            // Either everything settled, or this account has failed often enough that trying again
            // on a timer is just noise. Both mean the same thing: stop asking until the store's
            // queue actually changes. A bound is what keeps a permanently failing token from
            // becoming a call generator — the next account change or reconnection starts it over.
            backoffMs = 0L
            failedRounds = 0
            queue.drop(1).first()
        }
    }

    /** @return true when the store answered; false when it could not be asked. */
    private suspend fun refreshQueue(): Boolean =
        try {
            billing.refreshUnsettledPurchases()
                .onFailure { log(REFRESH_FAILED, it) }
                .isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The store adapter reaches a separate process that can be replaced under it — which is
            // exactly when unsettled purchases appear. Letting this escape would end settlement for
            // the life of the process from a coroutine nobody is watching.
            log(REFRESH_FAILED, e)
            false
        }

    /** @return true when at least one settlement failed in a way worth retrying. */
    private suspend fun settleAll(
        uid: String,
        purchases: List<BillingPurchase>,
    ): Boolean =
        coroutineScope {
            purchases
                .map { purchase -> async { settleOne(uid, purchase) } }
                .awaitAll()
                .any { it }
        }

    private suspend fun settleOne(
        uid: String,
        purchase: BillingPurchase,
    ): Boolean {
        val result = settlePurchase(purchase)
        val outcome = result.getOrNull()
        if (outcome is PurchaseOutcome.Refused) {
            // Final. Remembering it is what stops the loop; whether a refused token may also be
            // handed back to the store is a product decision and belongs to a later story.
            rememberRefusal(uid, purchase.purchaseToken)
            return false
        }
        return result.isFailure
    }

    private suspend fun isRefused(
        uid: String,
        token: String,
    ): Boolean = refusedGuard.withLock { (uid to token) in refused }

    private suspend fun rememberRefusal(
        uid: String,
        token: String,
    ) {
        refusedGuard.withLock { refused.add(uid to token) }
    }

    private fun nextBackoff(current: Long): Long =
        if (current <= 0L) retryDelayMs else (current * 2).coerceAtMost(maxRetryDelayMs)

    private companion object {
        const val INITIAL_RETRY_DELAY_MS = 30_000L
        const val MAX_RETRY_DELAY_MS = 15 * 60_000L
        const val MAX_RETRY_ROUNDS = 3
        const val REFRESH_FAILED = "the store's purchase queue could not be re-read"
    }
}
