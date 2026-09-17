package com.tpov.schoolquiz.android.feature.internet.profile.presentation.screen

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.R
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.AccountSwitchCount
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.AccountSwitchCountKind
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.AccountSwitchWarning
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountDataKind

/**
 * What switching accounts will lose, asked before it happens (AD-8).
 *
 * Every non-zero number gets its own line and its own word: a single "6 actions" would hide the
 * conflict that is waiting on the player's decision, and would say nothing about the answer
 * history, the repetition schedule and the unpublished drafts that the server does not keep. The
 * warning is the consent, so there is no third way out: tapping outside is the same as declining.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun AccountSwitchWarningDialog(
    warning: AccountSwitchWarning,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.profile_switch_warning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (warning.isKnown) {
                    warning.counts.forEach { count ->
                        Text(pluralStringResource(count.kind.plural(), count.value, count.value))
                    }
                } else {
                    Text(stringResource(R.string.profile_switch_warning_unsent_unknown))
                }
                if (warning.namedData.isNotEmpty()) {
                    Text(stringResource(R.string.profile_switch_warning_data_title))
                    warning.namedData.forEach { kind -> Text(stringResource(kind.label())) }
                }
                Text(stringResource(R.string.profile_switch_warning_irreversible), color = NoirDanger)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.profile_switch_warning_confirm), color = NoirDanger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.profile_switch_warning_decline))
            }
        },
    )
}

/** Своё слово на каждое состояние — счётчик без слова не объясняет, что именно пропадёт. */
@PluralsRes
private fun AccountSwitchCountKind.plural(): Int =
    when (this) {
        AccountSwitchCountKind.WAITING -> R.plurals.profile_switch_warning_waiting
        AccountSwitchCountKind.WAITING_PRECONDITION -> R.plurals.profile_switch_warning_waiting_precondition
        AccountSwitchCountKind.CONFLICTED -> R.plurals.profile_switch_warning_conflicted
        AccountSwitchCountKind.QUARANTINED -> R.plurals.profile_switch_warning_quarantined
        AccountSwitchCountKind.SYNCHRONOUS -> R.plurals.profile_switch_warning_synchronous
    }

/** Имя данных, а не таблицы: перечень приходит из истории 6.2, слова живут здесь (NFR11). */
@StringRes
private fun AccountDataKind.label(): Int =
    when (this) {
        AccountDataKind.ANSWER_HISTORY -> R.string.profile_switch_warning_data_answer_history
        AccountDataKind.REPETITION_SCHEDULE -> R.string.profile_switch_warning_data_repetition_schedule
        AccountDataKind.UNPUBLISHED_QUEST_DRAFTS -> R.string.profile_switch_warning_data_quest_drafts
    }

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AccountSwitchWarningDialogPreview() {
    NoirTheme {
        AccountSwitchWarningDialog(
            warning =
                AccountSwitchWarning(
                    counts =
                        listOf(
                            AccountSwitchCount(AccountSwitchCountKind.WAITING, 3),
                            AccountSwitchCount(AccountSwitchCountKind.CONFLICTED, 1),
                        ),
                    namedData = AccountDataKind.entries,
                    isKnown = true,
                ),
            onConfirm = {},
            onDecline = {},
        )
    }
}
