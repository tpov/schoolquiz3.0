@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryDialog
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryEvent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty

@Composable
internal fun ArenaEntryDialogs(
    state: ArenaEntryUiState,
    onEvent: (ArenaEntryEvent) -> Unit,
) {
    val stamp = state.session ?: return
    val dialog = state.dialog ?: return
    val dismiss = { onEvent(ArenaEntryEvent.Dismiss(stamp, dialog.id)) }
    AlertDialog(
        modifier = Modifier.testTag("arena_entry_dialog"),
        onDismissRequest = dismiss,
        title = {
            Text(
                stringResource(
                    if (dialog is ArenaEntryDialog.Prepare) {
                        R.string.arena_entry_prepare_title
                    } else {
                        R.string.arena_entry_cancel_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (dialog) {
                    is ArenaEntryDialog.Prepare -> {
                        Text(
                            if (dialog.lesson == null) {
                                stringResource(R.string.arena_entry_random_lesson)
                            } else {
                                dialog.lesson.title ?: stringResource(R.string.arena_catalog_untitled)
                            },
                        )
                        Text(
                            stringResource(R.string.arena_entry_price_consent, dialog.lesson?.priceNolics ?: 0),
                            Modifier.testTag("arena_consent_price"),
                        )
                        Text(stringResource(R.string.arena_entry_charge_consent))
                        Text(
                            stringResource(
                                difficultyResource(dialog.difficulty),
                            ),
                        )
                        Row {
                            for (difficulty in Difficulty.entries) TextButton(
                                onClick = { onEvent(ArenaEntryEvent.ChangeDifficulty(stamp, dialog.id, difficulty)) },
                                enabled = !state.busy,
                                modifier = Modifier.testTag("arena_difficulty_" + difficulty.name),
                            ) {
                                Text(
                                    stringResource(
                                        difficultyResource(difficulty),
                                    ),
                                )
                            }
                        }
                    }
                    is ArenaEntryDialog.Cancel -> {
                        Text(stringResource(R.string.arena_entry_ticket_id, dialog.ticket.ticketId))
                        dialog.ticket.header?.let {
                            Text(
                                stringResource(R.string.arena_entry_frozen_cost, it.priceNolics, it.lifeCost),
                            )
                        }
                        Text(stringResource(R.string.arena_entry_cancel_warning))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(ArenaEntryEvent.Confirm(stamp, dialog.id)) },
                enabled = !state.busy,
                modifier = Modifier.testTag("arena_entry_confirm"),
            ) { Text(stringResource(R.string.arena_entry_confirm)) }
        },
        dismissButton = {
            TextButton(
                dismiss,
                enabled = !state.busy,
            ) { Text(stringResource(R.string.arena_entry_dismiss)) }
        },
    )
}

private fun difficultyResource(difficulty: Difficulty): Int =
    if (difficulty == Difficulty.EASY) R.string.arena_entry_easy else R.string.arena_entry_hard
