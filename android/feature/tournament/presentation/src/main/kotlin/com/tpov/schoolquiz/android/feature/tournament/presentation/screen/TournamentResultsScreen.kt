@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentResultsComponent

/**
 * Итоги чемпионата (история 5.11): итоговые места замороженной публикации и правда эскроу —
 * зарезервировано, осталось, выплачено, возвращено; «осталось» называет сервер, клиент его
 * не вычисляет и не дописывает. Квитанции о выплатах остаются серверным фактом.
 */
@Composable
fun TournamentResultsScreen(
    component: TournamentResultsComponent,
    onBack: () -> Unit,
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
                Text(stringResource(R.string.tournament_results_title), style = NoirType.groupTitle)
                OutlinedButton(
                    onClick = {
                        component.onClose()
                        onBack()
                    },
                    modifier = Modifier.testTag("tournament_results_back"),
                ) {
                    Text(stringResource(R.string.tournament_back))
                }
            }
        }
        if (detail == null) {
            item { Text(stringResource(R.string.tournament_empty), color = NoirT3) }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(statusText(detail.event), color = NoirT3)
                    Text(stringResource(R.string.tournament_results_placements), style = NoirType.groupTitle)
                    detail.event.progress?.placements?.forEachIndexed { index, placement ->
                        val technical = placement.technicalForfeit == true
                        Text(
                            stringResource(
                                if (technical) {
                                    R.string.tournament_results_placement_technical
                                } else {
                                    R.string.tournament_results_placement
                                },
                                placement.rank,
                                placement.uid,
                                placement.slot,
                            ),
                            Modifier.testTag("tournament_results_placement_$index"),
                            color = NoirT3,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val escrow = detail.event.escrow
                    Text(stringResource(R.string.tournament_results_escrow_title), style = NoirType.groupTitle)
                    Text(
                        stringResource(R.string.tournament_results_escrow_reserved, escrow.reservedBoxes),
                        Modifier.testTag("tournament_results_escrow_reserved"),
                        color = NoirT3,
                    )
                    Text(
                        stringResource(R.string.tournament_results_escrow_remaining, escrow.remainingBoxes),
                        Modifier.testTag("tournament_results_escrow_remaining"),
                        color = NoirT3,
                    )
                    Text(
                        stringResource(R.string.tournament_results_escrow_paid, escrow.paidBoxes),
                        Modifier.testTag("tournament_results_escrow_paid"),
                        color = NoirT3,
                    )
                    Text(
                        stringResource(R.string.tournament_results_escrow_returned, escrow.returnedBoxes),
                        Modifier.testTag("tournament_results_escrow_returned"),
                        color = NoirT3,
                    )
                }
            }
        }
        if (state.busy) {
            item { CircularProgressIndicator() }
        }
        item { Text(noticeText(state.notice), color = NoirT3) }
    }
}
