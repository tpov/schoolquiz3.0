@file:Suppress("FunctionNaming", "MagicNumber", "UnusedParameter", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirAnswerPlate
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassFill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType

/**
 * The runner drawn in NOIR.
 *
 * Everything the question and result screens paint goes through this file, which is why it is the
 * whole port: the screens ask for "a runner card" or "the mode accent" and never name a colour, so
 * changing the answers here moves all of them at once.
 *
 * The mode has its own two colours — green for an easy round, red for a hard one — and they do not
 * follow the accent the rest of NOIR themes on. A player has to see which round they are in at a
 * glance, and an accent that changes with taste cannot say that. The accent still owns the things
 * that mean "you are here": the progress segments, the selected row.
 */
@Composable
internal fun runnerModeAccent(isHard: Boolean): Color = if (isHard) NoirDanger else NoirSuccess

@Composable
internal fun runnerNeutralBorderColor(): Color = NoirOutline

@Composable
internal fun runnerLightBorderColor(): Color = NoirHair

@Composable
internal fun runnerAnswerSurfaceColor(): Color = NoirAnswerPlate

@Composable
internal fun runnerDeepSurfaceColor(): Color = NoirS2

@Composable
internal fun runnerGroupSurfaceColor(): Color = NoirS1

@Composable
internal fun runnerCenterGlowColor(
    isHard: Boolean,
    accentColor: Color? = null,
): Color = (accentColor ?: runnerModeAccent(isHard)).copy(alpha = 0.07f)

/**
 * The page behind a question, built by the drawing's own arithmetic.
 *
 * A vertical gradient, not a glow: near-black at both edges with the mode's colour a whisper into
 * the middle. The three stops are derived rather than picked — surface #1E1E24 laid over black at
 * 34% gives the edge, at 76% gives the midpoint's base, and the accent sits over that base at 8%.
 * Writing the results as constants would hide that the mode colour is the only variable.
 */
@Composable
internal fun RunnerDesignBackground(
    isHard: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = accentColor ?: runnerModeAccent(isHard)
    val edge = NoirSurfaceBase.over(black = 0.34f)
    val midBase = NoirSurfaceBase.over(black = 0.76f)
    val middle = accent.blendOver(midBase, alpha = 0.08f)
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to edge,
                    RUNNER_GRADIENT_MIDPOINT to middle,
                    1f to edge,
                ),
            ),
        content = content,
    )
}

/** The surface the whole scale is derived from — NOIR's first surface, #1E1E24. */
private val NoirSurfaceBase = Color(0xFF1E1E24)

/** Where the mode colour peaks. Slightly past centre, as the drawing has it. */
private const val RUNNER_GRADIENT_MIDPOINT = 0.52f

/** This colour laid over black at [black] opacity. */
private fun Color.over(black: Float): Color =
    Color(red = red * black, green = green * black, blue = blue * black, alpha = 1f)

/** This colour laid over [base] at [alpha]. */
private fun Color.blendOver(
    base: Color,
    alpha: Float,
): Color =
    Color(
        red = red * alpha + base.red * (1f - alpha),
        green = green * alpha + base.green * (1f - alpha),
        blue = blue * alpha + base.blue * (1f - alpha),
        alpha = 1f,
    )

/** A panel in the runner: NOIR's glass, with the accent border reserved for the row being judged. */
@Composable
internal fun RunnerDesignCard(
    modifier: Modifier = Modifier,
    accentColor: Color = LocalNoirAccent.current,
    containerColor: Color = NoirGlassFill,
    borderColor: Color? = null,
    elevated: Boolean = false,
    useAccentBorder: Boolean = false,
    content: @Composable () -> Unit,
) {
    val stroke =
        borderColor ?: if (useAccentBorder) accentColor.copy(alpha = 0.45f) else NoirGlassStroke
    Box(
        modifier
            .clip(NoirShapeLg)
            .background(if (elevated) NoirS2 else containerColor)
            .border(1.dp, stroke, NoirShapeLg),
    ) {
        content()
    }
}

@Composable
internal fun RunnerDesignChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = NoirType.chip.copy(color = color),
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .clip(NoirShapePill)
                .background(color.copy(alpha = 0.10f))
                .border(1.dp, color.copy(alpha = 0.30f), NoirShapePill)
                .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
internal fun RunnerIconBadge(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        content = content,
    )
}
