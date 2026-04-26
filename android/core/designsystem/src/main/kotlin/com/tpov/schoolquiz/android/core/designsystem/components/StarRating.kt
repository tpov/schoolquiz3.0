package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

private const val STAR_COUNT = 3

/**
 * Pure logic: whether star at [index] is fully filled for [rating].
 * filled = rating covers at least 1 full unit beyond this star's start (threshold + 1.0).
 * Testable without Android runtime.
 */
internal fun starIsFilled(index: Int, rating: Float?): Boolean =
    rating != null && rating >= index.toFloat() + 1.0f

/**
 * Pure logic: whether star at [index] is partially filled for [rating].
 * partial = rating enters this star's range but does not complete it.
 */
internal fun starIsPartial(index: Int, rating: Float?): Boolean =
    rating != null && rating > index.toFloat() && !starIsFilled(index, rating)

/**
 * Pure logic: fraction of fill for a partial star (0f..1f exclusive).
 * Returns 0f when star is not partial.
 * Testable without Android runtime.
 */
internal fun starPartialFraction(index: Int, rating: Float?): Float {
    if (!starIsPartial(index, rating)) return 0f
    return rating!! - index.toFloat()
}

/**
 * Renders a partial star: [StarOutline] as background, [Star] clipped to [fraction] width.
 *
 * AC#26: for rating=2.7 star2 shows 70% filled, 30% outline.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun PartialStarIcon(fraction: Float, tint: Color, size: Dp) {
    Box(modifier = Modifier.size(size)) {
        Icon(
            imageVector = Icons.Outlined.StarOutline,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
        Box(modifier = Modifier.size(width = size * fraction, height = size).clipToBounds()) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.requiredSize(size),
            )
        }
    }
}

/**
 * Displays a 3-star rating row.
 *
 * rating null or 0f → all outline stars.
 * rating 2.7 → star0 full, star1 full, star2 partial 70%. (AC#26)
 * rating 3.0 → all 3 filled.
 *
 * Color: MaterialTheme.colorScheme.primary (GoogleBlue per ADR-0010).
 * BrandComponentsInvariantsTest: no hardcoded color literals allowed.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md FR#10
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun StarRating(
    rating: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier = modifier) {
        repeat(STAR_COUNT) { index ->
            when {
                starIsFilled(index, rating) -> Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(size),
                )
                starIsPartial(index, rating) -> PartialStarIcon(
                    fraction = starPartialFraction(index, rating),
                    tint = primary,
                    size = size,
                )
                else -> Icon(
                    imageVector = Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StarRating0Preview() {
    SchoolQuizTheme { StarRating(rating = 0f) }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StarRatingHalfPreview() {
    SchoolQuizTheme { StarRating(rating = 0.5f) }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StarRating15Preview() {
    SchoolQuizTheme { StarRating(rating = 1.5f) }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StarRating27Preview() {
    SchoolQuizTheme { StarRating(rating = 2.7f) }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StarRating30Preview() {
    SchoolQuizTheme { StarRating(rating = 3.0f) }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StarRating43Preview() {
    SchoolQuizTheme { StarRating(rating = 4.3f) }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StarRatingNullPreview() {
    SchoolQuizTheme { StarRating(rating = null) }
}
