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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBrowserComponent
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRankingView

/**
 * Маршрут просмотра (история 5.10): страница событий с курсором и деталь чтения события —
 * квалификация, призы, эскроу и опубликованный рейтинг (место авторитетно, проценты — витрина).
 */
@Composable
fun TournamentBrowserScreen(
    component: TournamentBrowserComponent,
    onEnterEvent: (TournamentEventView, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    val backLabel = stringResource(R.string.tournament_accessibility_back)
    val infoLabel = stringResource(R.string.tournament_accessibility_info)
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
                    stringResource(
                        if (detail == null) {
                            R.string.tournament_browser_title
                        } else {
                            R.string.tournament_browser_detail_title
                        },
                    ),
                    style = NoirType.groupTitle,
                )
                if (detail != null) {
                    OutlinedButton(
                        onClick = component::onCloseEvent,
                        modifier =
                            Modifier
                                .testTag("tournament_browser_back")
                                .semantics { contentDescription = backLabel },
                    ) {
                        Text(stringResource(R.string.tournament_back))
                    }
                } else {
                    OutlinedButton(
                        onClick = component::onRefreshClick,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("tournament_browser_refresh"),
                    ) {
                        Text(stringResource(R.string.tournament_refresh))
                    }
                }
            }
        }
        if (detail == null) {
            if (!state.hasLoaded && !state.busy) {
                item {
                    OutlinedButton(
                        onClick = component::onRefreshClick,
                        modifier = Modifier.testTag("tournament_browser_first_load"),
                    ) {
                        Text(stringResource(R.string.tournament_state_not_loaded))
                    }
                }
            }
            if (state.events.isEmpty() && state.hasLoaded) {
                item { Text(stringResource(R.string.tournament_empty), color = NoirT3) }
            }
            items(state.events, key = { it.eventId }) { event ->
                NoirGroup {
                    Column(
                        modifier = Modifier.padding(12.dp).testTag("tournament_browser_event_" + event.eventId),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(statusText(event), color = NoirT3)
                        Text(
                            stringResource(R.string.tournament_browser_rounds, event.qualification.roundCount),
                            color = NoirT3,
                        )
                        OutlinedButton(
                            onClick = { component.onOpenEvent(event.eventId) },
                            enabled = !state.busy,
                            modifier =
                                Modifier
                                    .testTag("tournament_browser_open_" + event.eventId)
                                    .semantics { contentDescription = infoLabel },
                        ) {
                            Text(stringResource(R.string.tournament_info))
                        }
                    }
                }
            }
            if (state.canLoadMore) {
                item {
                    OutlinedButton(
                        onClick = component::onLoadMoreClick,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("tournament_browser_load_more"),
                    ) {
                        Text(stringResource(R.string.tournament_load_more))
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(statusText(detail.event), color = NoirT3)
                    Text(
                        stringResource(R.string.tournament_browser_rounds, detail.event.qualification.roundCount),
                        color = NoirT3,
                    )
                    Text(stringResource(R.string.tournament_browser_prizes_title), style = NoirType.groupTitle)
                    detail.event.prizes.rows.forEachIndexed { index, row ->
                        Text(
                            stringResource(R.string.tournament_browser_prize_row, row.place, row.amountBoxes),
                            Modifier.testTag("tournament_detail_prize_row_$index"),
                            color = NoirT3,
                        )
                    }
                    Button(
                        onClick = { onEnterEvent(detail.event, detail.serverNowMs) },
                        modifier = Modifier.testTag("tournament_browser_enter"),
                    ) {
                        Text(stringResource(R.string.tournament_browser_enter))
                    }
                }
            }
            item { RankingBoard(detail.event.ranking) }
        }
        if (state.busy) {
            item { CircularProgressIndicator() }
        }
        item { Text(noticeText(state.notice), color = NoirT3) }
    }
}

/** Статус события словами серверного статуса; отменённый называет свою причину. */
@Composable
internal fun statusText(event: TournamentEventView): String =
    when (event.status) {
        "QUALIFICATION" -> stringResource(R.string.tournament_browser_status_open)
        "BRACKET" -> stringResource(R.string.tournament_browser_status_bracket)
        "FINISHED" -> stringResource(R.string.tournament_browser_status_finished)
        "CANCELLED" -> stringResource(R.string.tournament_browser_status_cancelled, event.cancelReason ?: "")
        else -> event.status
    }

/**
 * Рейтинг квалификации: место — авторитет сервера (им заморожена сетка); ratingPercent — тоже
 * официальный; averagePercent — только для сведения. Пустых процентов не бывает вместо нуля.
 */
@Composable
internal fun RankingBoard(
    ranking: TournamentRankingView?,
    modifier: Modifier = Modifier,
) {
    NoirGroup(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.tournament_ranking_title), style = NoirType.groupTitle)
            if (ranking == null) {
                Text(
                    stringResource(R.string.tournament_ranking_empty),
                    Modifier.testTag("tournament_ranking_empty"),
                    color = NoirT3,
                )
                return@NoirGroup
            }
            Text(
                stringResource(R.string.tournament_ranking_total, ranking.totalRanked),
                color = NoirT3,
            )
            ranking.rows.forEachIndexed { index, row ->
                Column(Modifier.testTag("tournament_ranking_row_$index")) {
                    Text(
                        stringResource(R.string.tournament_ranking_place, row.place) + " · " + row.uid,
                        color = NoirT3,
                    )
                    Text(
                        stringResource(R.string.tournament_rating_authoritative, percentOrDash(row.ratingPercent)),
                        Modifier.testTag("tournament_ranking_rating_$index"),
                        color = NoirT3,
                    )
                    Text(
                        stringResource(R.string.tournament_average_display_only, percentOrDash(row.averagePercent)),
                        Modifier.testTag("tournament_ranking_average_$index"),
                        color = NoirT3,
                    )
                }
            }
        }
    }
}
