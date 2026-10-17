@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlayUiState
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentQualificationComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_COMPLETED
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_QUESTION_TIMED_OUT
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_SETTLED
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_STARTED
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentQuestionTypes
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.WINDOW_OPEN
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentWindowView

/**
 * Маршрут участия (история 5.10): выбор события, раунды со статусами из `sessions[]`, старт раунда
 * с токеном ссылки, игра вопроса: ответ уезжает из очереди 5.9, окно ведёт expiresAtMs сервера,
 * квитанция готовности ставится после доставки. Опоздавший ответ показывает честный исход.
 */
@Composable
fun TournamentQualificationScreen(
    component: TournamentQualificationComponent,
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
                Text(stringResource(R.string.tournament_qualification_title), style = NoirType.groupTitle)
                if (state.event != null) {
                    OutlinedButton(
                        onClick = component::onCloseEvent,
                        modifier = Modifier.testTag("tournament_qualification_back"),
                    ) {
                        Text(stringResource(R.string.tournament_back))
                    }
                } else {
                    OutlinedButton(
                        onClick = component::onRefreshEventsClick,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("tournament_qualification_refresh"),
                    ) {
                        Text(stringResource(R.string.tournament_refresh))
                    }
                }
            }
        }
        val event = state.event
        if (event == null) {
            item { Text(stringResource(R.string.tournament_qualification_pick_title), style = NoirType.groupTitle) }
            items(state.events, key = { it.eventId }) { row ->
                NoirGroup {
                    Column(Modifier.padding(12.dp)) {
                        Text(statusText(row), color = NoirT3)
                        OutlinedButton(
                            onClick = { component.onPickEvent(row.eventId) },
                            enabled = !state.busy,
                            modifier = Modifier.testTag("tournament_qualification_pick_" + row.eventId),
                        ) {
                            Text(stringResource(R.string.tournament_browser_enter))
                        }
                    }
                }
            }
            if (state.canLoadMore) {
                item {
                    OutlinedButton(
                        onClick = component::onLoadMoreEventsClick,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("tournament_qualification_load_more"),
                    ) {
                        Text(stringResource(R.string.tournament_load_more))
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.linkToken,
                        onValueChange = component::onLinkTokenChange,
                        label = { Text(stringResource(R.string.tournament_qualification_link_hint)) },
                        modifier = Modifier.fillMaxWidth().testTag("tournament_qualification_link_token"),
                        singleLine = true,
                    )
                    state.rounds.forEachIndexed { index, round ->
                        Row(
                            Modifier.fillMaxWidth().testTag("tournament_qualification_round_$index"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.tournament_qualification_round, round.roundNumber))
                            Text(
                                round.sessionId?.let { sessionId ->
                                    round.status?.let { roundStatusText(it) } ?: sessionId
                                } ?: stringResource(R.string.tournament_qualification_round_not_started),
                                color = NoirT3,
                            )
                            Button(
                                onClick = { component.onStartRoundClick(round.roundNumber) },
                                enabled = !state.busy,
                                modifier = Modifier.testTag("tournament_qualification_start_$index"),
                            ) {
                                Text(stringResource(R.string.tournament_qualification_start))
                            }
                        }
                    }
                }
            }
            item { state.play?.let { PlayPanel(it, component, state.serverNowMs) } }
        }
        if (state.busy) {
            item { CircularProgressIndicator() }
        }
        item { Text(noticeText(state.notice), color = NoirT3) }
    }
}

@Composable
private fun PlayPanel(
    play: PlayUiState,
    component: TournamentQualificationComponent,
    serverNowMs: Long?,
) {
    NoirGroup {
        Column(
            modifier = Modifier.padding(12.dp).testTag("tournament_qualification_play"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                play.settled -> {
                    Text(
                        stringResource(
                            R.string.tournament_qualification_settled,
                            play.result?.let { "%.2f".format(it.percentScore) } ?: percentOrDash(null),
                        ),
                        Modifier.testTag("tournament_qualification_settled"),
                        style = NoirType.groupTitle,
                    )
                }
                play.timedOut -> {
                    Text(
                        stringResource(R.string.tournament_qualification_timed_out),
                        Modifier.testTag("tournament_qualification_timed_out"),
                        style = NoirType.groupTitle,
                    )
                    OutlinedButton(onClick = component::onRecheckClick) {
                        Text(stringResource(R.string.tournament_qualification_recheck))
                    }
                }
                else -> QuestionPanel(play, component, serverNowMs)
            }
        }
    }
}

