package com.tpov.schoolquiz.android.feature.app_shell.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.components.BrandPrimaryButton
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.OwnHistoryComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen.HistoryRecordCard
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HistoryFilter
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.OwnHistoryDayGroup
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.OwnHistoryUiState
import com.tpov.schoolquiz.shared.feature.economy.domain.model.OperationHistoryRecord
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * «История операций» (UX-20/AD-30, история 8.6): группы по дате, фильтры Все/Начисления/
 * Расходы, раскрытие записи. Записи — построчно по ресурсу: разные ресурсы никогда не
 * суммируются; pending виден отдельным списком и доходом не считается.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")
@Composable
fun OperationHistoryScreen(
    component: OwnHistoryComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = NoirType.groupTitle,
            color = NoirT1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filter == HistoryFilter.ALL,
                onClick = { component.onFilterChanged(HistoryFilter.ALL) },
                label = { Text(stringResource(R.string.history_filter_all)) },
            )
            FilterChip(
                selected = state.filter == HistoryFilter.INCOME,
                onClick = { component.onFilterChanged(HistoryFilter.INCOME) },
                label = { Text(stringResource(R.string.history_filter_income)) },
            )
            FilterChip(
                selected = state.filter == HistoryFilter.EXPENSE,
                onClick = { component.onFilterChanged(HistoryFilter.EXPENSE) },
                label = { Text(stringResource(R.string.history_filter_expense)) },
            )
        }

        when {
            state.isLoading -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }

            state.errorMessage -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_error),
                    color = NoirDanger,
                    style = NoirType.rowSub,
                )
                TextButton(onClick = component::onRetryClick) {
                    Text(stringResource(R.string.history_retry))
                }
            }

            state.isEmpty ->
                Text(
                    text = stringResource(R.string.history_empty),
                    style = NoirType.rowSub,
                    color = NoirT2,
                )

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.pending.isNotEmpty()) {
                        item(key = "pending") {
                            BrandCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(R.string.history_pending_title),
                                        style = NoirType.rowSub,
                                        color = NoirT1,
                                    )
                                    state.pending.forEach { row ->
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.history_pending_row,
                                                    row.heldAmount,
                                                    row.currency,
                                                ),
                                            style = NoirType.rowSub,
                                            color = NoirT2,
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.history_pending_hint),
                                        style = NoirType.rowSub,
                                        color = NoirT2,
                                    )
                                }
                            }
                        }
                    }
                    filteredGroups(state).forEach { group ->
                        item(key = group.dayStartMs) {
                            Text(
                                text = formatDay(group, timeZone = component.timeZone),
                                style = NoirType.rowSub,
                                color = NoirT2,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        items(group.entries, key = { it.id }) { record ->
                            HistoryRecordCard(record)
                        }
                    }
                    if (state.canLoadMore) {
                        item(key = "load-more") {
                            BrandPrimaryButton(
                                text = stringResource(R.string.history_load_more),
                                onClick = component::onLoadMoreClick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (state.isLoadingMore) {
                        item(key = "loading-more") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Фильтр применяется над готовыми группами, группировка и порядок сохраняются (UX-20). */
private fun filteredGroups(state: OwnHistoryUiState): List<OwnHistoryDayGroup> =
    state.groups
        .map { group ->
            OwnHistoryDayGroup(
                dayStartMs = group.dayStartMs,
                entries =
                    group.entries.filter { record ->
                        when (state.filter) {
                            HistoryFilter.ALL -> true
                            HistoryFilter.INCOME -> record.isIncome
                            HistoryFilter.EXPENSE -> !record.isIncome
                        }
                    },
            )
        }
        .filter { it.entries.isNotEmpty() }


@Composable
private fun formatDay(
    group: OwnHistoryDayGroup,
    timeZone: () -> TimeZone,
): String {
    // Группа и заголовок дня читают один и тот же пояс, что и группировка компонента (UX-20).
    val date = Instant.fromEpochMilliseconds(group.dayStartMs).toLocalDateTime(timeZone()).date
    return stringResource(R.string.history_day, date.dayOfMonth, date.monthNumber, date.year)
}
