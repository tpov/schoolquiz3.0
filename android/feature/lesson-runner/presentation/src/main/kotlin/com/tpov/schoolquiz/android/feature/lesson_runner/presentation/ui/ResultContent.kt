@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassCard
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore

private const val PERFECT_SCORE = 100
private const val HARD_REWARD_MULTIPLIER = 2
private const val NOLICS_PERCENT_STEP = 10

/**
 * The end of an attempt.
 *
 * One glass card holds the score, the personal best it is measured against, and the four figures
 * that changed. Below it the leaderboard, and at the foot two words instead of two buttons —
 * running again and moving on are both ordinary, and neither deserves a filled slab.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun ResultContent(
    state: RunnerUiState.Result,
    onSubmitRating: (Int) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHard = state.mode == Difficulty.HARD
    val modeLabel = if (isHard) "hard" else "easy"
    val earnedExperience = resultExperienceReward(state.percentScore.raw, state.mode)
    val earnedNolics = resultNolicsReward(state.percentScore.raw, state.mode)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Урок пройден · $modeLabel".uppercase(), style = NoirType.kicker)

        NoirGlassCard {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.percentScore.raw.toString(),
                        style = NoirType.num.copy(fontSize = 56.sp, fontWeight = FontWeight.Bold),
                    )
                    Text("%", style = NoirType.num.copy(fontSize = 22.sp, color = NoirT3))
                }
                BestMarkScale(
                    current = state.percentScore.raw,
                    best = state.userBestPercentScore,
                    isHard = isHard,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ResultFigure("Attempt", state.userAttemptCount.toString())
                    ResultFigure("Average", "${state.userAveragePercentScore}%")
                    ResultFigure("XP", "+$earnedExperience")
                    ResultFigure("Nolics", "+$earnedNolics")
                }
            }
        }

        if (state.saveWarning) {
            Text(
                "Результат не сохранён — уйдёт при следующей синхронизации",
                style = NoirType.rowSub.copy(color = NoirDanger),
            )
        }

        if (state.showRatingPrompt) {
            RatingPromptSection(
                ratingSubmissionState = state.ratingSubmissionState,
                onSubmitRating = onSubmitRating,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.top3.isNotEmpty()) {
            Top3Section(top3 = state.top3)
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Ещё раз",
                style = NoirType.button.copy(color = NoirT3),
                modifier = Modifier.clickable(onClick = onFinish),
            )
            Text(
                "Дальше →",
                style = NoirType.button.copy(color = LocalNoirAccent.current),
                modifier = Modifier.clickable(onClick = onFinish),
            )
        }
    }
}

/**
 * This attempt against your own best.
 *
 * The mark is the number worth beating, and it is the player's own — a leaderboard says who is
 * ahead, this says whether today went better than last time.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun BestMarkScale(
    current: Int,
    best: Int,
    isHard: Boolean,
) {
    val accent = if (isHard) NoirDanger else LocalNoirAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(NoirShapePill)
                .background(NoirOutline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(current.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .clip(NoirShapePill)
                    .background(accent),
            )
        }
        Text("Ваш лучший $best%", style = NoirType.kicker)
    }
}

/** A figure and what it counts. Mono, so a column of them lines up. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultFigure(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), style = NoirType.kicker)
        Text(value, style = NoirType.num.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
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
