@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore

private const val PERFECT_SCORE = 100
private const val RESULT_PROGRESS_ANIMATION_MS = 900
private const val HARD_REWARD_MULTIPLIER = 2
private const val NOLICS_PERCENT_STEP = 10

@Suppress("FunctionNaming", "LongMethod", "UnusedParameter", "ktlint:standard:function-naming")
@Composable
fun ResultContent(
    state: RunnerUiState.Result,
    onSubmitRating: (Int) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle =
        when {
            state.mode == Difficulty.EASY && state.percentScore.raw == PERFECT_SCORE && state.hardUnlocked ->
                "Поздравляем! Сложные вопросы доступны"
            state.mode == Difficulty.HARD && state.percentScore.raw == PERFECT_SCORE ->
                "100% сложные! Вы прошли урок полностью"
            else -> "Урок завершён"
        }
    val isHard = state.mode == Difficulty.HARD
    val accent = runnerModeAccent(isHard)
    val earnedExperience = resultExperienceReward(state.percentScore.raw, state.mode)
    val earnedNolics = resultNolicsReward(state.percentScore.raw, state.mode)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 76.dp, end = 16.dp, bottom = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ResultSummaryCard(
            modifier = Modifier.fillMaxWidth(),
            isHard = isHard,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${state.percentScore.raw}%",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
                ResultScoreProgress(
                    currentPercent = state.percentScore.raw,
                    userBestPercent = state.userBestPercentScore,
                    leader = state.top3.firstOrNull(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ResultMetrics(state = state, accent = accent)
            ResultRewardsRow(
                experience = earnedExperience,
                nolics = earnedNolics,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.saveWarning) {
                RunnerDesignCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = MaterialTheme.colorScheme.error,
                    elevated = true,
                ) {
                    Text(
                        text = "Результат не сохранён",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (state.top3.isNotEmpty()) {
            Top3Section(top3 = state.top3)
            Spacer(modifier = Modifier.height(32.dp))
        }
        RunnerResultAction(text = "Завершить", onClick = onFinish)
    }
}

private fun resultExperienceReward(
    percent: Int,
    mode: Difficulty,
): Int {
    val multiplier = if (mode == Difficulty.HARD) HARD_REWARD_MULTIPLIER else 1
    return percent.coerceIn(0, PERFECT_SCORE) * multiplier
}

private fun resultNolicsReward(
    percent: Int,
    mode: Difficulty,
): Int {
    val multiplier = if (mode == Difficulty.HARD) HARD_REWARD_MULTIPLIER else 1
    return (percent.coerceIn(0, PERFECT_SCORE) / NOLICS_PERCENT_STEP) * multiplier
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultSummaryCard(
    isHard: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = runnerCenterGlowColor(isHard = isHard),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp)) {
            content()
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultRewardsRow(
    experience: Int,
    nolics: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultRewardItem(
            label = "Опыт",
            value = "+$experience XP",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        ResultRewardItem(
            label = "Нолики",
            value = "+$nolics ◎",
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultRewardItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = runnerDeepSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, runnerLightBorderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultScoreProgress(
    currentPercent: Int,
    userBestPercent: Int,
    leader: TopParticipant?,
    modifier: Modifier = Modifier,
) {
    val currentProgress = (currentPercent / PERFECT_SCORE.toFloat()).coerceIn(0f, 1f)
    val userBestProgress = (userBestPercent / PERFECT_SCORE.toFloat()).coerceIn(0f, 1f)
    val leaderProgress = ((leader?.percent ?: 0) / PERFECT_SCORE.toFloat()).coerceIn(0f, 1f)
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(currentProgress) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = currentProgress,
            animationSpec =
                tween(
                    durationMillis = RESULT_PROGRESS_ANIMATION_MS,
                    easing = FastOutSlowInEasing,
                ),
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            val markerSize = 26.dp
            val markerWidth = 3.dp
            val userLineOffset =
                ((maxWidth * userBestProgress) - markerWidth / 2).coerceIn(0.dp, maxWidth - markerWidth)
            val leaderLineOffset =
                ((maxWidth * leaderProgress) - markerWidth / 2).coerceIn(0.dp, maxWidth - markerWidth)
            val userMarkerOffset =
                ((maxWidth * userBestProgress) - markerSize / 2).coerceIn(0.dp, maxWidth - markerSize)
            val leaderMarkerOffset =
                ((maxWidth * leaderProgress) - markerSize / 2).coerceIn(0.dp, maxWidth - markerSize)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(runnerNeutralBorderColor(), MaterialTheme.shapes.small),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .width(maxWidth * animatedProgress.value)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = userLineOffset)
                        .width(markerWidth)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
            )
            leader?.let { participant ->
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = leaderLineOffset)
                            .width(markerWidth)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.extraSmall),
                )
                ResultProgressMarker(
                    color = MaterialTheme.colorScheme.secondary,
                    avatarUrl = participant.avatarUrl,
                    modifier = Modifier.align(Alignment.TopStart).offset(x = leaderMarkerOffset),
                )
            }
            ResultProgressMarker(
                color = MaterialTheme.colorScheme.primary,
                avatarUrl = null,
                modifier = Modifier.align(Alignment.TopStart).offset(x = userMarkerOffset),
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultProgressMarker(
    color: Color,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(26.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = color,
        border = BorderStroke(1.dp, color),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (avatarUrl == null) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = color,
                )
            } else {
                ParticipantAvatar(
                    avatarUrl = avatarUrl,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultMetrics(
    state: RunnerUiState.Result,
    accent: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ResultMetricCard(
                label = "Попыток",
                value = state.userAttemptCount.toString(),
                accent = accent,
                modifier = Modifier.weight(1f),
            )
            ResultMetricCard(
                label = "Мой средний",
                value = "${state.userAveragePercentScore}%",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            ResultMetricCard(
                label = "Средний всех",
                value = "—",
                accent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    RunnerDesignCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = accent,
        containerColor = runnerDeepSurfaceColor(),
        borderColor = runnerLightBorderColor(),
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

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RunnerResultAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = runnerDeepSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = text,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun ResultContentPreview() {
    SchoolQuizTheme {
        ResultContent(
            state =
                RunnerUiState.Result(
                    percentScore = PercentScore(85),
                    mode = Difficulty.EASY,
                    completedAt = 0L,
                    hardUnlocked = false,
                    bestStarsRawTenths = 20,
                    currentAttemptStarsRawTenths = 17,
                    lessonAverageRating = 2.5f,
                    lessonRatingCount = 10,
                    top3 =
                        listOf(
                            TopParticipant("Alice", null, 95),
                            TopParticipant("Bob", null, 88),
                        ),
                    userAttemptCount = 3,
                    userAveragePercentScore = 75,
                    userBestPercentScore = 92,
                    showRatingPrompt = true,
                    saveWarning = false,
                ),
            onSubmitRating = {},
            onFinish = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun ResultContentSaveWarningPreview() {
    SchoolQuizTheme {
        ResultContent(
            state =
                RunnerUiState.Result(
                    percentScore = PercentScore(60),
                    mode = Difficulty.HARD,
                    completedAt = 0L,
                    hardUnlocked = false,
                    bestStarsRawTenths = 10,
                    currentAttemptStarsRawTenths = 26,
                    lessonAverageRating = null,
                    lessonRatingCount = 0,
                    top3 = emptyList(),
                    userAttemptCount = 1,
                    userAveragePercentScore = 60,
                    userBestPercentScore = 60,
                    showRatingPrompt = false,
                    saveWarning = true,
                ),
            onSubmitRating = {},
            onFinish = {},
        )
    }
}
