package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import kotlinx.coroutines.flow.Flow

/**
 * Names an account holds, and the shop where they change hands.
 *
 * Every call goes to the server. The registry of names is not readable by clients — it decides who
 * owns what, so letting a client read or write it would put the answer in the wrong hands.
 *
 * One exception: taking a lot down does not wait for the server. It is queued and delivered on its
 * own, and the shop learns the outcome from the queue ([observeCancelSync]) and from the next
 * refresh of the window — never from a local edit of the row.
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
    suspend fun listForSale(
        nickname: String,
        price: Long,
    )

    /**
     * Queues the removal of the account's own lot. Returns as soon as the intent is stored.
     *
     * Nothing is sent here and nothing local changes: the lot leaves the window when the server
     * deletes it. Fails only when there is no signed-in account to own the intent.
     */
    suspend fun cancelListing(nickname: String)

    /** What the queue knows about the removal of [nickname]'s lot; null when nothing is queued. */
    fun observeCancelSync(nickname: String): Flow<ListingCancelSync?>

    /** Pays the price, receives the name. Returns the commission withheld. */
    suspend fun buy(nickname: String): Long

    companion object {
        const val DEFAULT_LISTING_LIMIT = 50
    }
}
