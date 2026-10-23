package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import com.tpov.schoolquiz.android.core.designsystem.components.BrandPrimaryButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonEarningsComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HistoryFilter
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.OwnHistoryDayGroup
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * «Начисления» авторского урока (UX-20/AD-30, история 8.6): те же дата-группы, фильтры и карты
 * записи, что у общей истории, но чтение — lesson-scoped авторская проекция. Pending здесь не
 * бывает (форма едина), ресурсы построчно и никогда не суммируются.
 */
@Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")
@Composable
fun LessonEarningsScreen(
    component: LessonEarningsComponent,
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
        Text(text = stringResource(R.string.lesson_earnings_title), style = NoirType.groupTitle, color = NoirT1)
        Text(text = component.title, style = NoirType.rowSub, color = NoirT2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filter == HistoryFilter.ALL,
                onClick = { component.onFilterChanged(HistoryFilter.ALL) },
                label = { Text(stringResource(R.string.lesson_earnings_filter_all)) },
            )
            FilterChip(
                selected = state.filter == HistoryFilter.INCOME,
                onClick = { component.onFilterChanged(HistoryFilter.INCOME) },
                label = { Text(stringResource(R.string.lesson_earnings_filter_income)) },
            )
            FilterChip(
                selected = state.filter == HistoryFilter.EXPENSE,
                onClick = { component.onFilterChanged(HistoryFilter.EXPENSE) },
                label = { Text(stringResource(R.string.lesson_earnings_filter_expense)) },
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.errorMessage -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.lesson_earnings_error),
                    color = NoirDanger,
                    style = NoirType.rowSub,
                )
                TextButton(onClick = component::onRetryClick) {
                    Text(stringResource(R.string.lesson_earnings_refresh))
                }
            }

            else -> {
                val groups = filteredGroups(state)
                if (groups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.lesson_earnings_empty),
                        style = NoirType.rowSub,
                        color = NoirT2,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        groups.forEach { group ->
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
                                    text = stringResource(R.string.lesson_earnings_load_more),
                                    onClick = component::onLoadMoreClick,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else if (state.isLoadingMore) {
                            item(key = "loading-more") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Фильтр применяется над готовыми группами; группировка и порядок сохраняются (UX-20). */
private fun filteredGroups(state: com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.OwnHistoryUiState): List<OwnHistoryDayGroup> =
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
    val date = Instant.fromEpochMilliseconds(group.dayStartMs).toLocalDateTime(timeZone()).date
    return stringResource(R.string.lesson_earnings_day, date.dayOfMonth, date.monthNumber, date.year)
}
