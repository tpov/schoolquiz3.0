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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirBg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassFill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType

/**
 * The runner drawn in NOIR.
 *
 * Everything the question and result screens paint goes through this file, which is why it is the
 * whole port: the screens ask for "a runner card" or "the mode accent" and never name a colour, so
 * changing the answers here moves all of them at once.
 *
 * Hard mode keeps its red. It is the one place in the app where the accent carries meaning rather
 * than taste — the player needs to see at a glance which mode they are answering in — so it stays
 * fixed instead of following the accent the rest of NOIR themes on.
 */
@Composable
internal fun runnerModeAccent(isHard: Boolean): Color = if (isHard) NoirDanger else LocalNoirAccent.current

@Composable
internal fun runnerNeutralBorderColor(): Color = NoirOutline

@Composable
internal fun runnerLightBorderColor(): Color = NoirHair

@Composable
internal fun runnerAnswerSurfaceColor(): Color = NoirS2

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
 * The page behind a question: NOIR's near-black, lifted by a single wash of the mode's colour.
 *
 * One glow rather than a gradient across the whole page. The question and its answers are what
 * should hold the eye, and a busy background competes with the very thing being read.
 */
@Composable
internal fun RunnerDesignBackground(
    isHard: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val glow = runnerCenterGlowColor(isHard = isHard, accentColor = accentColor)
    Box(
        modifier
            .fillMaxSize()
            .background(NoirBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(glow, Color.Transparent),
                    center = Offset.Unspecified,
                    radius = 1400f,
                ),
            ),
        content = content,
    )
}

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
