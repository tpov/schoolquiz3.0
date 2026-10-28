@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDialog
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDialogAction
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryDialog
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryEvent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty

/*
 * NOIR (эпик 9.3): material3 AlertDialog → NoirDialog (surface-4, радиус 20, mono-кнопки).
 * Все testTag сохранены байт-в-байт (риг выбирает сложность и подтверждает цену по ним);
 * строки не менялись.
 */
@Composable
internal fun ArenaEntryDialogs(
    state: ArenaEntryUiState,
    onEvent: (ArenaEntryEvent) -> Unit,
) {
    val stamp = state.session ?: return
    val dialog = state.dialog ?: return
    val dismiss = { onEvent(ArenaEntryEvent.Dismiss(stamp, dialog.id)) }
    NoirDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.testTag("arena_entry_dialog"),
        title =
            stringResource(
                if (dialog is ArenaEntryDialog.Prepare) {
                    R.string.arena_entry_prepare_title
                } else {
                    R.string.arena_entry_cancel_title
                },
            ),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (dialog) {
                    is ArenaEntryDialog.Prepare -> {
                        Text(
                            text =
                                if (dialog.lesson == null) {
                                    stringResource(R.string.arena_entry_random_lesson)
                                } else {
                                    dialog.lesson.title ?: stringResource(R.string.arena_catalog_untitled)
                                },
                            style = NoirType.rowTitle,
                        )
                        Text(
                            text =
                                stringResource(R.string.arena_entry_price_consent, dialog.lesson?.priceNolics ?: 0),
                            modifier = Modifier.testTag("arena_consent_price"),
                            style = NoirType.rowSub,
                        )
                        Text(stringResource(R.string.arena_entry_charge_consent), style = NoirType.rowSub)
                        Text(
                            stringResource(
                                difficultyResource(dialog.difficulty),
                            ),
                            style = NoirType.rowSub,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (difficulty in Difficulty.entries) NoirButton(
                                text = stringResource(difficultyResource(difficulty)),
                                onClick = {
                                    onEvent(ArenaEntryEvent.ChangeDifficulty(stamp, dialog.id, difficulty))
                                },
                                modifier = Modifier.testTag("arena_difficulty_" + difficulty.name),
                                style = NoirButtonStyle.Ghost,
                                enabled = !state.busy,
                                fillWidth = false,
                            )
                        }
                    }
                    is ArenaEntryDialog.Cancel -> {
                        Text(
                            stringResource(R.string.arena_entry_ticket_id, dialog.ticket.ticketId),
                            style = NoirType.rowSub,
                        )
                        dialog.ticket.header?.let {
                            Text(
                                stringResource(R.string.arena_entry_frozen_cost, it.priceNolics, it.lifeCost),
                                style = NoirType.rowSub,
                            )
                        }
                        Text(stringResource(R.string.arena_entry_cancel_warning), style = NoirType.rowSub)
                    }
                }
            }
        },
        actions = {
            NoirDialogAction(
                text = stringResource(R.string.arena_entry_confirm),
                onClick = { onEvent(ArenaEntryEvent.Confirm(stamp, dialog.id)) },
                modifier = Modifier.testTag("arena_entry_confirm"),
                primary = true,
                enabled = !state.busy,
            )
            NoirDialogAction(
                text = stringResource(R.string.arena_entry_dismiss),
                onClick = dismiss,
                enabled = !state.busy,
            )
        },
    )
}

private fun difficultyResource(difficulty: Difficulty): Int =
    if (difficulty == Difficulty.EASY) R.string.arena_entry_easy else R.string.arena_entry_hard
