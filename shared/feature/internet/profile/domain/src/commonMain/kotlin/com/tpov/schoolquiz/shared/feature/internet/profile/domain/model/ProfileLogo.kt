package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * One of the eight emblems that can sit where an avatar would.
 *
 * The name is the identity, here and on the server, because that is what a gift box hands over —
 * there is no id behind it to key on. [imageUrl] points at the avatar image when the catalogue
 * knows it; null means the picture is unknown, and the UI falls back to the local glyph.
 */
data class ProfileLogo(
    val name: String,
    val price: Long,
    val owned: Boolean,
    val imageUrl: String? = null,
)
