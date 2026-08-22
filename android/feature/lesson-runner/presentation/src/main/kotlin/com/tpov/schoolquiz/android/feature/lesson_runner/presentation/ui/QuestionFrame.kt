@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.glowEasy
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import kotlinx.coroutines.delay

private const val FEEDBACK_FLIP_DURATION_MS = 420
private const val FEEDBACK_FLIP_HALF_ROTATION = 90f
private const val FEEDBACK_FLIP_FULL_ROTATION = 180f
private const val FEEDBACK_CAMERA_DISTANCE_FACTOR = 18f
private const val SELECTED_OPTION_CONTAINER_ALPHA = 0.16f
private const val FEEDBACK_OPTION_CONTAINER_ALPHA = 0.18f

internal data class FeedbackFlipState(
    val rotation: Float,
    val showBack: Boolean,
)

@Composable
internal fun rememberFeedbackFlipState(
    isFeedback: Boolean,
    feedbackTone: AnswerFeedbackTone,
    feedbackDelayMillis: Int,
): FeedbackFlipState {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isFeedback, feedbackTone, feedbackDelayMillis) {
        if (!isFeedback) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        rotation.snapTo(0f)
        if (feedbackDelayMillis > 0) {
            delay(feedbackDelayMillis.toLong())
        }
        rotation.animateTo(
            targetValue = FEEDBACK_FLIP_FULL_ROTATION,
            animationSpec = tween(durationMillis = FEEDBACK_FLIP_DURATION_MS),
        )
    }

    return FeedbackFlipState(
        rotation = rotation.value,
        showBack = isFeedback && rotation.value >= FEEDBACK_FLIP_HALF_ROTATION,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun QuestionFrame(
    questionText: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    bottomAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasQuestionSurface = !questionText.isNullOrBlank() || imageUrl != null
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!questionText.isNullOrBlank()) {
                RunnerDesignCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevated = true,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = questionText,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (imageUrl != null) {
                            QuestionImage(url = imageUrl, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else if (imageUrl != null) {
                RunnerDesignCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevated = true,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                ) {
                    QuestionImage(
                        url = imageUrl,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
            if (hasQuestionSurface) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
        if (bottomAction != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            ) {
                bottomAction()
            }
        }
    }
}

/**
 * The single primary action of the question screen.
 *
 * NOIR sets the rules here and they are easy to break by habit: the label is monospace uppercase
 * with wide tracking, never the display face, and there is exactly one filled action per screen.
 * The check icon is gone — the button already says what it does, and the specification counts a
 * decorative glyph against nothing but the eye.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun RunnerPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    NoirButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Suppress("FunctionNaming", "LongParameterList", "CyclomaticComplexMethod", "ktlint:standard:function-naming")
@Composable
internal fun AnswerOptionSurface(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    feedbackTone: AnswerFeedbackTone = AnswerFeedbackTone.Neutral,
    feedbackDelayMillis: Int = 0,
    onClick: () -> Unit,
) {
    val isFeedback = feedbackTone != AnswerFeedbackTone.Neutral
    val flip =
        rememberFeedbackFlipState(
            isFeedback = isFeedback,
            feedbackTone = feedbackTone,
            feedbackDelayMillis = feedbackDelayMillis,
        )
    val displayTone = if (flip.showBack) feedbackTone else AnswerFeedbackTone.Neutral
    val surface = runnerAnswerSurfaceColor()
    val correctColor = MaterialTheme.colorScheme.glowEasy
    val wrongColor = MaterialTheme.colorScheme.error
    val defaultBorderColor = runnerLightBorderColor()
    val selectedColor =
        MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_OPTION_CONTAINER_ALPHA).compositeOver(surface)
    val defaultColor = surface
    val borderColor =
        when (displayTone) {
            AnswerFeedbackTone.Correct -> correctColor
            AnswerFeedbackTone.Wrong -> wrongColor
            AnswerFeedbackTone.Neutral ->
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    defaultBorderColor
                }
        }
    val contentColor =
        when (displayTone) {
            AnswerFeedbackTone.Correct -> MaterialTheme.colorScheme.onSurface
            AnswerFeedbackTone.Wrong -> MaterialTheme.colorScheme.onSurface
            AnswerFeedbackTone.Neutral -> MaterialTheme.colorScheme.onSurface
        }
    val containerColor =
        when (displayTone) {
            AnswerFeedbackTone.Correct ->
                correctColor.copy(alpha = FEEDBACK_OPTION_CONTAINER_ALPHA).compositeOver(surface)
            AnswerFeedbackTone.Wrong ->
                wrongColor.copy(alpha = FEEDBACK_OPTION_CONTAINER_ALPHA).compositeOver(surface)
            AnswerFeedbackTone.Neutral -> if (selected) selectedColor else defaultColor
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationY = flip.rotation
                    cameraDistance = FEEDBACK_CAMERA_DISTANCE_FACTOR * density
                },
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(width = if (selected || flip.showBack) 2.dp else 1.5.dp, color = borderColor),
        tonalElevation = if (selected || flip.showBack) 2.dp else 0.dp,
        shadowElevation = if (selected || flip.showBack) 2.dp else 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .graphicsLayer {
                        rotationY = if (flip.showBack) FEEDBACK_FLIP_FULL_ROTATION else 0f
                    }
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Unspecified,
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailing()
            }
        }
    }
}
