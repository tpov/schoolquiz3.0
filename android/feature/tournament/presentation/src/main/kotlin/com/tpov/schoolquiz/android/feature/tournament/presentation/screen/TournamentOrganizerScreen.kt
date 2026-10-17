@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentOrganizerComponent
import com.tpov.schoolquiz.shared.core.sync.formatSyncTimestamp

/**
 * Маршрут организатора (история 5.10): ввод источника, подготовка банка, согласие с замороженными
 * условиями (строки призов + фонд под testTag), CREATE и читалка эскроу. Экран только рендерит
 * состояние и зовёт именованные намерения компонента.
 */
@Composable
fun TournamentOrganizerScreen(
    component: TournamentOrganizerComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tournament_organizer_title), style = NoirType.groupTitle)
        OutlinedTextField(
            value = state.sourceId,
            onValueChange = component::onSourceIdChange,
            label = { Text(stringResource(R.string.tournament_organizer_source_hint)) },
            modifier = Modifier.fillMaxWidth().testTag("tournament_organizer_source"),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = component::onPrepareClick,
                enabled = !state.busy && state.sourceId.isNotBlank(),
                modifier = Modifier.testTag("tournament_organizer_prepare"),
            ) {
                Text(stringResource(R.string.tournament_organizer_prepare))
            }
            OutlinedButton(
                onClick = component::onShowConsentClick,
                enabled = !state.busy && state.hasPrepare,
                modifier = Modifier.testTag("tournament_organizer_show_consent"),
            ) {
                Text(stringResource(R.string.tournament_organizer_show_consent))
            }
        }
        state.prepare?.let { prepare ->
            Text(
                stringResource(
                    R.string.tournament_organizer_prepare_done,
                    prepare.counts.easy,
                    prepare.counts.hard,
                ),
                color = NoirT3,
            )
        }
        if (state.busy) CircularProgressIndicator()
        Text(noticeText(state.notice), color = NoirT3)
        // Отказ CREATE повторяем: повтор живёт вне созданного события, он виден и без него.
        if (state.canRetryCreate) {
            OutlinedButton(
                onClick = component::onRetryCreateClick,
                enabled = !state.busy,
                modifier = Modifier.testTag("tournament_organizer_create_retry"),
            ) {
                Text(stringResource(R.string.tournament_retry))
            }
        }
        state.created?.let { created ->
            NoirGroup {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.tournament_organizer_created), style = NoirType.groupTitle)
                    Text(
                        stringResource(R.string.tournament_browser_detail_title) + ": " + created.eventId,
                        color = NoirT3,
                        modifier = Modifier.testTag("tournament_organizer_created_event"),
                    )
                    Text(stringResource(R.string.tournament_organizer_escrow_title), style = NoirType.groupTitle)
                    EscrowRow(
                        tag = "tournament_escrow_reserved",
                        text =
                            stringResource(
                                R.string.tournament_organizer_escrow_reserved,
                                created.escrow.reservedBoxes,
                            ),
                    )
                    EscrowRow(
                        tag = "tournament_escrow_remaining",
                        text =
                            stringResource(
                                R.string.tournament_organizer_escrow_remaining,
                                created.escrow.remainingBoxes,
                            ),
                    )
                    EscrowRow(
                        tag = "tournament_escrow_paid",
                        text = stringResource(R.string.tournament_organizer_escrow_paid, created.escrow.paidBoxes),
                    )
                    EscrowRow(
                        tag = "tournament_escrow_returned",
                        text =
                            stringResource(
                                R.string.tournament_organizer_escrow_returned,
                                created.escrow.returnedBoxes,
                            ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = component::onRetryCreateClick,
                            enabled = !state.busy,
                        ) {
                            Text(stringResource(R.string.tournament_retry))
                        }
                        OutlinedButton(
                            onClick = component::onRefreshClick,
                            enabled = !state.busy,
                        ) {
                            Text(stringResource(R.string.tournament_refresh))
                        }
                        OutlinedButton(
                            onClick = component::onNewCreateClick,
                            enabled = !state.busy,
                            modifier = Modifier.testTag("tournament_organizer_new_create"),
                        ) {
                            Text(stringResource(R.string.tournament_organizer_new_create))
                        }
                    }
                }
            }
        }
    }
    state.consent?.let { consent ->
        AlertDialog(
            modifier = Modifier.testTag("tournament_consent_dialog"),
            onDismissRequest = { if (!state.busy) component.onDismissConsent(consent.dialogId) },
            title = { Text(stringResource(R.string.tournament_organizer_consent_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tournament_organizer_consent_terms), style = NoirType.groupTitle)
                    // Замороженный момент открытия: то, что уедет в CREATE, а не «сейчас» экрана.
                    Text(
                        stringResource(
                            R.string.tournament_organizer_consent_starts,
                            formatSyncTimestamp(consent.startsAtMs),
                        ),
                        Modifier.testTag("tournament_consent_starts"),
                        color = NoirT3,
                    )
                    consent.prizeRows.forEachIndexed { index, row ->
                        // Замороженные строки призов согласия — деньги: свой testTag.
                        Text(
                            stringResource(R.string.tournament_organizer_consent_prize_row, row.place, row.amountBoxes),
                            Modifier.testTag("tournament_consent_prize_row_$index"),
                        )
                    }
                    Text(
                        stringResource(R.string.tournament_organizer_consent_fund, consent.fundBoxes),
                        Modifier.testTag("tournament_consent_fund"),
                    )
                    Text(stringResource(R.string.tournament_organizer_consent_warning))
                }
            },
            confirmButton = {
                Button(
                    onClick = { component.onConfirmCreate(consent.dialogId) },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("tournament_consent_confirm"),
                ) {
                    Text(stringResource(R.string.tournament_organizer_confirm_create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { component.onDismissConsent(consent.dialogId) },
                    enabled = !state.busy,
                ) {
                    Text(stringResource(R.string.tournament_close))
                }
            },
        )
    }
}

@Composable
private fun EscrowRow(
    tag: String,
    text: String,
) {
    Text(text, Modifier.testTag(tag), color = NoirT3)
}
