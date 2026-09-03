package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusedException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseVerification
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId

/**
 * Asks the server whether a purchase token is real and what it was worth.
 *
 * The device cannot answer this itself, and that is the whole point: a token the client alone has
 * seen proves nothing, so gold is granted by the server against the Play Developer API and the
 * client only learns the result. Nothing in the app may credit a balance from what comes back here.
 *
 * The same token may be presented any number of times — a process killed between paying and
 * crediting is expected, not exceptional — and the server answers identically every time.
 */
interface PurchaseVerifier {

    /**
     * Presents [purchaseToken] for [productId] and returns the server's answer.
     *
     * @throws PurchaseRefusedException when the server looked at the purchase and said no. Carries
     *   the reason as a code; there is nothing to retry.
     * @throws com.tpov.schoolquiz.shared.core.network.SyncFailure when the server could not be
     *   reached or did not answer. The purchase stays unsettled and is presented again later.
     */
    suspend fun verify(
        purchaseToken: String,
        productId: StoreProductId,
    ): PurchaseVerification
}
