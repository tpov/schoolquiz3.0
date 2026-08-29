@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChrome
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassFill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeXl
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ResultAdvice
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore

private const val PERFECT_SCORE = 100
private const val HARD_REWARD_MULTIPLIER = 2
private const val NOLICS_PERCENT_STEP = 10

// Accuracy chart geometry, lifted from the design's SVG (viewBox 340×120 rendered at 104×44):
// the plot spans x 10..330, score digits 1..9 sit on nine levels 11.5 units apart with '9' on
// y=14, and hairline gridlines cross at y=14/60/106.

// Best-mark scale, from the same design: a 34px block holding a 5px track, a 2×14 tick at the
// best mark, and the label hanging under the tick's position.
private const val BEST_SCALE_HEIGHT_DP = 34
private const val BEST_TRACK_HEIGHT_DP = 5
private const val BEST_FILL_ALPHA = 0.65f

// Chart paint, straight from the design: success wash under the line (0.10), danger wash above
// it (0.08), the stroke at three-quarter accent with a heavier head dot.

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
    comments: List<LessonComment>,
    onSubmitRating: (Int) -> Unit,
    onRunAgain: () -> Unit,
    onNextLesson: () -> Unit,
    onPostComment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHard = state.mode == Difficulty.HARD
    val earnedExperience = resultExperienceReward(state.percentScore.raw, state.mode)
    val earnedNolics = resultNolicsReward(state.percentScore.raw, state.mode)
    var discussionOpen by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text =
                stringResource(
                    if (isHard) R.string.runner_result_kicker_hard else R.string.runner_result_kicker_easy,
                ).uppercase(),
            style = NoirType.kicker,
        )

        ResultScoreCard(
            state = state,
            isHard = isHard,
            earnedExperience = earnedExperience,
            earnedNolics = earnedNolics,
        )

        if (state.saveWarning) {
            Text(
                stringResource(R.string.runner_result_save_warning),
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

        DiscussionRow(count = comments.size, onOpen = { discussionOpen = true })

        state.advice?.let { AdviceSection(advice = it) }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.runner_result_again),
                style = NoirType.button.copy(color = NoirT3),
                modifier = Modifier.clickable(onClick = onRunAgain),
            )
            Text(
                stringResource(R.string.runner_result_next),
                style = NoirType.button.copy(color = LocalNoirAccent.current),
                modifier = Modifier.clickable(onClick = onNextLesson),
            )
        }
    }

    if (discussionOpen) {
        DiscussionSheet(
            comments = comments,
            onPostComment = onPostComment,
            onDismiss = { discussionOpen = false },
        )
    }
}

/**
 * Where the points went, and what to read to get them next time.
 *
 * A percentage tells somebody how it went and nothing about what to do about it. Naming the count
 * of weak answers turns the score into something specific, and the prerequisite lesson turns it
 * into a next step — which is the difference between a result screen and a verdict.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun AdviceSection(advice: ResultAdvice) {
    val accent = LocalNoirAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NoirShapeLg)
            .background(NoirGlassFill)
            .border(1.dp, NoirGlassStroke, NoirShapeLg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(stringResource(R.string.runner_advice_kicker), style = NoirType.kicker.copy(color = NoirT2))
        Text(
            text =
                pluralStringResource(
                    R.plurals.runner_advice_weak_answers,
                    advice.weakAnswers,
                    advice.weakAnswers,
                ),
            style = NoirType.rowSub.copy(color = NoirT2),
        )
        advice.suggestedLessonTitle?.let { title ->
            Text(
                text = stringResource(R.string.runner_advice_suggested_lesson, title),
                style = NoirType.rowSub.copy(color = accent),
            )
        }
    }
}

/**
 * The discussion, as one row: how many have said something, and a way in.
 *
 * A door, not the room. The thread itself is a modal — on a result screen the comments are the
 * least of what somebody came for, and inline they push the actions off the bottom.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DiscussionRow(
    count: Int,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .defaultMinSize(minHeight = 60.dp)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.runner_discussion_title),
                style = NoirType.rowTitle.copy(fontSize = 15.sp, color = NoirT1),
            )
            Text(
                text = discussionCountWording(count),
                style = NoirType.rowSub.copy(fontSize = 12.5.sp, color = NoirT2),
            )
        }
        Text(
            stringResource(R.string.runner_discussion_open),
            style = NoirType.num.copy(fontSize = 12.sp, color = LocalNoirAccent.current),
        )
    }
}

/** "1 комментарий", "2 комментария", "5 комментариев" — and the teens count like many. */
private fun discussionCountWording(count: Int): String {
    if (count == 0) return "Пока никто не написал"
    val tail = count % 100
    val last = count % 10
    val teens = tail in 11..14
    val noun =
        when {
            teens -> "комментариев"
            last == 1 -> "комментарий"
            last in 2..4 -> "комментария"
            else -> "комментариев"
        }
    return "$count $noun"
}

