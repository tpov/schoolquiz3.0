package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R

private const val MIN_PERCENT_VALUE = 0
private const val MAX_PERCENT_VALUE = 100

internal data class TournamentLeaderboardUi(
    val title: String,
    val stageLabel: String,
    val qualificationRule: String,
    val currentUserNickname: String,
    val currentUserPercent: Int?,
    val standings: List<TournamentStandingUi>,
    val isLoading: Boolean,
    val errorMessage: String?,
)

internal data class TournamentStandingUi(
    val nickname: String,
    val percent: Int,
    val attempts: Int,
)

internal data class TournamentParticipantsUi(
    val title: String,
    val stageLabel: String,
    val participants: List<TournamentParticipantUi>,
    val isLoading: Boolean,
    val errorMessage: String?,
)

internal data class TournamentParticipantUi(
    val nickname: String,
    val status: String,
    val attempts: Int,
)

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun TournamentLeaderboardScreen(
    model: TournamentLeaderboardUi,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TournamentHeaderCard(
                icon = Icons.Default.EmojiEvents,
                title = stringResource(R.string.config_leaderboard),
                subtitle = model.title,
                detail = model.stageLabel,
            )
        }
        item {
            TournamentMetricCard(
                title = stringResource(R.string.tournament_my_result),
                value =
                    model.currentUserPercent?.let { "$it%" }
                        ?: stringResource(R.string.tournament_no_attempt),
                subtitle = model.currentUserNickname,
            )
        }
        item {
            TournamentMetricCard(
                title = stringResource(R.string.tournament_qualification_rule),
                value = model.qualificationRule,
                subtitle = stringResource(R.string.tournament_updates_after_sync),
            )
        }
        if (model.isLoading) {
            item {
                TournamentLoadingCard()
            }
        }
        model.errorMessage?.let { message ->
            item {
                TournamentEmptyCard(
                    title = stringResource(R.string.tournament_error_update_table),
                    subtitle = message,
                )
            }
        }
        if (model.standings.isEmpty()) {
            item {
                TournamentEmptyCard(
                    title = stringResource(R.string.tournament_empty_results),
                    subtitle = stringResource(R.string.tournament_empty_results_hint),
                )
            }
        } else {
            itemsIndexed(model.standings) { index, standing ->
                TournamentStandingRow(rank = index + 1, standing = standing)
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun TournamentParticipantsScreen(
    model: TournamentParticipantsUi,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TournamentHeaderCard(
                icon = Icons.Default.Groups,
                title = stringResource(R.string.tournament_participants_title),
                subtitle = model.title,
                detail = model.stageLabel,
            )
        }
        item {
            TournamentMetricCard(
                title = stringResource(R.string.tournament_participants_count),
                value = model.participants.size.toString(),
                subtitle = stringResource(R.string.tournament_server_list),
            )
        }
        if (model.isLoading) {
            item {
                TournamentLoadingCard()
            }
        }
        model.errorMessage?.let { message ->
            item {
                TournamentEmptyCard(
                    title = stringResource(R.string.tournament_error_update_participants),
                    subtitle = message,
                )
            }
        }
        itemsIndexed(model.participants) { _, participant ->
            TournamentParticipantRow(participant = participant)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentHeaderCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    detail: String,
) {
    NoirGroup {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalNoirAccent.current,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = NoirType.groupTitle,
                    color = NoirT1,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = NoirType.rowSub,
                    color = NoirT2,
                )
                Text(
                    text = detail,
                    style = NoirType.kicker,
                    color = NoirT3,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentLoadingCard() {
    NoirGroup {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.tournament_loading),
                style = NoirType.rowTitle,
                color = NoirT1,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LocalNoirAccent.current)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentMetricCard(
    title: String,
    value: String,
    subtitle: String,
) {
    NoirGroup {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = NoirType.kicker,
                color = NoirT3,
            )
            Text(
                text = value,
                style = NoirType.rowTitle,
                color = NoirT1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = NoirType.kicker,
                color = NoirT3,
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentEmptyCard(
    title: String,
    subtitle: String,
) {
    NoirGroup {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = LocalNoirAccent.current,
            )
            Text(
                text = title,
                style = NoirType.rowTitle,
                color = NoirT1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = NoirType.rowSub,
                color = NoirT3,
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentStandingRow(
    rank: Int,
    standing: TournamentStandingUi,
) {
    NoirGroup {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$rank. ${standing.nickname}",
                    style = NoirType.rowTitle,
                    color = NoirT1,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${standing.percent}%",
                    style = NoirType.rowTitle,
                    color = LocalNoirAccent.current,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                color = LocalNoirAccent.current,
                progress = {
                    standing.percent.coerceIn(MIN_PERCENT_VALUE, MAX_PERCENT_VALUE) / MAX_PERCENT_VALUE.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.tournament_attempts_count, standing.attempts),
                style = NoirType.kicker,
                color = NoirT3,
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentParticipantRow(participant: TournamentParticipantUi) {
    NoirGroup {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = LocalNoirAccent.current,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = participant.nickname,
                    style = NoirType.rowTitle,
                    color = NoirT1,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = participant.status,
                    style = NoirType.rowSub,
                    color = NoirT2,
                )
                Text(
                    text = stringResource(R.string.tournament_attempts_count, participant.attempts),
                    style = NoirType.kicker,
                    color = NoirT3,
                )
            }
        }
    }
}
