package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.AuthorLifecycleComponent
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltySummary

/**
 * Авторская половина TZ06C: статусы собственных уроков и роялти. Вердикт независимой проверки
 * и полка приезжают с сервера — экран только показывает, без локального состояния.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun AuthorLifecycleScreen(
    component: AuthorLifecycleComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.qa_author_lifecycle_title), style = NoirType.groupTitle)
        when {
            state.isLoading -> CircularProgressIndicator()

            else -> {
                state.errorMessage?.let { message ->
                    Text(
                        messageText(message),
                        color = MaterialTheme.colorScheme.error,
                        style = NoirType.rowSub,
                    )
                }
                state.royalty?.let { royalty ->
                    RoyaltyCard(royalty)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(state.entries, key = { it.lessonId }) { entry ->
                        LifecycleEntryCard(
                            entry = entry,
                            selected = entry.lessonId == state.selectedLessonId,
                            onClick = { component.onLessonSelected(entry.lessonId) },
                        )
                    }
                    if (state.canLoadMore) {
                        item {
                            OutlinedButton(onClick = component::onLoadMoreClick, enabled = !state.isLoadingMore) {
                                Text(stringResource(R.string.qa_lifecycle_load_more))
                            }
                        }
                    }
                    if (state.selectedLessonId != null) {
                        item {
                            HistoryCard(
                                title = stringResource(R.string.qa_lifecycle_history_title),
                                history = state.history,
                                loading = state.historyIsLoading,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = component::onRefreshClick) {
                        Text(stringResource(R.string.qa_lifecycle_refresh))
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RoyaltyCard(royalty: LessonRoyaltySummary) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = NoirS1)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.qa_lifecycle_royalty_title), style = NoirType.groupTitle)
            Text(
                stringResource(R.string.qa_lifecycle_royalty_summary, royalty.totalCreditedNolics),
                style = NoirType.rowSub,
                color = NoirT3,
            )
        }
    }
}
