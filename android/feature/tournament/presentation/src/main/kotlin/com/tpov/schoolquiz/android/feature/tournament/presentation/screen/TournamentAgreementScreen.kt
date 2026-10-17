@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAgreementComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.MEETING_DOUBLE_OUT
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.MEETING_FORFEIT
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.MEETING_WAITING_FOR_AGREEMENT
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.MEETING_WAITING_FOR_READY
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.MEETING_WAITING_FOR_WINDOW
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_STARTED
import com.tpov.schoolquiz.shared.core.sync.formatSyncTimestamp
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingView

/**
 * Согласование слота матча (история 5.11): предложения, подтверждения и готовность — поимённо
 * каждым участником; границы стадии и согласованного часа — серверные факты проекции. Времена
 * показывает читаемым серверным временем, а не сырой эпохой. Слот неизменен после согласия:
 * кнопки подтверждения уходят в отставку, когда слот уже согласован или уже подтверждён своим.
 * Когда сервер начал матч, у своей сессии открывается поверхность игры; пока матч не открыт —
 * surface намерений не рисуется, есть только честное состояние.
 */
@Composable
fun TournamentAgreementScreen(
    component: TournamentAgreementComponent,
    onPlay: (sessionId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.tournament_agreement_title))
                OutlinedButton(
                    onClick = {
                        component.onCloseMatch()
                        onBack()
                    },
                    modifier = Modifier.testTag("tournament_agreement_back"),
                ) {
                    Text(stringResource(R.string.tournament_back))
                }
            }
        }
        val meeting = state.meeting
        if (meeting == null) {
            // Матч не открыт: surface намерений (ввод слота, предложение, готовность) не рисуется —
            // там нечего нажимать, остаётся честное состояние.
            item { Text(stringResource(R.string.tournament_agreement_empty), color = NoirT3) }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        meetingStatusText(meeting.meeting),
                        Modifier.testTag("tournament_agreement_status"),
                    )
                    Text(
                        stringResource(R.string.tournament_agreement_deadline, formatSyncTimestamp(meeting.schedulingDeadlineMs)),
                        Modifier.testTag("tournament_agreement_deadline"),
                        color = NoirT3,
                    )
                    Text(
                        stringResource(
                            R.string.tournament_agreement_stage,
                            formatSyncTimestamp(meeting.stageStartsAtMs),
                            formatSyncTimestamp(meeting.stageEndsAtMs),
                        ),
                        Modifier.testTag("tournament_agreement_stage"),
                        color = NoirT3,
                    )
                    val view = meeting.meeting
                    val agreedSlot = view.slotStartsAtMs
                    if (agreedSlot != null) {
                        Text(
                            stringResource(R.string.tournament_agreement_agreed_slot, formatSyncTimestamp(agreedSlot)),
                            Modifier.testTag("tournament_agreement_agreed_slot"),
                        )
                        WindowLine(view.windowEndMs, agreedSlot)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.tournament_agreement_participants))
                    meeting.participants.forEachIndexed { index, participant ->
                        Text(
                            stringResource(
                                R.string.tournament_agreement_participant,
                                participant.uid,
                                // Сид, которого сервер не назвал, не выдумывается нулём.
                                participant.seed?.toString() ?: "—",
                            ),
                            Modifier.testTag("tournament_agreement_participant_$index"),
                            color = NoirT3,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.tournament_agreement_offers))
                    meeting.offers.forEachIndexed { index, offer ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .testTag("tournament_agreement_offer_$index"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(
                                    R.string.tournament_agreement_offer,
                                    offer.uid,
                                    formatSyncTimestamp(offer.slotStartsAtMs),
                                    formatSyncTimestamp(offer.acceptedAtMs),
                                ),
                                color = NoirT3,
                            )
                            OutlinedButton(
                                onClick = { component.onConfirmClick(offer.slotStartsAtMs) },
                                enabled = rowConfirmEnabled(offer.slotStartsAtMs, meeting, state.session?.ownerUid, state.busy),
                                modifier = Modifier.testTag("tournament_agreement_confirm_$index"),
                            ) {
                                Text(stringResource(R.string.tournament_agreement_confirm))
                            }
                        }
                    }
                    Text(stringResource(R.string.tournament_agreement_confirmations))
                    meeting.confirmations.forEachIndexed { index, confirmation ->
                        Text(
                            stringResource(
                                R.string.tournament_agreement_confirmation,
                                confirmation.uid,
                                formatSyncTimestamp(confirmation.slotStartsAtMs),
                                formatSyncTimestamp(confirmation.acceptedAtMs),
                            ),
                            Modifier.testTag("tournament_agreement_confirmation_$index"),
                            color = NoirT3,
                        )
                    }
                    if (meeting.hasMoreOffers || meeting.hasMoreConfirmations) {
                        OutlinedButton(
                            onClick = component::onLoadMoreClick,
                            enabled = !state.busy,
                            modifier = Modifier.testTag("tournament_agreement_load_more"),
                        ) {
                            Text(stringResource(R.string.tournament_load_more))
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.tournament_agreement_ready_title))
                    meeting.ready.forEachIndexed { index, ready ->
                        Text(
                            stringResource(R.string.tournament_agreement_ready_row, ready.uid, formatSyncTimestamp(ready.acceptedAtMs)),
                            Modifier.testTag("tournament_agreement_ready_row_$index"),
                            color = NoirT3,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.slotInput,
                        onValueChange = component::onSlotStartsAtChange,
                        label = { Text(stringResource(R.string.tournament_agreement_slot_hint)) },
                        modifier = Modifier.fillMaxWidth().testTag("tournament_agreement_slot_input"),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = component::onProposeClick,
                            enabled = !state.busy,
                            modifier = Modifier.testTag("tournament_agreement_propose"),
                        ) {
                            Text(stringResource(R.string.tournament_agreement_propose))
                        }
                        Button(
                            onClick = component::onReadyClick,
                            enabled = !state.busy,
                            modifier = Modifier.testTag("tournament_agreement_ready"),
                        ) {
                            Text(stringResource(R.string.tournament_agreement_ready))
                        }
                    }
                    val playSessionId = state.playSessionId
                    if (playSessionId != null && state.meeting?.meeting?.status == SESSION_STARTED) {
                        Button(
                            onClick = { onPlay(playSessionId) },
                            modifier = Modifier.testTag("tournament_agreement_play"),
                        ) {
                            Text(stringResource(R.string.tournament_agreement_play))
                        }
                    }
                }
            }
        }
        if (state.busy) {
            item { CircularProgressIndicator() }
        }
        item { Text(noticeText(state.notice), color = NoirT3) }
    }
}

