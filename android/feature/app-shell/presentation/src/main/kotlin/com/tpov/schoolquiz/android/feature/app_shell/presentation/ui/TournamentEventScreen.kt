package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirInk
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R

internal data class TournamentEventUi(
    val title: String,
    val stageLabel: String,
)

internal data class TournamentEventActions(
    val canManagePublicShelves: Boolean,
    val onStartClick: () -> Unit,
    val onLeaderboardClick: () -> Unit,
    val onLessonsClick: () -> Unit,
    val onParticipantsClick: () -> Unit,
    val onAddLessonsClick: () -> Unit,
)

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun TournamentEventScreen(
    model: TournamentEventUi,
    actions: TournamentEventActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NoirGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = model.title,
                            style = NoirType.appbar.copy(fontSize = 20.sp),
                            color = NoirT1,
                        )
                        Text(
                            text = model.stageLabel,
                            style = NoirType.rowSub,
                            color = NoirT3,
                        )
                        Spacer(Modifier.height(4.dp))
                        TournamentActionButton(
                            text = stringResource(R.string.tournament_start),
                            icon = Icons.Default.PlayArrow,
                            primary = true,
                            onClick = actions.onStartClick,
                        )
                        TournamentActionButton(
                            text = stringResource(R.string.config_leaderboard),
                            icon = Icons.Default.Leaderboard,
                            onClick = actions.onLeaderboardClick,
                        )
                        TournamentActionButton(
                            text = stringResource(R.string.tournament_lessons_list),
                            icon = Icons.Default.Book,
                            onClick = actions.onLessonsClick,
                        )
                        TournamentActionButton(
                            text = stringResource(R.string.tournament_participants_list),
                            icon = Icons.Default.People,
                            onClick = actions.onParticipantsClick,
                        )
                    }
                }
            }
        }
        if (actions.canManagePublicShelves) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(18.dp)
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LocalNoirAccent.current)
                        .clickable(onClick = actions.onAddLessonsClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_lesson),
                    tint = NoirInk,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
    if (primary) {
        NoirButton(
            text = text,
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        NoirButton(
            text = text,
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            style = NoirButtonStyle.Ghost,
        )
    }
}
