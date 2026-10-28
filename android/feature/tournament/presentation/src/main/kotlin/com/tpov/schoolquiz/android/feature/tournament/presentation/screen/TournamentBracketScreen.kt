@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBracketComponent
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMatchRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentProgressView

/**
 * Сетка чемпионата (история 5.11): события и опубликованная сетка чтения — раунды с матчами,
 * решённые поимённо (победитель, техническое решение), ждущие парами; у завершённого события —
 * итоговые места замороженной публикации. Сетка неизменна: повторное чтение показывает то же.
 */
@Composable
fun TournamentBracketScreen(
    component: TournamentBracketComponent,
    onOpenMatch: (eventId: String, matchId: String) -> Unit,
    onOpenResults: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    val detail = state.detail
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.tournament_bracket_title),
                    style = NoirType.groupTitle,
                )
                if (detail != null) {
                    NoirButton(
                        text = stringResource(R.string.tournament_back),
                        onClick = component::onCloseEvent,
                        modifier = Modifier.testTag("tournament_bracket_back"),
                        style = NoirButtonStyle.Ghost,
                        fillWidth = false,
                    )
                } else {
                    NoirButton(
                        text = stringResource(R.string.tournament_refresh),
                        onClick = component::onRefreshClick,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("tournament_bracket_refresh"),
                        style = NoirButtonStyle.Ghost,
                        fillWidth = false,
                    )
                }
            }
        }
        if (detail == null) {
            if (!state.hasLoaded && !state.busy) {
                item {
                    NoirButton(
                        text = stringResource(R.string.tournament_state_not_loaded),
                        onClick = component::onRefreshClick,
                        modifier = Modifier.testTag("tournament_bracket_first_load"),
                        style = NoirButtonStyle.Ghost,
                        fillWidth = false,
                    )
                }
            }
            if (state.events.isEmpty() && state.hasLoaded) {
                item { Text(stringResource(R.string.tournament_empty), color = NoirT3) }
            }
            items(state.events, key = { it.eventId }) { event ->
                NoirGroup {
                    Column(
                        Modifier.padding(12.dp).testTag("tournament_bracket_event_" + event.eventId),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(statusText(event), color = NoirT3)
                        NoirButton(
                            text = stringResource(R.string.tournament_info),
                            onClick = { component.onOpenEvent(event.eventId) },
                            enabled = !state.busy,
                            modifier = Modifier.testTag("tournament_bracket_open_" + event.eventId),
                            style = NoirButtonStyle.Ghost,
                            fillWidth = false,
                        )
                    }
                }
            }
            if (state.canLoadMore) {
                item {
                    NoirButton(
                        text = stringResource(R.string.tournament_load_more),
                        onClick = component::onLoadMoreClick,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("tournament_bracket_load_more"),
                        style = NoirButtonStyle.Ghost,
                        fillWidth = false,
                    )
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(statusText(detail.event), color = NoirT3)
                    BracketBoard(
                        event = detail.event,
                        busy = state.busy,
                        onOpenMatch = onOpenMatch,
                        onOpenResults = onOpenResults,
                    )
                }
            }
        }
        if (state.busy) {
            item { CircularProgressIndicator(color = LocalNoirAccent.current) }
        }
        item { Text(noticeText(state.notice), color = NoirT3) }
    }
}

/** Опубликованная сетка: раунды и матчи чтения, места — только у завершённого события. */
@Composable
private fun BracketBoard(
    event: TournamentEventView,
    busy: Boolean,
    onOpenMatch: (eventId: String, matchId: String) -> Unit,
    onOpenResults: (eventId: String) -> Unit,
) {
    val progress = event.progress
    if (progress == null) {
        Text(stringResource(R.string.tournament_empty), color = NoirT3)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        event.matches.groupBy { it.stage }.forEach { (stage, matches) ->
            Column(
                Modifier.testTag("tournament_bracket_round_$stage"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.tournament_bracket_round, stage), style = NoirType.groupTitle)
                matches.forEach { match ->
                    MatchRow(
                        match = match,
                        progress = progress,
                        busy = busy,
                        onOpenMatch = { onOpenMatch(event.eventId, match.matchId) },
                    )
                }
            }
        }
        if (event.status == "FINISHED" && progress.placements.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.tournament_bracket_placements), style = NoirType.groupTitle)
                progress.placements.forEachIndexed { index, placement ->
                    Text(
                        stringResource(
                            R.string.tournament_bracket_placement,
                            placement.rank,
                            placement.uid,
                            placement.slot,
                        ),
                        Modifier.testTag("tournament_bracket_placement_$index"),
                        color = NoirT3,
                    )
                }
                NoirButton(
                    text = stringResource(R.string.tournament_bracket_results),
                    onClick = { onOpenResults(event.eventId) },
                    modifier = Modifier.testTag("tournament_bracket_results"),
                    fillWidth = false,
                )
            }
        }
    }
}

@Composable
private fun MatchRow(
    match: TournamentMatchRow,
    progress: TournamentProgressView,
    busy: Boolean,
    onOpenMatch: () -> Unit,
) {
    val resolved = progress.resolved.firstOrNull { it.matchId == match.matchId }
    val pending = progress.pending.firstOrNull { it.matchId == match.matchId }
    Column(Modifier.testTag("tournament_bracket_match_" + match.matchId)) {
        when {
            resolved != null -> {
                val label =
                    resolved.winnerUid?.let {
                        stringResource(R.string.tournament_bracket_match_resolved, match.matchId, it)
                    } ?: stringResource(R.string.tournament_bracket_match_technical, match.matchId)
                Text(label, color = NoirT3)
            }
            pending != null -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text =
                            pending.entrants
                                .map { it.uid }
                                .let { uids ->
                                    when (uids.size) {
                                        2 -> stringResource(R.string.tournament_bracket_match_pending, uids[0], uids[1])
                                        1 -> stringResource(R.string.tournament_bracket_match_solo, uids[0])
                                        else -> match.matchId
                                    }
                                },
                        color = NoirT3,
                    )
                    NoirButton(
                        text = stringResource(R.string.tournament_bracket_agree),
                        onClick = onOpenMatch,
                        enabled = !busy,
                        modifier = Modifier.testTag("tournament_bracket_agree_" + match.matchId),
                        style = NoirButtonStyle.Ghost,
                        fillWidth = false,
                    )
                }
            }
            else -> Text(match.matchId, color = NoirT3)
        }
    }
}