@Composable
private fun QuestionPanel(
    play: PlayUiState,
    component: TournamentQualificationComponent,
    serverNowMs: Long?,
) {
    val question = play.question
    if (question == null) {
        Text(stringResource(R.string.tournament_empty), color = NoirT3)
        OutlinedButton(onClick = component::onRecheckClick) {
            Text(stringResource(R.string.tournament_qualification_recheck))
        }
        return
    }
    val payload = question.publicPayload
    Text(
        stringResource(R.string.tournament_qualification_question_of, play.questionIndex + 1),
        Modifier.testTag("tournament_qualification_question_index"),
        style = NoirType.groupTitle,
    )
    Text(payload.text, Modifier.testTag("tournament_qualification_question_text"))
    val options = payload.options.orEmpty() + payload.items.orEmpty() + payload.candidates.orEmpty()
    options.forEach { option ->
        val selected = option.id in play.selectedIds
        OutlinedButton(
            onClick = {
                if (payload.type == TournamentQuestionTypes.ORDERING) {
                    component.onOrderTap(
                        option.id,
                    )
                } else {
                    component.onToggleChoice(option.id)
                }
            },
            enabled = !play.awaitingDelivery,
            modifier = Modifier.testTag("tournament_qualification_option_" + option.id),
        ) {
            Text((if (selected) "[x] " else "[ ] ") + option.text)
        }
    }
    payload.blanks.orEmpty().forEachIndexed { index, blank ->
        OutlinedTextField(
            value = play.blanks.getOrElse(index) { "" },
            onValueChange = { component.onBlankChange(index, it) },
            label = { Text(stringResource(R.string.tournament_qualification_blank_hint, index + 1)) },
            modifier = Modifier.fillMaxWidth().testTag("tournament_qualification_blank_$index"),
            singleLine = true,
        )
    }
    play.selectedIds.forEachIndexed { index, id ->
        Text(
            stringResource(
                R.string.tournament_qualification_order_position,
                index + 1,
                options.firstOrNull { it.id == id }?.text ?: id,
            ),
            color = NoirT3,
        )
    }
    if (play.awaitingDelivery) {
        Text(
            stringResource(R.string.tournament_qualification_answer_pending),
            Modifier.testTag("tournament_qualification_answer_pending"),
            color = NoirT3,
        )
    } else {
        Button(
            onClick = component::onSubmitAnswerClick,
            modifier = Modifier.testTag("tournament_qualification_submit"),
        ) {
            Text(stringResource(R.string.tournament_qualification_answer_submit))
        }
    }
    // Окно AD-31 словами: ответ доставлен (в том числе поздней доставкой после бюджета опроса),
    // готовность подтверждена квитанцией READY_ACK; край окна — серверный expiresAtMs.
    val window = play.window
    if (!play.awaitingDelivery) {
        when {
            play.readyAckWindowId != null -> {
                Text(
                    stringResource(R.string.tournament_qualification_ready_acked),
                    Modifier.testTag("tournament_qualification_ready_acked"),
                    color = NoirT3,
                )
                WindowNextLine(window, serverNowMs)
            }
            window != null && window.state == WINDOW_OPEN -> {
                Text(
                    stringResource(R.string.tournament_qualification_answer_delivered),
                    Modifier.testTag("tournament_qualification_answer_delivered"),
                    color = NoirT3,
                )
                WindowNextLine(window, serverNowMs)
            }
        }
    }
}

/** Целые секунды до серверного края окна; пересчитывается отрисовкой и перечитыванием. */
@Composable
private fun WindowNextLine(
    window: TournamentWindowView?,
    serverNowMs: Long?,
) {
    if (window == null || window.state != WINDOW_OPEN || serverNowMs == null) return
    val seconds = ((window.expiresAtMs - (serverNowMs ?: 0L)).coerceAtLeast(0L) / 1000L).toInt()
    Text(
        stringResource(R.string.tournament_qualification_window_next, seconds),
        Modifier.testTag("tournament_qualification_window_next"),
        color = NoirT3,
    )
}

/** Статус раунда словами серверного статуса; незнакомое имя показывается как есть. */
@Composable
private fun roundStatusText(status: String?): String =
    when (status) {
        SESSION_STARTED -> stringResource(R.string.tournament_round_started)
        SESSION_SETTLED -> stringResource(R.string.tournament_round_settled)
        SESSION_COMPLETED -> stringResource(R.string.tournament_round_completed)
        SESSION_QUESTION_TIMED_OUT -> stringResource(R.string.tournament_round_timed_out)
        else -> status.orEmpty()
    }
