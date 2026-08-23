package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * A name offered in the shop.
 *
 * The seller appears by the name they wear and never by uid: the listing is public to everyone
 * signed in, and a uid there would turn the shop into a directory of accounts.
 */
data class NicknameListing(
    val nickname: String,
    val price: Long,
    val sellerNickname: String,
    val listedAtMs: Long,
)
