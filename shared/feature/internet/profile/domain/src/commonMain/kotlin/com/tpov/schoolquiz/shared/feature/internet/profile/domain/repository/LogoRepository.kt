package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo

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
    suspend fun listForSale(logo: String, price: Long)

    suspend fun cancelListing(logo: String)

    /** Buys a listed avatar. Returns the commission the trade paid. */
    suspend fun buyListed(logo: String): Long
}
