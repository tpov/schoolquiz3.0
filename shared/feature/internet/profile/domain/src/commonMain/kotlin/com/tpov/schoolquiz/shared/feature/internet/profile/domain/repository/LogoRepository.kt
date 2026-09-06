package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import kotlinx.coroutines.flow.Flow

/**
 * The emblem shelf.
 *
 * Logos arrive two ways — a gift box hands one over, or gold buys it — and both end up in the same
 * list on the account, so the shop only needs to know which ones are already there.
 */
interface LogoRepository {
    suspend fun catalog(): List<ProfileLogo>

    /** Spends gold on the fixed-price purchase. Returns what was charged. */
    suspend fun buy(logo: String): Long

    /** Puts the avatar on the account. Returns the image URL the server assigned to it. */
    suspend fun wear(logo: String): String

    suspend fun listings(limit: Int): List<LogoListing>

    /** Opens a seller listing at [price] gold. */
    suspend fun listForSale(
        logo: String,
        price: Long,
    )

    /**
     * Queues the removal of the account's own lot. Returns as soon as the intent is stored.
     *
     * Nothing is sent here and nothing local changes: the lot leaves the window when the server
     * deletes it. Fails only when there is no signed-in account to own the intent.
     */
    suspend fun cancelListing(logo: String)

    /** What the queue knows about the removal of [logo]'s lot; null when nothing is queued. */
    fun observeCancelSync(logo: String): Flow<ListingCancelSync?>

    /** Buys a listed avatar. Returns the commission the trade paid. */
    suspend fun buyListed(logo: String): Long
}
