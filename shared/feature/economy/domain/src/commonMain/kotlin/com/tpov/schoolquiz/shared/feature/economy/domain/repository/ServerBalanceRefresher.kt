package com.tpov.schoolquiz.shared.feature.economy.domain.repository

/**
 * Re-reads the account's balance from the server.
 *
 * Exists so that settling a purchase never has to do arithmetic. The server has already moved the
 * money; adding `goldGranted` to a local number would produce a second, unauthorised source of a
 * monetary value, and the two would disagree the first time a receipt was replayed or a refund
 * landed (SYNC-AD-25). Pulling the balance instead means the device shows what the server holds.
 *
 * A port rather than a direct call into the profile feature: the balance lives outside economy, and
 * the composition root is where the two meet.
 */
interface ServerBalanceRefresher {

    /** Pulls the current balance. A failure leaves the old number on screen, nothing worse. */
    suspend fun refresh(): Result<Unit>
}
