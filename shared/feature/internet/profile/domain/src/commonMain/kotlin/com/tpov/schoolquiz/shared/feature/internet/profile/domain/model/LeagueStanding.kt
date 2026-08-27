package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * Where this account sits among everybody, by experience.
 *
 * Two numbers and the share they make, because a place on its own says nothing: 42nd is excellent
 * among ten thousand and last among forty-two.
 */
data class LeagueStanding(
    val place: Int,
    val total: Int,
    /** Rounded up, so first of a hundred reads as the top 1% rather than the top 0%. */
    val topPercent: Int,
)
