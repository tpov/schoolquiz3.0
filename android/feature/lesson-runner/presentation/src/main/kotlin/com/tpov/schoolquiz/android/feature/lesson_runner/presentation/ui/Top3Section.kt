@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant

private const val RANK_SLOT_WIDTH_DP = 14
private const val AVATAR_SIZE_DP = 30
private const val ROW_MIN_HEIGHT_DP = 50
private const val CROWN_SIZE_DP = 15

/**
 * The leaderboard's crown, traced from the design: a filled three-point body over a base line,
 * both carried by the tint at the call site.
 */
private val CrownIcon: ImageVector =
    ImageVector.Builder(
        name = "RunnerCrown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData =
            PathData {
                moveTo(4f, 17.6f)
                lineTo(3f, 7.4f)
                lineTo(8f, 10.8f)
                lineTo(12f, 5.2f)
                lineTo(16f, 10.8f)
                lineTo(21f, 7.4f)
                lineTo(20f, 17.6f)
                close()
            },
        fill = SolidColor(Color.White),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.4f,
        strokeLineJoin = StrokeJoin.Round,
    ).addPath(
        pathData =
            PathData {
                moveTo(4f, 19.4f)
                lineTo(20f, 19.4f)
            },
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.4f,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

/**
 * Who set the bar this lesson.
 *
 * The leader is not a bigger version of everybody else — a gold band pinned to the top of the
 * list says "the number to beat" before a single digit is read. Places behind it stay ordinary
 * rows, because ordinary is exactly what second place is.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun Top3Section(
    top3: List<TopParticipant>,
    modifier: Modifier = Modifier,
) {
    if (top3.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        // A gold band bleeding to the right instead of a boxed header. The set this comes from
        // drops strokes: a section is announced by its colour running out of its own title, and
        // the ranking's colour is gold because first place is.
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        0f to NoirGold.copy(alpha = 0.20f),
                        TOP3_BAND_FADE to Color.Transparent,
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.runner_top3_title),
                style = NoirType.kicker.copy(fontSize = 11.sp, color = NoirGold),
            )
        }
        LeaderRow(participant = top3.first())
        top3.drop(1).forEachIndexed { index, participant ->
            RunnerUpRow(
                rank = index + RUNNER_UP_FIRST_RANK,
                participant = participant,
                showDivider = index < top3.size - RUNNER_UP_TRAILING_ROWS,
            )
        }
    }
}

/** Where the band's gold has faded out entirely. */
private const val TOP3_BAND_FADE = 0.8f

/** First place: the gold band. Crown instead of a number, everything on it gold. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun LeaderRow(
    participant: TopParticipant,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = NoirGold.copy(alpha = GOLD_BORDER_ALPHA))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(NoirGold.copy(alpha = GOLD_FILL_ALPHA))
                    .heightIn(min = ROW_MIN_HEIGHT_DP.dp)
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(RANK_SLOT_WIDTH_DP.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = CrownIcon,
                    contentDescription = null,
                    tint = NoirGold,
                    modifier = Modifier.size(CROWN_SIZE_DP.dp),
                )
            }
            AvatarRing(borderColor = NoirGold, borderWidth = 1.5.dp, fillColor = NoirGold.copy(alpha = 0.10f)) {
                ParticipantAvatar(
                    avatarUrl = participant.avatarUrl,
                    modifier = Modifier.size((AVATAR_SIZE_DP - 4).dp),
                )
            }
            ParticipantName(text = participant.nickname, color = NoirGold, modifier = Modifier.weight(1f))
            ParticipantPercent(percent = participant.percent, color = NoirGold)
        }
        HorizontalDivider(thickness = 1.dp, color = NoirGold.copy(alpha = GOLD_BORDER_ALPHA))
    }
}

/** A place behind the first: an ordinary row, separated by a hairline and nothing more. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RunnerUpRow(
    rank: Int,
    participant: TopParticipant,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_MIN_HEIGHT_DP.dp)
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(RANK_SLOT_WIDTH_DP.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "$rank",
                    style = NoirType.num.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = NoirT3,
                )
            }
            AvatarRing(borderColor = NoirOutline, borderWidth = 1.dp, fillColor = NoirS2) {
                ParticipantAvatar(
                    avatarUrl = participant.avatarUrl,
                    modifier = Modifier.size((AVATAR_SIZE_DP - 4).dp),
                )
            }
            ParticipantName(text = participant.nickname, color = NoirT1, modifier = Modifier.weight(1f))
            ParticipantPercent(percent = participant.percent, color = NoirT1)
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = NoirHair)
        }
    }
}

/**
 * The circle that holds an avatar.
 *
 * The ring does the ranking work — gold for the leader, quiet grey for everyone else — so the
 * picture inside can stay the same size all the way down the list.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun AvatarRing(
    borderColor: Color,
    borderWidth: Dp,
    fillColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(AVATAR_SIZE_DP.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ParticipantName(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = NoirType.rowTitle,
        color = color,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ParticipantPercent(
    percent: Int,
    color: Color,
) {
    Text(
        text = "$percent%",
        style = NoirType.num.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        color = color,
    )
}

private const val GOLD_FILL_ALPHA = 0.07f
private const val GOLD_BORDER_ALPHA = 0.28f
private const val RUNNER_UP_FIRST_RANK = 2
private const val RUNNER_UP_TRAILING_ROWS = 1

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
