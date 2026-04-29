@file:Suppress("FunctionNaming", "MagicNumber", "UnusedParameter", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignBackground
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignCard
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignChip
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignIconBadge
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignCenterGlowColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignDeepSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignGroupSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignModeAccent
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignNeutralBorderColor

@Composable
internal fun runnerModeAccent(isHard: Boolean): Color = schoolQuizDesignModeAccent(isHard)

@Composable
internal fun runnerNeutralBorderColor(): Color = schoolQuizDesignNeutralBorderColor()

@Composable
internal fun runnerLightBorderColor(): Color = schoolQuizDesignLightBorderColor()

@Composable
internal fun runnerAnswerSurfaceColor(): Color = runnerDeepSurfaceColor()

@Composable
internal fun runnerDeepSurfaceColor(): Color = schoolQuizDesignDeepSurfaceColor()

@Composable
internal fun runnerGroupSurfaceColor(): Color = schoolQuizDesignGroupSurfaceColor()

@Composable
internal fun runnerCenterGlowColor(
    isHard: Boolean,
    accentColor: Color? = null,
): Color = schoolQuizDesignCenterGlowColor(isHard = isHard, accentColor = accentColor)

@Composable
internal fun RunnerLegacyBackground(
    isHard: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    SchoolQuizDesignBackground(
        isHard = isHard,
        modifier = modifier,
        accentColor = accentColor,
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
    SchoolQuizDesignCard(
        modifier = modifier,
        accentColor = accentColor,
        containerColor = containerColor,
        borderColor = borderColor,
        elevated = elevated,
        useAccentBorder = useAccentBorder,
        content = content,
    )
}

@Composable
internal fun RunnerLegacyChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    SchoolQuizDesignChip(text = text, color = color, modifier = modifier)
}

@Composable
internal fun RunnerIconBadge(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    SchoolQuizDesignIconBadge(color = color, modifier = modifier, content = content)
}
