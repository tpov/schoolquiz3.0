package com.tpov.schoolquiz.platform.firebase.nickname

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import kotlinx.coroutines.tasks.await

/**
 * The logo shelf, over callable functions.
 *
 * Gold, the owned list and the avatar on the account all have to move together, which is why every
 * mutation here is a call and not a write: a client that could edit its own logo list could grant
 * itself the set for nothing.
 */
class FirebaseLogoRemoteDataSource(
    private val functions: FirebaseFunctions,
) : LogoRemoteDataSource {
    override suspend fun catalog(): List<ProfileLogo> =
        call(FETCH_CATALOG, emptyMap()).list(LOGOS).map { entry ->
            ProfileLogo(
                name = entry.string(NAME) ?: "",
                price = entry.number(PRICE) ?: 0L,
                owned = entry[OWNED] as? Boolean ?: false,
                imageUrl = entry.string(IMAGE_URL),
            )
        }

    override suspend fun buy(logo: String): Long = call(BUY, mapOf(LOGO to logo)).number(CHARGED) ?: 0L

    override suspend fun wear(logo: String): String = call(WEAR, mapOf(LOGO to logo)).string(AVATAR_URL) ?: ""

    override suspend fun listings(limit: Int): List<LogoListing> =
        call(FETCH_LISTINGS, mapOf(LIMIT to limit)).list(LISTINGS).map { entry ->
            LogoListing(
                logo = entry.string(LOGO) ?: "",
                price = entry.number(PRICE) ?: 0L,
                sellerNickname = entry.string(SELLER_NICKNAME) ?: "",
                listedAtMs = entry.number(LISTED_AT_MS) ?: 0L,
                imageUrl = entry.string(IMAGE_URL),
            )
        }

    override suspend fun listForSale(
        logo: String,
        price: Long,
    ) {
        call(LIST_FOR_SALE, mapOf(LOGO to logo, PRICE to price))
    }

    override suspend fun cancelListing(logo: String) {
        call(CANCEL_LISTING, mapOf(LOGO to logo))
    }

    override suspend fun buyListed(logo: String): Long = call(BUY_LISTED, mapOf(LOGO to logo)).number(COMMISSION) ?: 0L

    private suspend fun call(
        name: String,
        payload: Map<String, Any?>,
    ): Map<*, *> = functions.getHttpsCallable(name).call(payload).await().data as? Map<*, *> ?: emptyMap<String, Any?>()

    private fun Map<*, *>.string(field: String): String? = this[field]?.toString()?.takeIf { it.isNotBlank() }

    /** Callables hand numbers back as Int or Double depending on size; both mean the same thing. */
    private fun Map<*, *>.number(field: String): Long? = (this[field] as? Number)?.toLong()

    private fun Map<*, *>.list(field: String): List<Map<*, *>> =
        (this[field] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }

    private companion object {
        const val FETCH_CATALOG = "fetchLogoCatalog"
        const val BUY = "buyLogo"
        const val WEAR = "wearLogo"
        const val FETCH_LISTINGS = "fetchLogoListings"
        const val LIST_FOR_SALE = "listLogoForSale"
        const val CANCEL_LISTING = "cancelLogoListing"
        const val BUY_LISTED = "buyListedLogo"
        const val LOGOS = "logos"
        const val LISTINGS = "listings"
        const val LOGO = "logo"
        const val NAME = "name"
        const val PRICE = "price"
        const val OWNED = "owned"
        const val CHARGED = "charged"
        const val COMMISSION = "commission"
        const val AVATAR_URL = "avatarUrl"
        const val IMAGE_URL = "imageUrl"
        const val SELLER_NICKNAME = "sellerNickname"
        const val LISTED_AT_MS = "listedAtMs"
        const val LIMIT = "limit"
    }
}
