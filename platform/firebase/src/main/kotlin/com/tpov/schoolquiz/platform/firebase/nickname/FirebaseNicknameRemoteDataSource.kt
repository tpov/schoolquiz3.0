package com.tpov.schoolquiz.platform.firebase.nickname

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.NicknameRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameRejection
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameTier
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import kotlinx.coroutines.tasks.await

/**
 * Names and the shop that trades them, over callable functions.
 *
 * Nothing here touches Firestore directly. The registry that decides who owns which name is closed
 * to clients by the security rules, and a sale has to move title, gold and the listing together —
 * three writes a client could only do one at a time, leaving a half-finished trade behind.
 */
class FirebaseNicknameRemoteDataSource(
    private val functions: FirebaseFunctions,
) : NicknameRemoteDataSource {
    override suspend fun checkAvailability(nickname: String): NicknameAvailability {
        val data = call(CHECK_AVAILABILITY, mapOf(NICKNAME to nickname))
        return NicknameAvailability(
            nickname = data.string(NICKNAME) ?: "",
            available = data[AVAILABLE] as? Boolean ?: false,
            reason = NicknameRejection.fromCode(data.string(REASON)),
            price = data.number(PRICE) ?: 0L,
            tier = NicknameTier.fromCode(data.string(TIER)),
            holder = data.string(HOLDER),
        )
    }

    override suspend fun owned(): List<OwnedNickname> =
        call(FETCH_OWNED, emptyMap()).list(NICKNAMES).map { entry ->
            OwnedNickname(
                nickname = entry.string(NICKNAME) ?: "",
                active = entry[ACTIVE] as? Boolean ?: false,
                generated = entry[GENERATED] as? Boolean ?: false,
                listedPrice = entry.number(LISTED_PRICE),
            )
        }

    override suspend fun setActive(nickname: String) {
        call(SET_ACTIVE, mapOf(NICKNAME to nickname))
    }

    override suspend fun claim(nickname: String): Long = call(CLAIM, mapOf(NICKNAME to nickname)).number(CHARGED) ?: 0L

    override suspend fun listings(limit: Int): List<NicknameListing> =
        call(FETCH_LISTINGS, mapOf(LIMIT to limit)).list(LISTINGS).map { entry ->
            NicknameListing(
                nickname = entry.string(NICKNAME) ?: "",
                price = entry.number(PRICE) ?: 0L,
                sellerNickname = entry.string(SELLER_NICKNAME) ?: "",
                listedAtMs = entry.number(LISTED_AT_MS) ?: 0L,
            )
        }

    override suspend fun listForSale(
        nickname: String,
        price: Long,
    ) {
        call(LIST_FOR_SALE, mapOf(NICKNAME to nickname, PRICE to price))
    }

    override suspend fun cancelListing(nickname: String) {
        call(CANCEL_LISTING, mapOf(NICKNAME to nickname))
    }

    override suspend fun buy(nickname: String): Long = call(BUY, mapOf(NICKNAME to nickname)).number(COMMISSION) ?: 0L

    private suspend fun call(
        name: String,
        payload: Map<String, Any>,
    ): Map<*, *> = functions.getHttpsCallable(name).call(payload).await().data as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun Map<*, *>.string(field: String): String? = this[field]?.toString()?.takeIf { it.isNotBlank() }

    /** Callables hand numbers back as Int or Double depending on size; both mean the same thing. */
    private fun Map<*, *>.number(field: String): Long? = (this[field] as? Number)?.toLong()

    private fun Map<*, *>.list(field: String): List<Map<*, *>> =
        (this[field] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }

    private companion object {
        const val CHECK_AVAILABILITY = "checkNicknameAvailability"
        const val FETCH_OWNED = "fetchOwnedNicknames"
        const val SET_ACTIVE = "setActiveNickname"
        const val CLAIM = "claimNickname"
        const val FETCH_LISTINGS = "fetchNicknameListings"
        const val LIST_FOR_SALE = "listNicknameForSale"
        const val CANCEL_LISTING = "cancelNicknameListing"
        const val BUY = "buyListedNickname"

        const val NICKNAME = "nickname"
        const val NICKNAMES = "nicknames"
        const val LISTINGS = "listings"
        const val AVAILABLE = "available"
        const val REASON = "reason"
        const val ACTIVE = "active"
        const val GENERATED = "generated"
        const val LISTED_PRICE = "listedPrice"
        const val LISTED_AT_MS = "listedAtMs"
        const val SELLER_NICKNAME = "sellerNickname"
        const val PRICE = "price"
        const val TIER = "tier"
        const val HOLDER = "holder"
        const val LIMIT = "limit"
        const val CHARGED = "charged"
        const val COMMISSION = "commission"
    }
}
