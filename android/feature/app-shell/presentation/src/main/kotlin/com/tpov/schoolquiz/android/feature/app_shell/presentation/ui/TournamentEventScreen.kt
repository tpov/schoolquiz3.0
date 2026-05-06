package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignCard
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignDeepSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor

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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
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
                        text = "Старт",
                        icon = Icons.Default.PlayArrow,
                        primary = true,
                        onClick = actions.onStartClick,
                    )
                    TournamentActionButton(
                        text = "Лидерборд",
                        icon = Icons.Default.Leaderboard,
                        onClick = actions.onLeaderboardClick,
                    )
                    TournamentActionButton(
                        text = "Список уроков",
                        icon = Icons.Default.Book,
                        onClick = actions.onLessonsClick,
                    )
                    TournamentActionButton(
                        text = "Список участников",
                        icon = Icons.Default.People,
                        onClick = actions.onParticipantsClick,
                    )
                    if (actions.canManagePublicShelves) {
                        TournamentActionButton(
                            text = "Добавить урок",
                            icon = Icons.Default.Add,
                            onClick = actions.onAddLessonsClick,
                        )
                    }
                }
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
