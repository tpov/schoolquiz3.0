@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
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
    hintAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // The question owns the space above the answers and sits in the middle of it.
        //
        // One scrolling column put the question hard against the header with the answers pressed
        // underneath, which reads as a form. The design gives the question the whole upper half
        // and lets the answers sit at the bottom where a thumb is — so the two are laid out as two
        // regions, not one list.
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (!questionText.isNullOrBlank()) {
                // No card around the question. It is the screen, and a border around the screen's
                // subject only competes with it; the mode glow behind already does the framing.
                Text(text = questionText, style = NoirType.question)
                if (imageUrl != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    QuestionImage(url = imageUrl, modifier = Modifier.fillMaxWidth())
                }
            } else if (imageUrl != null) {
                RunnerDesignCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevated = true,
                    borderColor = LocalNoirAccent.current.copy(alpha = 0.86f),
                ) {
                    QuestionImage(
                        url = imageUrl,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }

        content()

        if (bottomAction != null || hintAction != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                hintAction?.invoke()
                Spacer(Modifier.weight(1f))
                bottomAction?.invoke()
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
    Text(
        text = text,
        style =
            NoirType.button.copy(
                fontSize = 13.sp,
                color = if (enabled) LocalNoirAccent.current else NoirTOff,
            ),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
    )
}

/**
 * The quiet left-hand action of the bottom row — the hint. Muted on purpose: it is a wayfinding
 * label, not an invitation, and it must never compete with the answer action on the right.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun RunnerSecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text.uppercase(),
        style =
            NoirType.chip.copy(
                fontSize = 11.sp,
                color = if (enabled) NoirT3 else NoirTOff,
            ),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
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
            AnswerFeedbackTone.Muted -> NoirTOff
            AnswerFeedbackTone.Neutral -> if (selected) accent else NoirT2
        }
    // No fill on selection: the frame (radio/checkbox + hairline) carries it, per the design.
    val fill =
        when {
            displayTone == AnswerFeedbackTone.Correct -> NoirSuccess.copy(alpha = 0.10f)
            displayTone == AnswerFeedbackTone.Wrong -> NoirDanger.copy(alpha = 0.10f)
            else -> Color.Transparent
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (displayTone == AnswerFeedbackTone.Muted) MUTED_ROW_ALPHA else 1f),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationX = flip.rotation
                    cameraDistance = FLIP_CAMERA_DISTANCE
                }
                .background(fill)
                .clickable(enabled = enabled, onClick = onClick)
                .defaultMinSize(minHeight = 62.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
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

/**
 * The letter that names an answer: A, B, C, D.
 *
 * A letter rather than a radio dot, as the design draws it. The row is the target — the whole
 * width of it — so a control that looks like a small thing to hit invites aiming at the small
 * thing, and the letter reads as a label instead.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun AnswerKeyLetter(
    index: Int,
    selected: Boolean,
) {
    Text(
        text = ('A' + index).toString(),
        style = NoirType.num.copy(fontSize = 13.sp, color = if (selected) LocalNoirAccent.current else NoirTOff),
    )
}

/**
 * The same letter, boxed — how the design marks an answer you can pick several of.
 *
 * The box is what says "more than one of these": a bare letter reads as single choice, and the
 * player finds out otherwise only after tapping. 22dp square with a 1.5dp edge, from the drawing.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun AnswerKeyBox(
    index: Int,
    selected: Boolean,
) {
    val accent = LocalNoirAccent.current
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) accent.copy(alpha = 0.20f) else Color.Transparent)
            .border(1.5.dp, if (selected) accent else NoirOutline, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ('A' + index).toString(),
            style = NoirType.num.copy(fontSize = 10.sp, color = if (selected) accent else NoirTOff),
        )
    }
}

private const val FLIP_CAMERA_DISTANCE = 16f

/** Alpha of a correct-but-unchosen row (design decision F6) — invited, not celebrated. */
internal const val MUTED_ROW_ALPHA = 0.38f

/**
 * Camera distance for the reveal flip, shared by every question type.
 *
 * Internal rather than private: fill-in-the-blank and ordering flip their own rows and must turn
 * through the same perspective, or the same gesture reads as two different animations.
 */
internal const val FEEDBACK_CAMERA_DISTANCE_FACTOR = 16f
