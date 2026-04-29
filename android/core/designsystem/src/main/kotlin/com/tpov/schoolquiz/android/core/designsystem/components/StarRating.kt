@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STAR_COUNT = 3
private const val STAR_TENTHS = 10
private const val EMPTY_STAR_ALPHA = 0.28f
private const val FULL_STAR_PROGRESS = 1f
private const val EMPTY_STAR_PROGRESS = 0f
private const val STAR_VERTEX_COUNT = 10
private const val STAR_START_DEGREES = -90f
private const val STAR_VERTEX_DEGREES = 36f
private const val STAR_OUTER_RADIUS_FACTOR = 0.46f
private const val STAR_INNER_RADIUS_FACTOR = 0.22f
private const val STAR_STROKE_WIDTH_FACTOR = 0.07f

/**
 * Pure logic: whether star at [index] is fully filled for [rating].
 * filled = rating covers at least 1 full unit beyond this star's start (threshold + 1.0).
 * Testable without Android runtime.
 */
internal fun starIsFilled(
    index: Int,
    rating: Float?,
): Boolean = rating != null && rating >= index.toFloat() + 1.0f

/**
 * Pure logic: whether star at [index] is partially filled for [rating].
 * partial = rating enters this star's range but does not complete it.
 */
internal fun starIsPartial(
    index: Int,
    rating: Float?,
): Boolean = rating != null && rating > index.toFloat() && !starIsFilled(index, rating)

/**
 * Pure logic: fraction of fill for a partial star (0f..1f exclusive).
 * Returns 0f when star is not partial.
 * Testable without Android runtime.
 */
internal fun starPartialFraction(
    index: Int,
    rating: Float?,
): Float {
    if (!starIsPartial(index, rating)) return 0f
    val filledTenths = ((rating!! - index.toFloat()) * STAR_TENTHS).roundToInt()
    return filledTenths.coerceIn(1, STAR_TENTHS - 1) / STAR_TENTHS.toFloat()
}

/**
 * Renders a custom star progress: a real star path with a clipped fill and a stable outline.
 *
 * AC#26: for rating=2.7 star2 shows 7 filled tenths, 3 faint outline tenths.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun StarProgressIcon(
    progress: Float,
    tint: Color,
    starSize: Dp,
) {
    Canvas(modifier = Modifier.size(starSize)) {
        val starPath = createStarPath()
        val stroke =
            Stroke(
                width = min(size.width, size.height) * STAR_STROKE_WIDTH_FACTOR,
                join = StrokeJoin.Round,
            )
        if (progress > EMPTY_STAR_PROGRESS) {
            clipRect(right = size.width * progress.coerceIn(EMPTY_STAR_PROGRESS, FULL_STAR_PROGRESS)) {
                drawPath(path = starPath, color = tint)
            }
        }
        if (progress < FULL_STAR_PROGRESS) {
            drawPath(
                path = starPath,
                color = tint.copy(alpha = EMPTY_STAR_ALPHA),
                style = stroke,
            )
        }
    }
}

private fun DrawScope.createStarPath(): Path {
    val center = Offset(size.width / 2f, size.height / 2f)
    val base = min(size.width, size.height)
    val outerRadius = base * STAR_OUTER_RADIUS_FACTOR
    val innerRadius = base * STAR_INNER_RADIUS_FACTOR
    return Path().apply {
        repeat(STAR_VERTEX_COUNT) { index ->
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            val angleRadians = (STAR_START_DEGREES + STAR_VERTEX_DEGREES * index) * PI.toFloat() / 180f
            val point =
                Offset(
                    x = center.x + cos(angleRadians) * radius,
                    y = center.y + sin(angleRadians) * radius,
                )
            if (index == 0) {
                moveTo(point.x, point.y)
            } else {
                lineTo(point.x, point.y)
            }
        }
        close()
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
            val progress =
                when {
                    starIsFilled(index, rating) -> FULL_STAR_PROGRESS
                    starIsPartial(index, rating) -> starPartialFraction(index, rating)
                    else -> EMPTY_STAR_PROGRESS
                }
            StarProgressIcon(
                progress = progress,
                tint = primary,
                starSize = size,
            )
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
