@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIconButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TIMER_TICK_MS = 250L

/**
 * The strip above the question: how to leave, what you hold, how much is left, and how long.
 *
 * A row rather than a card. The question is the screen; a boxed header would make the chrome look
 * like content, and the round already has a mode glow behind it doing the framing.
 *
 * The timer turns red in the last five seconds. That is the only alarm on the screen, so it earns
 * the colour — everything else stays neutral so this can be seen without looking for it.
 */
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun QuestionProgressHeader(
    indexInPool: Int,
    totalInPool: Int,
    deadlineMs: Long,
    isPaused: Boolean,
    isHard: Boolean,
    onCrossClick: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    lives: Int? = null,
) {
    var remainingMs by remember(indexInPool, deadlineMs) {
        mutableLongStateOf((deadlineMs - System.currentTimeMillis()).coerceAtLeast(0))
    }
    LaunchedEffect(indexInPool, deadlineMs, isPaused) {
        if (isPaused) return@LaunchedEffect
        while (isActive) {
            val remaining = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0)
            remainingMs = remaining
            if (remaining == 0L) {
                onTimeout()
                break
            }
            delay(TIMER_TICK_MS)
        }
    }

    val total = totalInPool.coerceAtLeast(1)
    val current = (indexInPool + 1).coerceIn(1, total)
    val remainingQuestions = (total - current + 1).coerceAtLeast(0)
    val urgent = remainingMs <= URGENT_THRESHOLD_MS
    val timerColor by animateColorAsState(
        targetValue = if (urgent) NoirDanger else NoirT1,
        label = "timer",
    )
    // Design: `noir-pulse 1s ease-in-out infinite` — opacity swings .5 ↔ 1 in the last stretch.
    val pulseTransition = rememberInfiniteTransition(label = "timerPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = TIMER_PULSE_MIN_ALPHA,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = TIMER_PULSE_HALF_PERIOD_MS),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "timerPulseAlpha",
    )

    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NoirIconButton(
                icon = NoirIcons.Close,
                contentDescription = stringResource(R.string.runner_cd_abort),
                onClick = onCrossClick,
            )
            if (lives != null) {
                // Gold heart on hard, red heart otherwise — never the Gem.
                HeaderPill(
                    icon = NoirIcons.Heart,
                    text = lives.toString(),
                    tint = if (isHard) NoirGold else NoirDanger,
                )
            }
            HeaderPill(icon = NoirIcons.Menu, text = remainingQuestions.toString(), tint = NoirT3)
            Spacer(Modifier.weight(1f))
            Text(
                text = remainingMs.asClock(),
                style = NoirType.timer.copy(color = timerColor, fontSize = 34.sp),
                modifier = Modifier.graphicsLayer { alpha = if (urgent) pulseAlpha else 1f },
            )
        }
        ProgressSegments(total = total, current = current, isHard = isHard)
    }
}

/** A count with its icon. The icon says what it is, the figure stays white. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun HeaderPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
) {
    Row(
        Modifier
            .clip(NoirShapePill)
            .background(NoirS1)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(text, style = NoirType.num.copy(fontSize = 12.5.sp))
    }
}

/**
 * One segment per question, so position is legible without a "4 / 12" to read.
 *
 * A bar would show the same fraction; segments also show how many are left, which is the number
 * that actually changes how somebody plays the last stretch.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ProgressSegments(
    total: Int,
    current: Int,
    isHard: Boolean,
) {
    val done = if (isHard) NoirDanger else LocalNoirAccent.current
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(NoirShapePill)
                    .background(if (index < current) done else NoirOutline),
            )
        }
    }
}

private const val URGENT_THRESHOLD_MS = 5_000L
private const val TIMER_PULSE_MIN_ALPHA = 0.5f
private const val TIMER_PULSE_HALF_PERIOD_MS = 500

/** m:ss, and never a bare second count — "0:07" reads as time, "7" reads as a score. */
private fun Long.asClock(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun QuestionProgressHeaderPreview() {
    SchoolQuizTheme {
        QuestionProgressHeader(
            indexInPool = 2,
            totalInPool = 10,
            deadlineMs = System.currentTimeMillis() + 30_000L,
            isPaused = false,
            isHard = false,
            onCrossClick = {},
            onTimeout = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun QuestionProgressHeaderHardPreview() {
    SchoolQuizTheme {
        QuestionProgressHeader(
            indexInPool = 0,
            totalInPool = 5,
            deadlineMs = System.currentTimeMillis() + 10_000L,
            isPaused = false,
            isHard = true,
            onCrossClick = {},
            onTimeout = {},
        )
    }
}
