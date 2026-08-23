@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import kotlinx.coroutines.delay

private const val FEEDBACK_FLIP_DURATION_MS = 420
private const val FEEDBACK_FLIP_HALF_ROTATION = 90f
private const val FEEDBACK_FLIP_FULL_ROTATION = 180f

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
                // No card around the question. It is the screen, and a border around the screen's
                // subject only competes with it; the mode glow behind already does the framing.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = questionText,
                        style = NoirType.question,
                    )
                    if (imageUrl != null) {
                        QuestionImage(url = imageUrl, modifier = Modifier.fillMaxWidth())
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

/**
 * One answer: full width, separated by a hairline, no fill.
 *
 * Filled options turn a question into a menu of buttons and make the reading harder, which is the
 * opposite of what the screen is for. The flip on reveal keeps the row in place while it changes
 * meaning, so the eye does not have to find it again.
 *
 * A correct answer nobody chose is muted rather than lit. Blazing it rewards the mistake with the
 * brightest thing on screen.
 */
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
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
    val accent = LocalNoirAccent.current
    val toneColor =
        when (displayTone) {
            AnswerFeedbackTone.Correct -> NoirSuccess
            AnswerFeedbackTone.Wrong -> NoirDanger
            AnswerFeedbackTone.Neutral -> if (selected) accent else NoirT2
        }
    // The chosen row keeps a whisper of fill so it is findable after the reveal; everything else
    // stays on the ground.
    val fill =
        when {
            displayTone == AnswerFeedbackTone.Correct -> NoirSuccess.copy(alpha = 0.10f)
            displayTone == AnswerFeedbackTone.Wrong -> NoirDanger.copy(alpha = 0.10f)
            selected -> accent.copy(alpha = 0.08f)
            else -> Color.Transparent
        }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationX = flip.rotation
                    cameraDistance = FLIP_CAMERA_DISTANCE
                }
                .background(fill)
                .clickable(enabled = enabled, onClick = onClick)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke(this)
            Text(
                text = text,
                style = NoirType.rowTitle.copy(color = toneColor, fontSize = 15.sp),
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke(this)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NoirHair))
    }
}

private const val FLIP_CAMERA_DISTANCE = 16f

/**
 * Camera distance for the reveal flip, shared by every question type.
 *
 * Internal rather than private: fill-in-the-blank and ordering flip their own rows and must turn
 * through the same perspective, or the same gesture reads as two different animations.
 */
internal const val FEEDBACK_CAMERA_DISTANCE_FACTOR = 16f
