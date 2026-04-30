@file:Suppress("FunctionNaming", "MagicNumber", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.currentSchoolQuizDesignStyle
import com.tpov.schoolquiz.android.core.designsystem.glowEasy
import com.tpov.schoolquiz.android.core.designsystem.glowHard

enum class SchoolQuizDesignStyle {
    Main,
}

@Immutable
data class SchoolQuizDesignTokens(
    val backgroundAccentAlpha: Float,
    val backgroundSurfaceAlpha: Float,
    val deepSurfaceAlpha: Float,
    val neutralBorderAlpha: Float,
    val lightBorderAlpha: Float,
    val chipContainerAlpha: Float,
)

val MainDesignTokens =
    SchoolQuizDesignTokens(
        backgroundAccentAlpha = 0.08f,
        backgroundSurfaceAlpha = 0.76f,
        deepSurfaceAlpha = 0.72f,
        neutralBorderAlpha = 0.5f,
        lightBorderAlpha = 0.3f,
        chipContainerAlpha = 0.12f,
    )

fun SchoolQuizDesignStyle.tokens(): SchoolQuizDesignTokens =
    when (this) {
        SchoolQuizDesignStyle.Main -> MainDesignTokens
    }

@Composable
fun schoolQuizDesignModeAccent(
    isHard: Boolean,
    style: SchoolQuizDesignStyle? = null,
): Color {
    val resolvedStyle = style ?: currentSchoolQuizDesignStyle()
    resolvedStyle.tokens()
    return if (isHard) {
        MaterialTheme.colorScheme.glowHard
    } else {
        MaterialTheme.colorScheme.glowEasy
    }
}

@Composable
fun schoolQuizDesignNeutralBorderColor(style: SchoolQuizDesignStyle? = null): Color =
    MaterialTheme.colorScheme.outline.copy(
        alpha = (style ?: currentSchoolQuizDesignStyle()).tokens().neutralBorderAlpha,
    )

@Composable
fun schoolQuizDesignLightBorderColor(style: SchoolQuizDesignStyle? = null): Color =
    MaterialTheme.colorScheme.onSurface.copy(
        alpha = (style ?: currentSchoolQuizDesignStyle()).tokens().lightBorderAlpha,
    )

@Composable
fun schoolQuizDesignDeepSurfaceColor(style: SchoolQuizDesignStyle? = null): Color =
    MaterialTheme.colorScheme.surface
        .copy(alpha = (style ?: currentSchoolQuizDesignStyle()).tokens().deepSurfaceAlpha)
        .compositeOver(Color.Black)

@Composable
fun schoolQuizDesignGroupSurfaceColor(style: SchoolQuizDesignStyle? = null): Color {
    (style ?: currentSchoolQuizDesignStyle()).tokens()
    return MaterialTheme.colorScheme.surface
}

@Composable
fun schoolQuizDesignCenterGlowColor(
    isHard: Boolean,
    accentColor: Color? = null,
    style: SchoolQuizDesignStyle? = null,
): Color {
    val resolvedStyle = style ?: currentSchoolQuizDesignStyle()
    val tokens = resolvedStyle.tokens()
    val accent = accentColor ?: schoolQuizDesignModeAccent(isHard = isHard, style = resolvedStyle)
    val baseSurface =
        MaterialTheme.colorScheme.surface
            .copy(alpha = tokens.backgroundSurfaceAlpha)
            .compositeOver(Color.Black)
    return accent.copy(alpha = tokens.backgroundAccentAlpha).compositeOver(baseSurface)
}

@Composable
fun SchoolQuizDesignBackground(
    isHard: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    style: SchoolQuizDesignStyle? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val edgeSurface =
        MaterialTheme.colorScheme.surface
            .copy(alpha = 0.34f)
            .compositeOver(Color.Black)
    val centerGlow =
        schoolQuizDesignCenterGlowColor(
            isHard = isHard,
            accentColor = accentColor,
            style = style,
        )
    Box(
        modifier =
            modifier
                .background(
                    Brush.verticalGradient(
                        0f to edgeSurface,
                        0.52f to centerGlow,
                        1f to edgeSurface,
                    ),
                ),
        content = content,
    )
}

@Composable
@Suppress("LongParameterList")
fun SchoolQuizDesignCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color? = null,
    elevated: Boolean = false,
    useAccentBorder: Boolean = false,
    style: SchoolQuizDesignStyle? = null,
    content: @Composable () -> Unit,
) {
    val resolvedStyle = style ?: currentSchoolQuizDesignStyle()
    val resolvedBorderColor =
        borderColor
            ?: if (useAccentBorder) {
                accentColor
            } else {
                schoolQuizDesignNeutralBorderColor(resolvedStyle)
            }
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
fun SchoolQuizDesignChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: SchoolQuizDesignStyle? = null,
) {
    val resolvedStyle = style ?: currentSchoolQuizDesignStyle()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = resolvedStyle.tokens().chipContainerAlpha),
        contentColor = color,
        border = BorderStroke(1.dp, schoolQuizDesignNeutralBorderColor(resolvedStyle)),
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
fun SchoolQuizDesignIconBadge(
    color: Color,
    modifier: Modifier = Modifier,
    style: SchoolQuizDesignStyle? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedStyle = style ?: currentSchoolQuizDesignStyle()
    Surface(
        modifier = modifier.size(48.dp),
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = resolvedStyle.tokens().chipContainerAlpha),
        contentColor = color,
        border = BorderStroke(1.dp, schoolQuizDesignNeutralBorderColor(resolvedStyle)),
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
fun SchoolQuizDesignAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor =
            if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            },
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                    } else {
                        schoolQuizDesignNeutralBorderColor()
                    },
            ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = text,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Unspecified,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Unspecified,
                )
            }
        }
    }
}

