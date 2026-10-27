package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsMarkStateUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsQuestionPreviewUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsUiState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue

/**
 * Вкладка «Решения» (история 4.17, UX-22): строки семейcтва очереди проверки (64dp, wash-review,
 * radius-6 теги), фильтр «Без отметки / Верно / Неверно», «Логи» записи и статический
 * предпросмотр вопроса — без таймера и плат. Отказ по роли — авторская сторона: только
 * «Предложения» (пустая секция до историй 4.22–4.25).
 */
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun DecisionsView(
    state: DecisionsUiState,
    onFilterSelected: (ReviewDecisionFilter) -> Unit,
    onLoadMoreClick: () -> Unit,
    onEntrySelected: (String, String) -> Unit,
    onBackToListClick: () -> Unit,
    onMarkSelected: (ReviewDecisionMarkValue) -> Unit,
    onCommentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.accessRefused) {
            ProposalsOnlySection()
        } else {
            DecisionsFilters(
                selected = state.selectedFilter,
                onFilterSelected = onFilterSelected,
            )
            if (state.detail == null) {
                DecisionsList(
                    state = state,
                    onEntrySelected = onEntrySelected,
                    onLoadMoreClick = onLoadMoreClick,
                    modifier = Modifier.weight(1f),
                )
            } else {
                DecisionsDetail(
                    detail = state.detail,
                    onBackToListClick = onBackToListClick,
                    onMarkSelected = onMarkSelected,
                    onCommentChanged = onCommentChanged,
                    modifier = Modifier.weight(1f),
                )
            }
            state.errorMessage?.let {
                Text(text = it.resolveText(), color = NoirDanger)
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ProposalsOnlySection() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.qa_decisions_proposals_title),
            style = NoirType.groupTitle,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.qa_decisions_proposals_empty),
            color = NoirT3,
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DecisionsFilters(
    selected: ReviewDecisionFilter,
    onFilterSelected: (ReviewDecisionFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewDecisionFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label()) },
            )
        }
    }
}

@Composable
private fun ReviewDecisionFilter.label(): String =
    stringResource(
        when (this) {
            ReviewDecisionFilter.UNMARKED -> R.string.qa_decisions_filter_unmarked
            ReviewDecisionFilter.VALID -> R.string.qa_decisions_filter_valid
            ReviewDecisionFilter.INVALID -> R.string.qa_decisions_filter_invalid
        },
    )

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DecisionsList(
    state: DecisionsUiState,
    onEntrySelected: (String, String) -> Unit,
    onLoadMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading ->
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        state.entries.isEmpty() ->
            Text(text = stringResource(R.string.qa_decisions_empty), color = NoirT3, modifier = modifier)
        else ->
            LazyColumn(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.entries, key = { "${it.verdictId}:${it.questionId}" }) { item ->
                    DecisionsRowCard(item = item, onClick = { onEntrySelected(item.verdictId, item.questionId) })
                }
                if (state.hasMore) {
                    item(key = "load-more") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (state.loadingMore) {
                                CircularProgressIndicator()
                            } else {
                                TextButton(onClick = onLoadMoreClick) {
                                    Text(stringResource(R.string.qa_decisions_load_more))
                                }
                            }
                        }
                    }
                }
            }
    }
}

