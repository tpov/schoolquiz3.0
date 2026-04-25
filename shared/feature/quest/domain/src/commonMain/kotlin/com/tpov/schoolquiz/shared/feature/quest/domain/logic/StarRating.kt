package com.tpov.schoolquiz.shared.feature.quest.domain.logic

/**
 * Pure domain model for a single star fill state.
 *
 * A star can be empty (no fill), fully filled, or fractionally filled
 * (0..fractionsPerStar exclusive). Fraction represents the number of
 * "tenths" filled out of [fractionsPerStar].
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   FR#6 — "3 звезды, делятся на 10 частей каждая"
 *   Delegated Decision #16 — "10 fractional positions per star"
 *   Domain Test Scenarios 35-40.
 */
sealed interface StarFill {
    /** Star is completely empty (no fill). */
    data object Empty : StarFill

    /** Star is completely filled. */
    data object Full : StarFill

    /**
     * Star is partially filled.
     *
     * @param tenths Number of tenths filled (1..9 inclusive).
     *   0 maps to [Empty], 10 maps to [Full].
     */
    data class Partial(val tenths: Int) : StarFill {
        init {
            require(tenths in 1..9) { "StarFill.Partial.tenths must be in 1..9, got $tenths" }
        }
    }
}

/**
 * Result of computing star fills for a given [averageRating].
 *
 * @param fills List of [StarFill] values, one per star (size == [stars]).
 * @param hasNoRatings True when [averageRating] was null (no ratings yet).
 */
data class StarRatingModel(
    val fills: List<StarFill>,
    val hasNoRatings: Boolean,
)

/**
 * Computes the per-star fill states from [averageRating].
 *
 * The rating scale is 0.0..([stars].toFloat()) with [fractionsPerStar]
 * discrete positions per star (default: 3 stars × 10 fractions = 30 positions).
 *
 * Algorithm:
 *   totalFractions = round(averageRating * fractionsPerStar)
 *   for each star i in 0..<stars:
 *     starFractions = min(totalFractions - i * fractionsPerStar, fractionsPerStar)
 *     if starFractions <= 0 → Empty
 *     if starFractions >= fractionsPerStar → Full
 *     else → Partial(starFractions)
 *
 * @param averageRating Rating in [0.0..[stars].toFloat()] or null for unrated.
 * @param stars Number of stars (default: 3).
 * @param fractionsPerStar Number of discrete fill positions per star (default: 10).
 * @return [StarRatingModel] with [stars]-length fill list.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — Domain Test Scenarios 35-40.
 */
fun computeStarFills(
    averageRating: Float?,
    stars: Int = 3,
    fractionsPerStar: Int = 10,
): StarRatingModel {
    if (averageRating == null) {
        return StarRatingModel(
            fills = List(stars) { StarFill.Empty },
            hasNoRatings = true,
        )
    }

    // Clamp to valid range and convert to integer tenths
    val clamped = averageRating.coerceIn(0.0f, stars.toFloat())
    val totalFractions = (clamped * fractionsPerStar).toInt()

    val fills = List(stars) { starIndex ->
        val starFractions = (totalFractions - starIndex * fractionsPerStar)
            .coerceIn(0, fractionsPerStar)
        when {
            starFractions <= 0 -> StarFill.Empty
            starFractions >= fractionsPerStar -> StarFill.Full
            else -> StarFill.Partial(starFractions)
        }
    }

    return StarRatingModel(fills = fills, hasNoRatings = false)
}
