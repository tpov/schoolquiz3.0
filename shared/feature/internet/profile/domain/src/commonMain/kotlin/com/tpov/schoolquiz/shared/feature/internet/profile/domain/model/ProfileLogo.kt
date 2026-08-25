package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * One of the eight emblems that can sit where an avatar would.
 *
 * The name is the identity, here and on the server, because that is what a gift box hands over —
 * there is no id behind it to key on.
 */
data class ProfileLogo(
    val name: String,
    val price: Long,
    val owned: Boolean,
)
