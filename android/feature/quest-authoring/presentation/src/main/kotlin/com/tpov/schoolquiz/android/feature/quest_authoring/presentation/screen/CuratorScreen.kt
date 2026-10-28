package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDialog
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDialogAction
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.CuratorComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.transitionLabelRes
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.CuratorUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision

/**
 * Кураторская поверхность TZ06C + 4.8 (AD-21): короткий тап карточки открывает урок (статус,
 * флаги, историю решений), long press и равнозначный semantic action открывают меню разрешённых
 * «Разместить»/«Снять», пункт меню назначает цель, и решение уходит серверу только после
 * подтверждения в диалоге с обязательной причиной. Разрешения и исход решает сервер — экран
 * показывает; отказ сервера полку не меняет, прежняя редакция остаётся на карточке (CAP-5).
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun CuratorScreen(
    component: CuratorComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.qa_curator_title), style = NoirType.groupTitle)
        when {
            state.isLoading -> CircularProgressIndicator(color = LocalNoirAccent.current)

            state.permissionDenied ->
                Text(stringResource(R.string.qa_curator_permission_denied), style = NoirType.rowSub, color = NoirT3)

            else -> {
                state.errorMessage?.let { message ->
                    Text(
                        messageText(message),
                        color = NoirDanger,
                        style = NoirType.rowSub,
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(state.entries, key = { it.lessonId }) { entry ->
                        LifecycleEntryCard(
                            entry = entry,
                            selected = entry.lessonId == state.selectedLessonId,
                            menuOpen = entry.lessonId == state.menuLessonId,
                            onClick = { component.onLessonSelected(entry.lessonId) },
                            onLongClick = { component.onLessonMenuRequested(entry.lessonId) },
                            onMenuDismiss = component::onMenuDismissed,
                            onTargetChosen = component::onTransitionRequested,
                        )
                    }
                    if (state.canLoadMore) {
                        item {
                            NoirButton(
                                text = stringResource(R.string.qa_lifecycle_load_more),
                                onClick = component::onLoadMoreClick,
                                enabled = !state.isLoadingMore,
                                style = NoirButtonStyle.Ghost,
                                fillWidth = false,
                            )
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
                state.lastDecision?.let { decision ->
                    Text(
                        when (decision) {
                            is LessonTransitionDecision.Applied ->
                                stringResource(
                                    R.string.qa_curator_decision_applied,
                                    decision.targetShelf.wire,
                                )
                            is LessonTransitionDecision.Rejected ->
                                stringResource(
                                    R.string.qa_curator_decision_rejected,
                                    decision.code,
                                    decision.targetShelf.wire,
                                )
                        },
                        style = NoirType.rowSub,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoirButton(
                        text = stringResource(R.string.qa_lifecycle_refresh),
                        onClick = component::onRefreshClick,
                        style = NoirButtonStyle.Ghost,
                        fillWidth = false,
                    )
                }
            }
        }
    }
    state.pendingConfirmation?.let { confirmation ->
        TransitionConfirmDialog(
            state = state,
            lessonId = confirmation.lessonId,
            target = confirmation.target,
            onConfirm = { component.onTransitionClick(confirmation.target) },
            onDismiss = component::onConfirmationDismissed,
            onReasonChanged = component::onReasonChanged,
        )
    }
}

/** Диалог подтверждения (AD-21): решение уходит серверу только отсюда, с причиной. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TransitionConfirmDialog(
    state: CuratorUiState,
    lessonId: String,
    target: LessonShelf,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onReasonChanged: (String) -> Unit,
) {
    NoirDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.qa_curator_confirm_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.qa_curator_confirm_text,
                        lessonId,
                        stringResource(target.transitionLabelRes()),
                    ),
                    style = NoirType.rowSub,
                )
                OutlinedTextField(
                    value = state.reasonDraft,
                    onValueChange = onReasonChanged,
                    label = { Text(stringResource(R.string.qa_curator_reason_label)) },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NoirT1,
                            unfocusedTextColor = NoirT1,
                            focusedContainerColor = NoirS2,
                            unfocusedContainerColor = NoirS2,
                            focusedBorderColor = LocalNoirAccent.current,
                            unfocusedBorderColor = NoirOutline,
                            focusedLabelColor = LocalNoirAccent.current,
                            unfocusedLabelColor = NoirT2,
                            cursorColor = LocalNoirAccent.current,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isTransitioning) CircularProgressIndicator(color = LocalNoirAccent.current)
            }
        },
        actions = {
            NoirDialogAction(
                text = stringResource(R.string.qa_curator_confirm_positive),
                onClick = onConfirm,
                enabled = !state.isTransitioning,
                primary = true,
            )
            NoirDialogAction(
                text = stringResource(R.string.qa_curator_confirm_negative),
                onClick = onDismiss,
                enabled = !state.isTransitioning,
            )
        },
    )
}

/**
 * Карточка урока: тап — открыть. У куратора добавляется long press/semantic action — меню
 * переходов (AD-21); авторская поверхность переиспользует карточку без меню как есть.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun LifecycleEntryCard(
    entry: LessonLifecycleEntry,
    selected: Boolean,
    onClick: () -> Unit,
    menuOpen: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMenuDismiss: () -> Unit = {},
    onTargetChosen: (LessonShelf) -> Unit = {},
) {
    NoirGroup(
        modifier =
            if (onLongClick != null) {
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        // Равнозначный semantic action для long press (AD-21): доступность называет жест.
                        onLongClickLabel = stringResource(R.string.qa_curator_menu_open),
                    )
            } else {
                Modifier.fillMaxWidth()
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(entry.lessonId, style = NoirType.groupTitle)
            val shelf = entry.placement?.shelf
            Text(
                stringResource(
                    R.string.qa_lifecycle_entry_summary,
                    entry.state.wire,
                    shelf?.wire ?: DASH,
                    entry.currentContentVersion,
                    entry.placement?.contentVersion ?: 0L,
                ),
                style = NoirType.rowSub,
                color = NoirT3,
            )
            Text(
                stringResource(
                    R.string.qa_lifecycle_entry_flags,
                    entry.published,
                    entry.reviewApproved,
                    entry.rating.sum,
                    entry.rating.count,
                ),
                style = NoirType.rowSub,
                color = NoirT3,
            )
            if (selected) {
                Text(stringResource(R.string.qa_lifecycle_selected), style = NoirType.rowSub)
            }
            // Меню разрешённых целей: «Разместить» (arena/tournament/home) и «Снять»
            // (retired/rejected). Проверку допустимости самого перехода ведёт сервер.
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onMenuDismiss,
                containerColor = NoirS2,
            ) {
                TRANSITION_TARGETS.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(stringResource(target.transitionLabelRes()), color = NoirT1) },
                        onClick = { onTargetChosen(target) },
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun HistoryCard(
    title: String,
    history: List<LessonHistoryEntry>,
    loading: Boolean,
) {
    NoirGroup(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = NoirType.groupTitle)
            when {
                loading -> CircularProgressIndicator(color = LocalNoirAccent.current)

                history.isEmpty() ->
                    Text(stringResource(R.string.qa_lifecycle_history_empty), style = NoirType.rowSub, color = NoirT3)

                else ->
                    history.forEach { row ->
                        Text(
                            stringResource(
                                R.string.qa_lifecycle_history_row,
                                row.fromShelf.wire,
                                row.targetShelf.wire,
                                row.status.wire,
                                row.reasonCode ?: DASH,
                            ),
                            style = NoirType.rowSub,
                            color = NoirT3,
                        )
                    }
            }
        }
    }
}

@Composable
internal fun messageText(message: UiMessage): String =
    when (message) {
        is UiMessage.Res -> stringResource(message.id, *message.args.toTypedArray())
        is UiMessage.Raw -> message.value
        is UiMessage.ArenaQueued -> message.toString()
    }

private val TRANSITION_TARGETS =
    listOf(LessonShelf.ARENA, LessonShelf.TOURNAMENT, LessonShelf.HOME, LessonShelf.RETIRED, LessonShelf.REJECTED)
private const val DASH = "—"
