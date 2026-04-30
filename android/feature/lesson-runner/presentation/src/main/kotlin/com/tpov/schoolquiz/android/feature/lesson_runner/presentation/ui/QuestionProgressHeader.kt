package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TIMER_TICK_MS = 250L

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

    val accent = runnerModeAccent(isHard)
    val total = totalInPool.coerceAtLeast(1)
    val current = (indexInPool + 1).coerceIn(1, total)
    val progress = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        RunnerDesignCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = accent,
            elevated = true,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CrossButton(onClick = onCrossClick)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Вопрос",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        )
                        Text(
                            text = "$current / $totalInPool",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    RunnerDesignChip(
                        text = "${(remainingMs + 999L) / 1000L}s",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = accent,
                    trackColor = runnerNeutralBorderColor(),
                )
            }
        }
    }
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
