@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirViolet
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun Top3Section(
    top3: List<TopParticipant>,
    modifier: Modifier = Modifier,
) {
    if (top3.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = runnerGroupSurfaceColor(),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, runnerLightBorderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Лучшие участники",
                modifier = Modifier.fillMaxWidth(),
                style = NoirType.groupTitle,
                color = NoirT1,
                textAlign = TextAlign.Center,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top3.forEachIndexed { index, participant ->
                    TopParticipantCard(index = index, participant = participant)
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TopParticipantCard(
    index: Int,
    participant: TopParticipant,
    modifier: Modifier = Modifier,
) {
    val rankColor =
        if (index == 0) {
            NoirViolet
        } else {
            LocalNoirAccent.current
        }
    RunnerDesignCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = rankColor,
        containerColor = runnerDeepSurfaceColor(),
        borderColor = Color.Transparent,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = rankColor.copy(alpha = 0.2f),
                contentColor = rankColor,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                    Text(
                        text = "${index + 1}",
                        style = NoirType.button,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            ParticipantAvatar(
                avatarUrl = participant.avatarUrl,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = participant.nickname,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = NoirType.rowTitle,
                color = if (index == 0) rankColor else NoirT1,
            )
            ParticipantPercent(
                percent = participant.percent,
                color = rankColor,
                highlighted = index == 0,
                modifier = Modifier.padding(end = if (index == 0) 0.dp else 8.dp),
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ParticipantPercent(
    percent: Int,
    color: androidx.compose.ui.graphics.Color,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (highlighted) {
        RunnerDesignChip(
            text = "$percent%",
            color = color,
            modifier = modifier,
        )
    } else {
        Text(
            text = "$percent%",
            modifier = modifier,
            style = NoirType.groupTitle,
            color = color,
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun Top3SectionPreview() {
    SchoolQuizTheme {
        Top3Section(
            top3 =
                listOf(
                    TopParticipant("Alice", null, 95),
                    TopParticipant("Bob", null, 88),
                    TopParticipant("Carol", null, 72),
                ),
        )
    }
}