/** Строка очереди: 64dp, wash-review (NoirS1), radius-6 тег этапа и ✓/✗ вердикта (UX-22). */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DecisionsRowCard(
    item: DecisionsListItemUiState,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = NoirS1),
        modifier =
            Modifier.fillMaxWidth().heightIn(
                min = 64.dp,
            ).testTag("decisions-row-${item.verdictId}-${item.questionId}"),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text =
                        if (item.unreadable) {
                            stringResource(R.string.qa_decisions_unreadable)
                        } else {
                            item.questionText
                        },
                    style = NoirType.rowTitle,
                    maxLines = 2,
                )
                Text(
                    text =
                        listOf(item.questTitle, item.lessonTitle).filter { it.isNotBlank() }
                            .joinToString(" · ").ifBlank { stringResource(R.string.qa_decisions_unknown_submission) },
                    style = NoirType.rowSub,
                    color = NoirT3,
                )
            }
            DecisionsVerdictTag(stage = item.stage, pass = item.verdictPass)
            DecisionsMarkTag(value = item.markValue)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DecisionsVerdictTag(
    stage: String,
    pass: Boolean?,
) {
    val tag =
        when (pass) {
            true -> stringResource(R.string.qa_decisions_verdict_pass)
            false -> stringResource(R.string.qa_decisions_verdict_fail)
            null -> stage
        }
    Text(
        text = tag,
        style = NoirType.rowSub,
        color =
            when (pass) {
                true -> NoirSuccess
                false -> NoirDanger
                null -> NoirT3
            },
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DecisionsMarkTag(value: ReviewDecisionMarkValue?) {
    when (value) {
        ReviewDecisionMarkValue.VALID ->
            Text(
                stringResource(R.string.qa_decisions_mark_valid),
                color = NoirSuccess,
                style = NoirType.rowSub,
            )
        ReviewDecisionMarkValue.INVALID ->
            Text(
                stringResource(R.string.qa_decisions_mark_invalid),
                color = NoirDanger,
                style = NoirType.rowSub,
            )
        null -> Unit
    }
}

@Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")
@Composable
private fun DecisionsDetail(
    detail: DecisionsDetailUiState,
    onBackToListClick: () -> Unit,
    onMarkSelected: (ReviewDecisionMarkValue) -> Unit,
    onCommentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBackToListClick) {
            Text(stringResource(R.string.qa_decisions_back_to_list))
        }
        if (detail.unreadable) {
            Text(
                text = stringResource(R.string.qa_decisions_unreadable_detail),
                color = NoirDanger,
            )
        } else {
            Text(text = detail.questTitle, style = NoirType.groupTitle, fontWeight = FontWeight.SemiBold)
            Text(
                text = listOf(detail.lessonTitle, detail.stage).filter { it.isNotBlank() }.joinToString(" · "),
                style = NoirType.rowSub,
                color = NoirT3,
            )
            DecisionsQuestionPreview(detail.preview)
            // «Логи» (UX-22): основание модели, источники, попытка, модель/промпт/ревизия —
            // всё из неизменяемой записи вердикта.
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = NoirS1),
                modifier = Modifier.fillMaxWidth().testTag("decisions-logs-${detail.verdictId}"),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.qa_decisions_logs_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.qa_decisions_logs_verdict, detail.reason),
                        style = NoirType.rowSub,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.qa_decisions_logs_meta,
                                detail.model,
                                detail.promptVersion,
                                detail.attempt,
                                detail.revision,
                            ),
                        style = NoirType.rowSub,
                        color = NoirT3,
                    )
                    Text(text = detail.rationale, style = NoirType.rowSub)
                    detail.sources.forEach { source ->
                        Text(text = source, style = NoirType.rowSub, color = NoirT3)
                    }
                }
            }
            DecisionsMarkControls(detail, onMarkSelected, onCommentChanged)
        }
    }
}

/** Статический предпросмотр вопроса (UX-22): без таймера, без плат — только текст и варианты. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DecisionsQuestionPreview(preview: DecisionsQuestionPreviewUiState?) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = NoirS1),
        modifier = Modifier.fillMaxWidth().testTag("decisions-preview"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.qa_decisions_preview_title), fontWeight = FontWeight.SemiBold)
            when {
                preview == null -> Text(stringResource(R.string.qa_decisions_preview_missing), color = NoirT3)
                preview.parseFailed -> Text(stringResource(R.string.qa_decisions_preview_broken), color = NoirDanger)
                else -> {
                    Text(preview.text, style = NoirType.rowTitle)
                    preview.options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (option.correct) "✓" else "·",
                                color = if (option.correct) NoirSuccess else NoirT3,
                            )
                            Text(option.text, style = NoirType.rowSub)
                        }
                    }
                    preview.answerKeyLine?.let { key ->
                        Text(
                            text = stringResource(R.string.qa_decisions_preview_key, key),
                            style = NoirType.rowSub,
                            color = LocalNoirAccent.current,
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DecisionsMarkControls(
    detail: DecisionsDetailUiState,
    onMarkSelected: (ReviewDecisionMarkValue) -> Unit,
    onCommentChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onMarkSelected(ReviewDecisionMarkValue.VALID) },
                enabled = detail.markState != DecisionsMarkStateUi.SAVING,
                modifier = Modifier.testTag("decisions-mark-valid"),
            ) {
                Text(stringResource(R.string.qa_decisions_mark_valid))
            }
            OutlinedButton(
                onClick = { onMarkSelected(ReviewDecisionMarkValue.INVALID) },
                enabled = detail.markState != DecisionsMarkStateUi.SAVING,
                modifier = Modifier.testTag("decisions-mark-invalid"),
            ) {
                Text(stringResource(R.string.qa_decisions_mark_invalid))
            }
            when (detail.markState) {
                DecisionsMarkStateUi.SAVING -> Text(stringResource(R.string.qa_decisions_mark_saving), color = NoirT3)
                DecisionsMarkStateUi.SAVED ->
                    Text(
                        stringResource(R.string.qa_decisions_mark_saved),
                        color = LocalNoirAccent.current,
                    )
                DecisionsMarkStateUi.NONE -> Unit
            }
        }
        OutlinedTextField(
            value = detail.commentDraft,
            onValueChange = onCommentChanged,
            label = { Text(stringResource(R.string.qa_decisions_comment_hint)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).testTag("decisions-comment"),
        )
    }
}