@Composable
fun SchoolQuizDesignMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    SchoolQuizDesignCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = accent,
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SchoolQuizDesignScoreProgress(
    progress: Float,
    userMarkerProgress: Float,
    leaderMarkerProgress: Float?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(44.dp),
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val userProgress = userMarkerProgress.coerceIn(0f, 1f)
        val leaderProgress = leaderMarkerProgress?.coerceIn(0f, 1f)
        val trackWidth = maxWidth
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(schoolQuizDesignNeutralBorderColor(), MaterialTheme.shapes.small),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(clampedProgress)
                    .height(12.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        )
        ScoreProgressMarkerSlot(
            progress = userProgress,
            color = MaterialTheme.colorScheme.primary,
            trackWidth = trackWidth,
        )
        if (leaderProgress != null) {
            ScoreProgressMarkerSlot(
                progress = leaderProgress,
                color = MaterialTheme.colorScheme.secondary,
                trackWidth = trackWidth,
            )
        }
    }
}

@Composable
private fun BoxScope.ScoreProgressMarkerSlot(
    progress: Float,
    color: Color,
    trackWidth: Dp,
) {
    val markerSize = 26.dp
    val lineWidth = 3.dp
    val markerMaxOffset = (trackWidth - markerSize).coerceAtLeast(0.dp)
    val lineMaxOffset = (trackWidth - lineWidth).coerceAtLeast(0.dp)
    val markerOffset = (trackWidth * progress - markerSize / 2f).coerceIn(0.dp, markerMaxOffset)
    val lineOffset = (trackWidth * progress - lineWidth / 2f).coerceIn(0.dp, lineMaxOffset)

    Box(
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = lineOffset)
                .width(lineWidth)
                .height(28.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(lineWidth)
                    .height(28.dp)
                    .background(color, MaterialTheme.shapes.extraSmall),
        )
    }
    SchoolQuizDesignProgressMarker(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .offset(x = markerOffset),
        color = color,
        markerSize = markerSize,
    )
}

@Composable
private fun SchoolQuizDesignProgressMarker(
    color: Color,
    markerSize: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(markerSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = color,
        border = BorderStroke(1.dp, color),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = color,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
@Suppress("UnusedPrivateMember")
private fun SchoolQuizDesignPreview() {
    SchoolQuizTheme {
        SchoolQuizDesignBackground(
            isHard = false,
            modifier = Modifier.height(280.dp).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SchoolQuizDesignCard(
                    containerColor = schoolQuizDesignDeepSurfaceColor(),
                    borderColor = schoolQuizDesignLightBorderColor(),
                ) {
                    Text(
                        text = "Карточка вопроса",
                        modifier = Modifier.padding(16.dp),
                    )
                }
                SchoolQuizDesignScoreProgress(
                    progress = 0.72f,
                    userMarkerProgress = 0.86f,
                    leaderMarkerProgress = 0.94f,
                )
            }
        }
    }
}