/**
 * The score, as the drawing builds it: a lifted plate with a wash coming out of its top-left
 * corner, one very large figure, the mark to beat under it, and the rewards along the foot.
 *
 * The plate is a fill step rather than an outlined card. The set this comes from dropped strokes
 * everywhere — at this palette a 1px white edge reads grey and chops the screen into boxes — so
 * depth here is carried by how light the fill is and by the shadow under it.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultScoreCard(
    state: RunnerUiState.Result,
    isHard: Boolean,
    earnedExperience: Int,
    earnedNolics: Int,
) {
    val tone = if (isHard) NoirDanger else NoirSuccess
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NoirShapeXl)
            .background(NoirChrome)
            .drawBehind {
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(tone.copy(alpha = 0.32f), Color.Transparent),
                            center = Offset.Zero,
                            radius = size.maxDimension * SCORE_WASH_REACH,
                        ),
                )
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = state.percentScore.raw.toString(),
                    style = NoirType.num.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold, color = NoirT1),
                )
                Text(
                    "%",
                    style = NoirType.num.copy(fontSize = 20.sp, color = NoirT2),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            BestMarkScale(
                current = state.percentScore.raw,
                best = state.userBestPercentScore,
                isHard = isHard,
            )
        }
        ResultRewardStrip(
            state = state,
            earnedExperience = earnedExperience,
            earnedNolics = earnedNolics,
        )
    }
}

/**
 * What the run paid, along the foot of the card.
 *
 * Sunk rather than raised — a darker wash with a light hairline over it — so it reads as the base
 * of the card and not as three more things to tap.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultRewardStrip(
    state: RunnerUiState.Result,
    earnedExperience: Int,
    earnedNolics: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f))
            .drawBehind {
                drawRect(color = NoirGlassStroke, size = Size(size.width, 1.dp.toPx()))
            },
    ) {
        RewardCell(NoirIcons.Star, "+$earnedExperience", LocalNoirAccent.current)
        RewardCell(NoirIcons.Nolic, "+$earnedNolics", LocalNoirAccent.current)
        state.livesRemainingHearts?.let { RewardCell(NoirIcons.Bolt, "−1", NoirDanger) }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RowScope.RewardCell(
    icon: ImageVector,
    value: String,
    tint: Color,
) {
    Row(
        Modifier
            .weight(1f)
            .drawBehind {
                drawRect(color = NoirHair, size = Size(1.dp.toPx(), size.height))
            }
            .padding(horizontal = 8.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(value, style = NoirType.num.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NoirT1))
    }
}

/** How far the corner wash reaches across the card, as a share of its longest side. */
private const val SCORE_WASH_REACH = 1.32f

/**
 * This attempt against your own best.
 *
 * The tick is the number worth beating, and it is the player's own — a leaderboard says who is
 * ahead, this says whether today went better than last time. The label hangs off the tick so the
 * two read as one mark, not as a caption under the bar.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun BestMarkScale(
    current: Int,
    best: Int,
    isHard: Boolean,
) {
    val accent = if (isHard) NoirDanger else LocalNoirAccent.current
    Box(Modifier.fillMaxWidth().height(BEST_SCALE_HEIGHT_DP.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BEST_TRACK_HEIGHT_DP.dp)
                .clip(NoirShapePill)
                .background(NoirS2),
        )
        Box(
            Modifier
                .fillMaxWidth(current.coerceIn(0, PERFECT_SCORE) / PERFECT_SCORE.toFloat())
                .height(BEST_TRACK_HEIGHT_DP.dp)
                .clip(NoirShapePill)
                .background(accent.copy(alpha = BEST_FILL_ALPHA)),
        )
        if (best > 0) {
            Box(Modifier.fillMaxWidth(best.coerceIn(0, PERFECT_SCORE) / PERFECT_SCORE.toFloat())) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .width(2.dp)
                        .height(14.dp)
                        .background(NoirT2),
                )
                Text(
                    stringResource(R.string.runner_result_best, best),
                    style =
                        NoirType.kicker.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em,
                            color = NoirT2,
                        ),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 16.dp),
                )
            }
        }
    }
}

/**
 * The lesson's discussion: the comments people left, and a one-line field to add one.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DiscussionSheet(
    comments: List<LessonComment>,
    onPostComment: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DiscussionBody(comments = comments, onPostComment = onPostComment)
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DiscussionBody(
    comments: List<LessonComment>,
    onPostComment: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val accent = LocalNoirAccent.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.runner_discussion_header),
                style = NoirType.kicker.copy(color = NoirT2),
            )
            Text(
                text = comments.size.toString(),
                style = NoirType.kicker.copy(color = NoirT2),
            )
        }
        comments.forEach { comment -> CommentRow(comment) }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(NoirShapeMd)
                    .background(NoirS2)
                    .border(1.dp, NoirOutline, NoirShapeMd)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = stringResource(R.string.runner_discussion_placeholder),
                        style = NoirType.rowSub.copy(color = NoirTOff),
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = NoirType.rowSub.copy(color = NoirT1),
                    singleLine = true,
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.runner_discussion_send),
                style = NoirType.button.copy(color = accent),
                modifier =
                    Modifier.clickable {
                        if (draft.isNotBlank()) {
                            onPostComment(draft)
                            draft = ""
                        }
                    },
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun CommentRow(comment: LessonComment) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(NoirShapePill)
                .background(NoirS3)
                .border(1.dp, NoirOutline, NoirShapePill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = comment.authorNickname.take(1).uppercase(),
                style = NoirType.kicker.copy(color = NoirT2),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = comment.authorNickname,
                style = NoirType.rowTitle.copy(fontSize = 12.5.sp),
            )
            Text(
                text = comment.text,
                style = NoirType.rowSub.copy(color = NoirT2),
            )
        }
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
                    advice = null,
                    questionScores =
                        listOf(9, 9, 9, 8, 8, 7, 7, 7, 6, 6, 5, 5, 5, 4, 3, 3, 1, 1, 1, 1),
                    livesRemainingHearts = 3,
                    livesMaxHearts = 5,
                    showRatingPrompt = true,
                    saveWarning = false,
                ),
            onSubmitRating = {},
            comments = emptyList(),
            onRunAgain = {},
            onNextLesson = {},
            onPostComment = {},
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
                    questionScores = listOf(5, 6, 4, 7, 3, 2, 8, 1),
                    showRatingPrompt = false,
                    saveWarning = true,
                ),
            onSubmitRating = {},
            comments = emptyList(),
            onRunAgain = {},
            onNextLesson = {},
            onPostComment = {},
        )
    }
}
