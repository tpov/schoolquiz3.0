package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.InboxComponent
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxItem

/**
 * Общий inbox (AD-20): приватные отчёты об ошибках и публичные комментарии к своим урокам одним
 * списком. Круг читателей каждой строки назван словами — «Приватный отчёт» против «Комментарий»;
 * экран ничего не расширяет и не приватизирует, право решает серверная вью.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun InboxScreen(
    component: InboxComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.qa_inbox_title), style = NoirType.groupTitle)
            OutlinedButton(onClick = component::onRefreshClick) {
                Text(stringResource(R.string.qa_inbox_refresh))
            }
        }
        when {
            state.isLoading -> CircularProgressIndicator()

            else -> {
                state.errorMessage?.let { message ->
                    Text(
                        message.resolveText(),
                        color = MaterialTheme.colorScheme.error,
                        style = NoirType.rowSub,
                    )
                }
                if (state.entries.isEmpty()) {
                    Text(
                        stringResource(R.string.qa_inbox_empty),
                        style = NoirType.rowSub,
                        color = NoirT3,
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.entries, key = { "${it.kindMarker()}-${it.id}" }) { entry ->
                        InboxEntryCard(entry)
                    }
                    if (state.canLoadMore) {
                        item {
                            OutlinedButton(onClick = component::onLoadMoreClick, enabled = !state.isLoadingMore) {
                                Text(stringResource(R.string.qa_lifecycle_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun InboxEntryCard(entry: InboxItem) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = NoirS1),
        modifier = Modifier.fillMaxWidth().testTag("inbox-entry-${entry.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (entry) {
                                is InboxItem.PrivateErrorReport ->
                                    stringResource(R.string.qa_inbox_kind_private_report)
                                is InboxItem.LessonComment ->
                                    stringResource(R.string.qa_inbox_kind_comment)
                            },
                        )
                    },
                )
                AssistChip(onClick = {}, label = { Text(entry.lessonId) })
            }
            Text(entry.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                when (entry) {
                    is InboxItem.PrivateErrorReport ->
                        stringResource(R.string.qa_inbox_readers_author_only)
                    is InboxItem.LessonComment ->
                        stringResource(R.string.qa_inbox_readers_public, entry.authorNickname)
                },
                style = NoirType.rowSub,
                color = NoirT3,
            )
        }
    }
}

private fun InboxItem.kindMarker(): String =
    when (this) {
        is InboxItem.PrivateErrorReport -> "report"
        is InboxItem.LessonComment -> "comment"
    }
