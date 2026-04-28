@file:Suppress("FunctionNaming", "MagicNumber", "UnusedParameter", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.glowEasy
import com.tpov.schoolquiz.android.core.designsystem.glowHard

private const val RUNNER_BACKGROUND_ACCENT_ALPHA = 0.08f
private const val RUNNER_BACKGROUND_SURFACE_ALPHA = 0.76f
private const val RUNNER_DEEP_SURFACE_ALPHA = 0.72f
private const val RUNNER_NEUTRAL_BORDER_ALPHA = 0.5f
private const val RUNNER_LIGHT_BORDER_ALPHA = 0.3f
private const val RUNNER_CHIP_CONTAINER_ALPHA = 0.12f

@Composable
internal fun runnerModeAccent(isHard: Boolean): Color =
    if (isHard) {
        MaterialTheme.colorScheme.glowHard
    } else {
        MaterialTheme.colorScheme.glowEasy
    }

@Composable
internal fun runnerNeutralBorderColor(): Color =
    MaterialTheme.colorScheme.outline.copy(alpha = RUNNER_NEUTRAL_BORDER_ALPHA)

@Composable
internal fun runnerLightBorderColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = RUNNER_LIGHT_BORDER_ALPHA)

@Composable
internal fun runnerAnswerSurfaceColor(): Color = runnerDeepSurfaceColor()

@Composable
internal fun runnerDeepSurfaceColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = RUNNER_DEEP_SURFACE_ALPHA).compositeOver(Color.Black)

@Composable
internal fun runnerGroupSurfaceColor(): Color = MaterialTheme.colorScheme.surface

@Composable
internal fun runnerCenterGlowColor(
    isHard: Boolean,
    accentColor: Color? = null,
): Color {
    val accent = accentColor ?: runnerModeAccent(isHard)
    val baseSurface =
        MaterialTheme.colorScheme.surface
            .copy(alpha = RUNNER_BACKGROUND_SURFACE_ALPHA)
            .compositeOver(Color.Black)
    return accent.copy(alpha = RUNNER_BACKGROUND_ACCENT_ALPHA).compositeOver(baseSurface)
}

@Composable
internal fun RunnerLegacyBackground(
    isHard: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val centerGlow = runnerCenterGlowColor(isHard = isHard, accentColor = accentColor)
    Box(
        modifier =
            modifier
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black,
                        0.52f to centerGlow,
                        1f to Color.Black,
                    ),
                ),
        content = content,
    )
}

@Composable
internal fun RunnerLegacyCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color? = null,
    elevated: Boolean = false,
    useAccentBorder: Boolean = false,
    content: @Composable () -> Unit,
) {
    val resolvedBorderColor = borderColor ?: if (useAccentBorder) accentColor else runnerNeutralBorderColor()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, resolvedBorderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (elevated) 2.dp else 0.dp,
        content = content,
    )
}

@Composable
internal fun RunnerLegacyChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = RUNNER_CHIP_CONTAINER_ALPHA),
        contentColor = color,
        border = BorderStroke(1.dp, runnerNeutralBorderColor()),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
    }
}

@Composable
internal fun RunnerIconBadge(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = RUNNER_CHIP_CONTAINER_ALPHA),
        contentColor = color,
        border = BorderStroke(1.dp, runnerNeutralBorderColor()),
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}