/** Кнопка подтверждения строки уходит в отставку, когда слот уже согласован сервером или этот
 * слот уже подтверждён этим участником: неизменность согласия не приглашают сломать повтором. */
internal fun rowConfirmEnabled(
    offerSlotStartsAtMs: Long,
    meeting: TournamentMeetingPage,
    ownerUid: String?,
    busy: Boolean,
): Boolean =
    !busy &&
        meeting.meeting.slotStartsAtMs == null &&
        meeting.confirmations.none { it.uid == ownerUid && it.slotStartsAtMs == offerSlotStartsAtMs }

/** Окно готовности словами серверных часов: край и начало — факты встречи. */
@Composable
private fun WindowLine(
    windowEndMs: Long?,
    slotStartsAtMs: Long?,
) {
    if (windowEndMs == null || slotStartsAtMs == null) return
    Text(
        stringResource(
            R.string.tournament_agreement_window,
            formatSyncTimestamp(slotStartsAtMs),
            formatSyncTimestamp(windowEndMs),
        ),
        Modifier.testTag("tournament_agreement_window"),
        color = NoirT3,
    )
}

/** Статус встречи словами серверного статуса; незнакомое имя показывается как есть. */
@Composable
internal fun meetingStatusText(meeting: TournamentMeetingView?): String =
    when (meeting?.status) {
        MEETING_WAITING_FOR_AGREEMENT -> stringResource(R.string.tournament_meeting_waiting_agreement)
        MEETING_WAITING_FOR_WINDOW -> stringResource(R.string.tournament_meeting_waiting_window)
        MEETING_WAITING_FOR_READY -> stringResource(R.string.tournament_meeting_waiting_ready)
        SESSION_STARTED -> stringResource(R.string.tournament_meeting_started)
        MEETING_FORFEIT -> stringResource(R.string.tournament_meeting_forfeit)
        MEETING_DOUBLE_OUT -> stringResource(R.string.tournament_meeting_double_out)
        else -> meeting?.status.orEmpty()
    }
