package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import kotlin.math.roundToInt

private const val DISABLED_ALPHA = 0.3f
private const val DROP_TARGET_ALPHA = 0.4f
private const val FEEDBACK_FLIP_FULL_ROTATION = 180f
private const val FEEDBACK_STAGGER_MS = 90
private const val FEEDBACK_CONTAINER_ALPHA = 0.16f

@Suppress(
    "FunctionNaming",
    "LongMethod",
    "CyclomaticComplexMethod",
    "LongParameterList",
    "ktlint:standard:function-naming",
)
@Composable
fun OrderingContent(
    state: QuestionUiState.Ordering,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    feedback: AnswerFeedback.Ordering? = null,
    hintEnabled: Boolean = false,
    onHint: () -> Unit = {},
) {
    val lastIndex = state.items.lastIndex
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableIntStateOf(0) }

    QuestionFrame(
        questionText = state.questionText,
        imageUrl = state.imageUrl?.takeIf { state.hasImage },
        modifier = modifier,
        bottomAction = {
            RunnerPrimaryAction(
                text = stringResource(R.string.runner_action_done),
                enabled = feedback == null,
                onClick = onSubmit,
            )
        },
        hintAction = {
            RunnerSecondaryAction(
                text = stringResource(R.string.runner_hint_action),
                enabled = hintEnabled,
                onClick = onHint,
            )
        },
    ) {
        state.items.forEachIndexed { index, item ->
            val isDragging = draggingIndex == index
            val targetIndex =
                draggingIndex?.let { from ->
                    val delta = (dragOffsetY / itemHeightPx.toFloat().coerceAtLeast(1f)).roundToInt()
                    (from + delta).coerceIn(0, lastIndex)
                }
            val tone =
                when {
                    feedback == null -> AnswerFeedbackTone.Neutral
                    !feedback.revealCorrect -> AnswerFeedbackTone.Neutral
                    feedback.correctOrderIds.getOrNull(index) == item.id -> AnswerFeedbackTone.Correct
                    else -> AnswerFeedbackTone.Wrong
                }
            val isFeedback = tone != AnswerFeedbackTone.Neutral
            val flip =
                rememberFeedbackFlipState(
                    isFeedback = isFeedback,
                    feedbackTone = tone,
                    feedbackDelayMillis = if (feedback == null) 0 else index * FEEDBACK_STAGGER_MS,
                )
            val displayTone = if (flip.showBack) tone else AnswerFeedbackTone.Neutral
            val surface = runnerAnswerSurfaceColor()
            val borderColor =
                when (displayTone) {
                    AnswerFeedbackTone.Correct -> NoirSuccess
                    AnswerFeedbackTone.Wrong -> NoirDanger
                    AnswerFeedbackTone.Muted, AnswerFeedbackTone.Neutral -> runnerLightBorderColor()
                }
            val containerColor =
                when (displayTone) {
                    AnswerFeedbackTone.Correct ->
                        NoirSuccess.copy(alpha = FEEDBACK_CONTAINER_ALPHA).compositeOver(surface)
                    AnswerFeedbackTone.Wrong ->
                        NoirDanger.copy(alpha = FEEDBACK_CONTAINER_ALPHA).compositeOver(surface)
                    AnswerFeedbackTone.Muted, AnswerFeedbackTone.Neutral -> surface
                }
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            rotationX = flip.rotation
                            cameraDistance = FEEDBACK_CAMERA_DISTANCE_FACTOR * density
                        }
                        .onSizeChanged { size -> if (itemHeightPx == 0) itemHeightPx = size.height }
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset { IntOffset(x = 0, y = if (isDragging) dragOffsetY.roundToInt() else 0) }
                        .alpha(if (!isDragging && targetIndex == index) DROP_TARGET_ALPHA else 1f)
                        .pointerInput(index, lastIndex, feedback) {
                            if (feedback != null) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    if (from != null) {
                                        val delta =
                                            (dragOffsetY / itemHeightPx.toFloat().coerceAtLeast(1f)).roundToInt()
                                        val to = (from + delta).coerceIn(0, lastIndex)
                                        if (from != to) onReorder(from, to)
                                    }
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                },
                            )
                        },
                shape = MaterialTheme.shapes.small,
                color = containerColor,
                border =
                    BorderStroke(
                        if (displayTone == AnswerFeedbackTone.Neutral) 1.dp else 2.dp,
                        borderColor,
                    ),
                tonalElevation = if (displayTone == AnswerFeedbackTone.Neutral) 0.dp else 2.dp,
                shadowElevation = if (displayTone == AnswerFeedbackTone.Neutral) 0.dp else 2.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .graphicsLayer {
                                rotationX = if (flip.showBack) FEEDBACK_FLIP_FULL_ROTATION else 0f
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.runner_cd_drag),
                        tint = NoirT3,
                    )
                    Text(
                        text = item.text,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        style = NoirType.rowTitle,
                        color = NoirT1,
                    )
                    IconButton(
                        onClick = { onMoveUp(index) },
                        enabled = feedback == null && index > 0,
                        modifier = Modifier.alpha(if (index > 0) 1f else DISABLED_ALPHA),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.runner_cd_move_up),
                        )
                    }
                    IconButton(
                        onClick = { onMoveDown(index) },
                        enabled = feedback == null && index < lastIndex,
                        modifier = Modifier.alpha(if (index < lastIndex) 1f else DISABLED_ALPHA),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.runner_cd_move_down),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun OrderingContentPreview() {
    SchoolQuizTheme {
        OrderingContent(
            state =
                QuestionUiState.Ordering(
                    questionText = "Расставьте в правильном порядке",
                    hasImage = false,
                    imageUrl = null,
                    items =
                        listOf(
                            OptionUi("1", "Первый шаг"),
                            OptionUi("2", "Второй шаг"),
                            OptionUi("3", "Третий шаг"),
                        ),
                ),
            onMoveUp = {},
            onMoveDown = {},
            onReorder = { _, _ -> },
            onSubmit = {},
        )
    }
}
