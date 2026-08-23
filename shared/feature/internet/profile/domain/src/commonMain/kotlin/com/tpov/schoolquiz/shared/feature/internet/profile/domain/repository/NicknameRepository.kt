package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname

/**
 * Names an account holds, and the shop where they change hands.
 *
 * Every call goes to the server. The registry of names is not readable by clients — it decides who
 * owns what, so letting a client read or write it would put the answer in the wrong hands.
 */
interface NicknameRepository {
    /** While typing. Never reports who holds a taken name. */
    suspend fun checkAvailability(nickname: String): NicknameAvailability

    suspend fun owned(): List<OwnedNickname>

    /** Wears a name already owned. Costs nothing. */
    suspend fun setActive(nickname: String)

    /** Takes an unclaimed name. Free the first time, gold after that; returns what was charged. */
    suspend fun claim(nickname: String): Long

    suspend fun listings(limit: Int = DEFAULT_LISTING_LIMIT): List<NicknameListing>

    /** The name being worn cannot be listed — selling it would leave the seller nameless. */
    suspend fun listForSale(nickname: String, price: Long)

    suspend fun cancelListing(nickname: String)

    /** Pays the price, receives the name. Returns the commission withheld. */
    suspend fun buy(nickname: String): Long

    companion object {
        const val DEFAULT_LISTING_LIMIT = 50
    }
}
