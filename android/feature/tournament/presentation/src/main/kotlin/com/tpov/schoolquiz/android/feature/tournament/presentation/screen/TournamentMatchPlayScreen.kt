@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlayUiState
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentMatchPlayComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentQuestionTypes
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.WINDOW_OPEN
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentWindowView

/**
 * Игра матча (история 5.11): та же машина сессии, что и квалификация, — вопрос, окно по
 * серверному expiresAtMs, ответ из очереди 5.9, квитанция готовности после доставки. Опоздавший
 * ответ показывает честный исход сервера.
 */
@Composable
fun TournamentMatchPlayScreen(
    component: TournamentMatchPlayComponent,
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
                Text(stringResource(R.string.tournament_match_play_title))
                NoirButton(
                    text = stringResource(R.string.tournament_back),
                    onClick = {
                        component.onCloseMatchSession()
                        onBack()
                    },
                    modifier = Modifier.testTag("tournament_match_play_back"),
                    style = NoirButtonStyle.Ghost,
                    fillWidth = false,
                )
            }
        }
        item { state.play?.let { PlayPanel(it, component, state.serverNowMs) } }
        if (state.busy) {
            item { CircularProgressIndicator(color = LocalNoirAccent.current) }
        }
        item { Text(noticeText(state.notice), color = NoirT3) }
    }
}

@Composable
private fun PlayPanel(
    play: PlayUiState,
    component: TournamentMatchPlayComponent,
    serverNowMs: Long?,
) {
    Column(
        modifier = Modifier.padding(12.dp).testTag("tournament_match_play_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            play.settled -> {
                Text(
                    stringResource(
                        R.string.tournament_match_play_settled,
                        play.result?.let { "%.2f".format(it.percentScore) } ?: percentOrDash(null),
                    ),
                    Modifier.testTag("tournament_match_play_settled"),
                    style = NoirType.groupTitle,
                )
            }
            play.timedOut -> {
                Text(
                    stringResource(R.string.tournament_match_play_timed_out),
                    Modifier.testTag("tournament_match_play_timed_out"),
                    style = NoirType.groupTitle,
                )
                NoirButton(
                    text = stringResource(R.string.tournament_match_play_recheck),
                    onClick = component::onRecheckClick,
                    style = NoirButtonStyle.Ghost,
                    fillWidth = false,
                )
            }
            else -> QuestionPanel(play, component, serverNowMs)
        }
    }
}

@Composable
private fun QuestionPanel(
    play: PlayUiState,
    component: TournamentMatchPlayComponent,
    serverNowMs: Long?,
) {
    val question = play.question
    if (question == null) {
        Text(stringResource(R.string.tournament_empty), color = NoirT3)
        NoirButton(
            text = stringResource(R.string.tournament_match_play_recheck),
            onClick = component::onRecheckClick,
            style = NoirButtonStyle.Ghost,
            fillWidth = false,
        )
        return
    }
    val payload = question.publicPayload
    Text(
        stringResource(R.string.tournament_match_play_question_of, play.questionIndex + 1),
        Modifier.testTag("tournament_match_play_question_index"),
        style = NoirType.groupTitle,
    )
    Text(payload.text, Modifier.testTag("tournament_match_play_question_text"))
    val options = payload.options.orEmpty() + payload.items.orEmpty() + payload.candidates.orEmpty()
    options.forEach { option ->
        val selected = option.id in play.selectedIds
        NoirButton(
            text = (if (selected) "[x] " else "[ ] ") + option.text,
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
            modifier = Modifier.testTag("tournament_match_play_option_" + option.id),
            style = NoirButtonStyle.Ghost,
            fillWidth = false,
        )
    }
    payload.blanks.orEmpty().forEachIndexed { index, blank ->
        OutlinedTextField(
            value = play.blanks.getOrElse(index) { "" },
            onValueChange = { component.onBlankChange(index, it) },
            label = { Text(stringResource(R.string.tournament_match_play_blank_hint, index + 1)) },
            modifier = Modifier.fillMaxWidth().testTag("tournament_match_play_blank_$index"),
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
            singleLine = true,
        )
    }
    play.selectedIds.forEachIndexed { index, id ->
        Text(
            stringResource(
                R.string.tournament_match_play_order_position,
                index + 1,
                options.firstOrNull { it.id == id }?.text ?: id,
            ),
            color = NoirT3,
        )
    }
    if (play.awaitingDelivery) {
        Text(
            stringResource(R.string.tournament_match_play_answer_pending),
            Modifier.testTag("tournament_match_play_answer_pending"),
            color = NoirT3,
        )
    } else {
        NoirButton(
            text = stringResource(R.string.tournament_match_play_answer_submit),
            onClick = component::onSubmitAnswerClick,
            modifier = Modifier.testTag("tournament_match_play_submit"),
            fillWidth = false,
        )
    }
    // Окно AD-31 словами: ответ доставлен (в том числе поздней доставкой), готовность
    // подтверждена квитанцией READY_ACK; край окна — серверный expiresAtMs.
    val window = play.window
    if (!play.awaitingDelivery) {
        when {
            play.readyAckWindowId != null -> {
                Text(
                    stringResource(R.string.tournament_match_play_ready_acked),
                    Modifier.testTag("tournament_match_play_ready_acked"),
                    color = NoirT3,
                )
                WindowNextLine(window, serverNowMs)
            }
            window != null && window.state == WINDOW_OPEN -> {
                Text(
                    stringResource(R.string.tournament_match_play_answer_delivered),
                    Modifier.testTag("tournament_match_play_answer_delivered"),
                    color = NoirT3,
                )
                WindowNextLine(window, serverNowMs)
            }
        }
    }
}

/** Целые секунды до серверного края окна; пересчитывается отрисовкой и перечитыванием.
 * Без серверного «сейчас» строки нет: вычитание из нуля рисует выдуманный счёт эпохи. */
@Composable
private fun WindowNextLine(
    window: TournamentWindowView?,
    serverNowMs: Long?,
) {
    if (window == null || window.state != WINDOW_OPEN) return
    val now = serverNowMs ?: return
    val seconds = ((window.expiresAtMs - now).coerceAtLeast(0L) / 1000L).toInt()
    Text(
        stringResource(R.string.tournament_match_play_window_next, seconds),
        Modifier.testTag("tournament_match_play_window_next"),
        color = NoirT3,
    )
}
