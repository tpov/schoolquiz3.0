package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassFill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsMarkStateUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsQuestionPreviewUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsSurfaceUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsUiState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalRow
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
    onSurfaceSelected: (DecisionsSurfaceUi) -> Unit,
    onProposalsBoxSelected: (QuestProposalBox) -> Unit,
    onLoadMoreProposalsClick: () -> Unit,
    onProposalSelected: (String) -> Unit,
    onBackToProposalsListClick: () -> Unit,
    onProposalDecision: (Boolean) -> Unit,
    onProposalCommentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.accessRefused) {
            // Авторская сторона (UX-22): только «Предложения», поверхностей нет.
            ProposalsSurface(
                state = state,
                onBoxSelected = onProposalsBoxSelected,
                onLoadMoreClick = onLoadMoreProposalsClick,
                onProposalSelected = onProposalSelected,
                onBackToListClick = onBackToProposalsListClick,
                onDecide = onProposalDecision,
                onCommentChanged = onProposalCommentChanged,
                modifier = Modifier.weight(1f),
            )
        } else {
            DecisionsSurfaceToggle(
                selected = state.surface,
                onSurfaceSelected = onSurfaceSelected,
            )
            when (state.surface) {
                DecisionsSurfaceUi.VERDICTS -> {
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
                DecisionsSurfaceUi.PROPOSALS -> {
                    ProposalsSurface(
                        state = state,
                        onBoxSelected = onProposalsBoxSelected,
                        onLoadMoreClick = onLoadMoreProposalsClick,
                        onProposalSelected = onProposalSelected,
                        onBackToListClick = onBackToProposalsListClick,
                        onDecide = onProposalDecision,
                        onCommentChanged = onProposalCommentChanged,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DecisionsSurfaceToggle(
    selected: DecisionsSurfaceUi,
    onSurfaceSelected: (DecisionsSurfaceUi) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DecisionsSurfaceUi.entries.forEach { surface ->
            FilterChip(
                selected = surface == selected,
                onClick = { onSurfaceSelected(surface) },
                label = {
                    Text(
                        stringResource(
                            when (surface) {
                                DecisionsSurfaceUi.VERDICTS -> R.string.qa_proposals_surface_verdicts
                                DecisionsSurfaceUi.PROPOSALS -> R.string.qa_proposals_surface_proposals
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun ProposalsSurface(
    state: DecisionsUiState,
    onBoxSelected: (QuestProposalBox) -> Unit,
    onLoadMoreClick: () -> Unit,
    onProposalSelected: (String) -> Unit,
    onBackToListClick: () -> Unit,
    onDecide: (Boolean) -> Unit,
    onCommentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.qa_proposals_surface_proposals),
            style = NoirType.groupTitle,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestProposalBox.entries.forEach { box ->
                FilterChip(
                    selected = box == state.proposalsBox,
                    onClick = { onBoxSelected(box) },
                    label = {
                        Text(
                            stringResource(
                                when (box) {
                                    QuestProposalBox.INCOMING -> R.string.qa_proposals_box_incoming
                                    QuestProposalBox.OUTGOING -> R.string.qa_proposals_box_outgoing
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (state.proposalsDetail == null) {
            if (state.proposals.isEmpty() && !state.proposalsLoading) {
                Text(
                    text =
                        stringResource(
                            when (state.proposalsBox) {
                                QuestProposalBox.INCOMING -> R.string.qa_proposals_empty_incoming
                                QuestProposalBox.OUTGOING -> R.string.qa_proposals_empty_outgoing
                            },
                        ),
                    color = NoirT3,
                )
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.proposals, key = { it.proposalId }) { row ->
                    ProposalRowCard(row = row, onOpen = { onProposalSelected(row.proposalId) })
                }
                if (state.proposalsHasMore) {
                    item {
                        Text(
                            text = stringResource(R.string.qa_proposals_load_more),
                            color = LocalNoirAccent.current,
                            modifier =
                                Modifier
                                    .clickable(enabled = !state.proposalsLoadingMore, onClick = onLoadMoreClick)
                                    .padding(vertical = 8.dp),
                        )
                    }
                }
            }
            if (state.proposalsLoading) {
                Text(text = stringResource(R.string.qa_proposals_loading), color = NoirT3)
            }
        } else {
            ProposalDetail(
                row = state.proposalsDetail,
                commentDraft = state.proposalCommentDraft,
                decisionState = state.proposalDecisionState,
                onBackToListClick = onBackToListClick,
                onDecide = onDecide,
                onCommentChanged = onCommentChanged,
                modifier = Modifier.weight(1f),
            )
        }
        state.proposalsErrorMessage?.let {
            Text(text = it.resolveText(), color = NoirDanger)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ProposalRowCard(
    row: QuestProposalRow,
    onOpen: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(NoirShapeMd)
                .background(NoirGlassFill)
                .border(1.dp, NoirGlassStroke, NoirShapeMd)
                .clickable(onClick = onOpen)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(proposalKindLabel(row.kind), style = NoirType.kicker.copy(color = NoirT2))
            Text(proposalStatusLabel(row.status), style = NoirType.kicker.copy(color = LocalNoirAccent.current))
            if (row.stale) {
                Text(
                    text = stringResource(R.string.qa_proposals_stale),
                    style = NoirType.kicker.copy(color = NoirDanger),
                )
            }
        }
        Text(
            text = row.lessonTitle ?: row.targetLessonId ?: row.targetThemeId ?: row.proposalId,
            style = NoirType.rowTitle.copy(fontSize = 15.sp, color = NoirT1),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun ProposalDetail(
    row: QuestProposalRow,
    commentDraft: String,
    decisionState: DecisionsMarkStateUi,
    onBackToListClick: () -> Unit,
    onDecide: (Boolean) -> Unit,
    onCommentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = proposalKindLabel(row.kind),
            style = NoirType.kicker.copy(color = NoirT2),
            modifier = Modifier.clickable(onClick = onBackToListClick),
        )
        if (row.unreadable) {
            Text(text = stringResource(R.string.qa_proposals_unreadable), color = NoirDanger)
            return@Column
        }
        Text(
            text = row.lessonTitle ?: row.targetLessonId ?: row.proposalId,
            style = NoirType.rowTitle.copy(fontSize = 16.sp, color = NoirT1),
        )
        Text(
            text = proposalStatusLabel(row.status) + row.staleSuffix(),
            style = NoirType.rowSub.copy(color = LocalNoirAccent.current),
        )
        row.proposalText?.let {
            Text(text = it, style = NoirType.rowSub.copy(color = NoirT1))
        }
        row.proposalOwnVariant?.let {
            Text(
                text = stringResource(R.string.qa_proposals_own_variant, it),
                style = NoirType.rowSub.copy(color = NoirT2),
            )
        }
        if (row.questionPreviews.isNotEmpty()) {
            Text(text = stringResource(R.string.qa_proposals_questions), style = NoirType.kicker.copy(color = NoirT2))
            row.questionPreviews.forEach { preview ->
                Text(
                    text =
                        (if (preview.edited) "✓ " else "· ") + preview.text,
                    style = NoirType.rowSub.copy(color = NoirT2),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        row.decision?.let { decision ->
            Text(
                text =
                    stringResource(R.string.qa_proposals_outcome, proposalDecisionLabel(decision.decision)) +
                        (decision.comment?.takeIf { it.isNotBlank() }?.let { c -> ": $c" } ?: ""),
                style = NoirType.rowSub.copy(color = NoirT2),
            )
        }
        // Решение — только владелец цели на ждущем и не-устаревшем (право решает сервер).
        val decidable = row.status == "CHECK_PASSED" && !row.stale && !row.unreadable && row.decision == null
        if (decidable) {
            BasicTextField(
                value = commentDraft,
                onValueChange = onCommentChanged,
                textStyle = NoirType.rowSub.copy(color = NoirT1),
                cursorBrush = SolidColor(LocalNoirAccent.current),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(NoirShapeMd)
                        .border(1.dp, NoirOutline, NoirShapeMd)
                        .padding(10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text =
                        if (decisionState == DecisionsMarkStateUi.SAVING) {
                            stringResource(R.string.qa_proposals_decision_saving)
                        } else {
                            stringResource(R.string.qa_proposals_decide_approve)
                        },
                    color = if (decisionState == DecisionsMarkStateUi.SAVING) NoirTOff else NoirSuccess,
                    modifier =
                        Modifier.clickable(enabled = decisionState != DecisionsMarkStateUi.SAVING) {
                            onDecide(true)
                        },
                )
                Text(
                    text = stringResource(R.string.qa_proposals_decide_reject),
                    color = if (decisionState == DecisionsMarkStateUi.SAVING) NoirTOff else NoirDanger,
                    modifier =
                        Modifier.clickable(enabled = decisionState != DecisionsMarkStateUi.SAVING) {
                            onDecide(false)
                        },
                )
            }
        }
    }
}

/** Хвост статуса: метка «Устарело» на той же строке, если база предложения уехала. */
@Composable
private fun QuestProposalRow.staleSuffix(): String =
    if (stale) {
        " · " + stringResource(R.string.qa_proposals_stale)
    } else {
        ""
    }

@Composable
private fun proposalKindLabel(kind: String): String =
    stringResource(
        when (kind) {
            "REPORT" -> R.string.qa_proposals_kind_report
            "COPY_EDIT" -> R.string.qa_proposals_kind_copy_edit
            "NEW_LESSON" -> R.string.qa_proposals_kind_new_lesson
            else -> R.string.qa_proposals_kind_report
        },
    )

@Composable
private fun proposalStatusLabel(status: String): String =
    stringResource(
        when (status) {
            "QUEUED" -> R.string.qa_proposals_status_queued
            "CHECK_PASSED" -> R.string.qa_proposals_status_check_passed
            "CHECK_FAILED" -> R.string.qa_proposals_status_check_failed
            "CHECK_DELAYED" -> R.string.qa_proposals_status_check_delayed
            "APPROVED" -> R.string.qa_proposals_status_approved
            "REJECTED" -> R.string.qa_proposals_status_rejected
            else -> R.string.qa_proposals_status_queued
        },
    )

@Composable
private fun proposalDecisionLabel(decision: String): String =
    stringResource(
        when (decision) {
            "APPROVE" -> R.string.qa_proposals_decision_approve_label
            "REJECT" -> R.string.qa_proposals_decision_reject_label
            else -> R.string.qa_proposals_decision_approve_label
        },
    )

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
