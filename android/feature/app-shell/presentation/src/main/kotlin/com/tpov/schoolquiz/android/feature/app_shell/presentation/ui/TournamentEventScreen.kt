package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignCard
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignDeepSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor
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
                SchoolQuizDesignCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = schoolQuizDesignDeepSurfaceColor(),
                    borderColor = schoolQuizDesignLightBorderColor(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = model.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = model.stageLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
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
            FloatingActionButton(
                onClick = actions.onAddLessonsClick,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_lesson),
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
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            content = { content() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            content = { content() },
        )
    }
}
