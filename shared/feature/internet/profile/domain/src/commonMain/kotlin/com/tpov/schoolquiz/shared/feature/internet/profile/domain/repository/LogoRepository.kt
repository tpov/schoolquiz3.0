package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo

/**
 * The emblem shelf.
 *
 * Logos arrive two ways — a gift box hands one over, or gold buys it — and both end up in the same
 * list on the account, so the shop only needs to know which ones are already there.
 */
interface LogoRepository {
    suspend fun catalog(): List<ProfileLogo>

    /** Spends gold. Returns what was charged. */
    suspend fun buy(logo: String): Long
}
