package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.glowEasy
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart

private const val CONSUMED_ALPHA = 0.4f
private const val FEEDBACK_FLIP_FULL_ROTATION = 180f
private const val FEEDBACK_STAGGER_MS = 90
private const val FILLED_BLANK_CONTAINER_ALPHA = 0.2f
private const val FEEDBACK_BLANK_CONTAINER_ALPHA = 0.18f

@OptIn(ExperimentalLayoutApi::class)
@Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "ktlint:standard:function-naming",
)
@Composable
fun FillBlankContent(
    state: QuestionUiState.FillBlank,
    candidates: List<OptionUi>,
    usedCandidateIds: Set<String>,
    onCandidateSelected: (blankIndex: Int, candidateId: String) -> Unit,
    onBlankCleared: (blankIndex: Int) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    feedback: AnswerFeedback.FillBlank? = null,
) {
    val blankParts = state.templateParts.filterIsInstance<TemplatePart.Blank>()
    val firstEmptyIndex = blankParts.firstOrNull { it.index !in state.filledValues }?.index
    var activeBlankIndex by remember(blankParts.map { it.index }) { mutableStateOf(firstEmptyIndex) }
    val feedbackFilledValues = feedback?.filledCandidateIdsByBlankIndex
    val candidateTextById = candidates.associate { it.id to it.text }
    val isComplete = blankParts.all { it.index in state.filledValues }

    LaunchedEffect(state.filledValues, firstEmptyIndex) {
        if (activeBlankIndex == null || activeBlankIndex in state.filledValues) {
            activeBlankIndex = firstEmptyIndex
        }
    }

    QuestionFrame(
        questionText = null,
        imageUrl = state.imageUrl?.takeIf { state.hasImage },
        modifier = modifier,
        bottomAction = {
            RunnerPrimaryAction(
                text = "Готово",
                enabled = feedback == null && isComplete,
                onClick = onSubmit,
            )
        },
    ) {
        RunnerDesignCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = MaterialTheme.colorScheme.primary,
        ) {
            FlowRow(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.templateParts.forEach { part ->
                    when (part) {
                        is TemplatePart.Text -> {
                            Text(
                                text = part.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        is TemplatePart.Blank -> {
                            val feedbackCandidateId = feedbackFilledValues?.get(part.index)
                            val filledText =
                                state.filledValues[part.index] ?: feedbackCandidateId?.let(candidateTextById::get)
                            val isActive = part.index == activeBlankIndex
                            val tone =
                                when {
                                    feedbackCandidateId == null -> AnswerFeedbackTone.Neutral
                                    feedback?.revealCorrect == false -> AnswerFeedbackTone.Neutral
                                    feedbackCandidateId == state.correctCandidateIdsByBlankIndex[part.index] ->
                                        AnswerFeedbackTone.Correct
                                    else -> AnswerFeedbackTone.Wrong
                                }
                            val isFeedback = tone != AnswerFeedbackTone.Neutral
                            val flip =
                                rememberFeedbackFlipState(
                                    isFeedback = isFeedback,
                                    feedbackTone = tone,
                                    feedbackDelayMillis =
                                        if (feedback == null) {
                                            0
                                        } else {
                                            part.index * FEEDBACK_STAGGER_MS
                                        },
                                )
                            val displayTone = if (flip.showBack) tone else AnswerFeedbackTone.Neutral
                            val surface = runnerAnswerSurfaceColor()
                            val blankBorderColor =
                                when (displayTone) {
                                    AnswerFeedbackTone.Correct -> MaterialTheme.colorScheme.glowEasy
                                    AnswerFeedbackTone.Wrong -> MaterialTheme.colorScheme.error
                                    AnswerFeedbackTone.Neutral -> runnerLightBorderColor()
                                }
                            val blankContainerColor =
                                when {
                                    displayTone == AnswerFeedbackTone.Correct ->
                                        MaterialTheme.colorScheme.glowEasy
                                            .copy(alpha = FEEDBACK_BLANK_CONTAINER_ALPHA)
                                            .compositeOver(surface)
                                    displayTone == AnswerFeedbackTone.Wrong ->
                                        MaterialTheme.colorScheme.error
                                            .copy(alpha = FEEDBACK_BLANK_CONTAINER_ALPHA)
                                            .compositeOver(surface)
                                    isActive -> MaterialTheme.colorScheme.primary
                                    filledText != null ->
                                        MaterialTheme.colorScheme.primary
                                            .copy(alpha = FILLED_BLANK_CONTAINER_ALPHA)
                                            .compositeOver(surface)
                                    else -> surface
                                }
                            OutlinedButton(
                                onClick = {
                                    if (feedback != null) return@OutlinedButton
                                    if (filledText != null) {
                                        onBlankCleared(part.index)
                                    }
                                    activeBlankIndex = part.index
                                },
                                modifier =
                                    Modifier.graphicsLayer {
                                        rotationY = flip.rotation
                                        cameraDistance = FEEDBACK_CAMERA_DISTANCE_FACTOR * density
                                    },
                                shape = MaterialTheme.shapes.small,
                                border =
                                    BorderStroke(
                                        width = feedbackBorderWidth(displayTone, isActive),
                                        color = blankBorderColor,
                                    ),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = blankContainerColor,
                                        contentColor =
                                            if (isActive && displayTone == AnswerFeedbackTone.Neutral) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    ),
                            ) {
                                Text(
                                    text = filledText ?: part.placeholder,
                                    modifier =
                                        Modifier.graphicsLayer {
                                            rotationY =
                                                if (flip.showBack) {
                                                    FEEDBACK_FLIP_FULL_ROTATION
                                                } else {
                                                    0f
                                                }
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (candidates.isNotEmpty()) {
            Text(
                text = "Варианты",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                candidates.forEach { candidate ->
                    val isUsed = candidate.id in usedCandidateIds
                    Button(
                        onClick = {
                            if (feedback != null) return@Button
                            val targetIndex = activeBlankIndex ?: firstEmptyIndex
                            if (!isUsed && targetIndex != null) {
                                onCandidateSelected(targetIndex, candidate.id)
                            }
                        },
                        enabled = feedback == null && !isUsed,
                        modifier = Modifier.alpha(if (isUsed) CONSUMED_ALPHA else 1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                    ) {
                        Text(text = candidate.text)
                    }
                }
            }
        }
    }
}

private fun feedbackBorderWidth(
    displayTone: AnswerFeedbackTone,
    isActive: Boolean,
) = if (displayTone != AnswerFeedbackTone.Neutral || isActive) 2.dp else 1.dp

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun FillBlankContentPreview() {
    SchoolQuizTheme {
        FillBlankContent(
            state =
                QuestionUiState.FillBlank(
                    questionText = "Kotlin ___ JVM ___",
                    hasImage = false,
                    imageUrl = null,
                    templateParts =
                        listOf(
                            TemplatePart.Text("Kotlin "),
                            TemplatePart.Blank(0, "___", "b0"),
                            TemplatePart.Text(" JVM "),
                            TemplatePart.Blank(1, "___", "b1"),
                        ),
                    filledValues = mapOf(0 to "работает"),
                ),
            candidates =
                listOf(
                    OptionUi("1", "работает"),
                    OptionUi("2", "на"),
                    OptionUi("3", "платформе"),
                ),
            usedCandidateIds = setOf("1"),
            onCandidateSelected = { _, _ -> },
            onBlankCleared = {},
            onSubmit = {},
            feedback = null,
        )
    }
}
