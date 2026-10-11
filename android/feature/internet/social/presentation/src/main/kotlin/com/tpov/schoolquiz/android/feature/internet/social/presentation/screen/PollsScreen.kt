package com.tpov.schoolquiz.android.feature.internet.social.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.internet.social.presentation.R
import com.tpov.schoolquiz.android.feature.internet.social.presentation.component.PollsComponent
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollAuthorDraft
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollsUiMessage
import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollsUiState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollDistribution
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState

/**
 * Поверхность опросов (UX-O7, история 8.2): список серий, вопрос с голосованием, авторская форма.
 * Экран рендерит состояние компонента: скрытое распределение показывается подсказкой, нулевой
 * счёт — нулями; проценты считает презентация по формуле §5 (знаменатель — участники).
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun PollsScreen(
    component: PollsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.polls_title), style = NoirType.groupTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = component::onRefreshClick) {
                    Text(stringResource(R.string.polls_refresh))
                }
                if (state.authoring == null && !state.isQuestionOpen) {
                    Button(onClick = component::onCreateSeriesClick) {
                        Text(stringResource(R.string.polls_create))
                    }
                }
            }
        }

        state.errorMessage?.let { message ->
            Text(
                message.resolve(),
                color = MaterialTheme.colorScheme.error,
                style = NoirType.rowSub,
            )
        }

        val authoring = state.authoring
        when {
            authoring != null -> AuthorForm(component, authoring, state.publishQueued)
            state.isQuestionOpen -> QuestionView(component, state.questionState, state.questionIndex, state)
            else -> SeriesList(component, state)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun SeriesList(
    component: PollsComponent,
    state: PollsUiState,
) {
    when {
        state.isLoading -> CircularProgressIndicator()
        else -> {
            if (state.series.isEmpty() && state.errorMessage == null) {
                Text(stringResource(R.string.polls_empty), style = NoirType.rowSub, color = NoirT3)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.series, key = { it.seriesId }) { series ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(series.title, style = NoirType.rowTitle)
                            Text(
                                stringResource(R.string.polls_progress, series.answeredCount, series.questionCount),
                                style = NoirType.rowSub,
                                color = NoirT3,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (series.own) {
                                    Text(
                                        stringResource(R.string.polls_own_series),
                                        style = NoirType.rowSub,
                                        color = NoirT3,
                                    )
                                } else {
                                    Text("", style = NoirType.rowSub)
                                }
                                OutlinedButton(onClick = { component.onOpenSeriesClick(series.seriesId) }) {
                                    Text(
                                        stringResource(
                                            R.string.polls_question_of,
                                            series.answeredCount + 1,
                                            series.questionCount,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun QuestionView(
    component: PollsComponent,
    snapshot: PollQuestionState?,
    questionIndex: Int,
    state: PollsUiState,
) {
    OutlinedButton(onClick = component::onCloseSeriesClick) {
        Text(stringResource(R.string.polls_back_to_list))
    }
    when {
        snapshot == null && state.questionLoading -> CircularProgressIndicator()
        snapshot == null -> {
            state.questionError?.let { message ->
                Text(message.resolve(), color = MaterialTheme.colorScheme.error, style = NoirType.rowSub)
            }
        }
        else -> {
            Text(
                stringResource(R.string.polls_question_of, questionIndex + 1, snapshot.questionCount),
                style = NoirType.rowSub,
                color = NoirT3,
            )
            Text(snapshot.question.text, style = NoirType.rowTitle)
            snapshot.ownVote?.let { own ->
                Text(
                    stringResource(R.string.polls_your_vote),
                    style = NoirType.rowSub,
                    color = MaterialTheme.colorScheme.primary,
                )
            } ?: state.voteQueued.let { queued ->
                if (queued) {
                    Text(
                        stringResource(R.string.polls_vote_queued),
                        style = NoirType.rowSub,
                        color = NoirT3,
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(snapshot.question.options, key = { it.optionId }) { option ->
                    val selected = option.optionId in state.selectedOptionIds
                    val percent = percentOf(option.optionId, snapshot.distribution)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (snapshot.question.mode) {
                                PollMode.SINGLE ->
                                    RadioButton(selected = selected, onClick = {
                                        if (snapshot.ownVote == null && !state.voteQueued) {
                                            component.onToggleOptionClick(option.optionId)
                                        }
                                    })
                                PollMode.MULTIPLE ->
                                    Checkbox(checked = selected, onCheckedChange = {
                                        if (snapshot.ownVote == null && !state.voteQueued) {
                                            component.onToggleOptionClick(option.optionId)
                                        }
                                    })
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.text, style = NoirType.rowTitle)
                                snapshot.distribution?.let { distribution ->
                                    Text(
                                        "$percent%",
                                        style = NoirType.rowSub,
                                        color = NoirT3,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            when {
                snapshot.ownVote != null -> {
                    snapshot.distribution?.let { distribution ->
                        Text(
                            stringResource(R.string.polls_respondents, distribution.respondentCount),
                            style = NoirType.rowSub,
                            color = NoirT3,
                        )
                    }
                    if (snapshot.questionIndex + 1 < snapshot.questionCount) {
                        Button(onClick = component::onNextQuestionClick) {
                            Text(stringResource(R.string.polls_next))
                        }
                    }
                }
                state.voteQueued -> Unit
                else -> {
                    if (state.selectedOptionIds.isEmpty()) {
                        Text(
                            stringResource(R.string.polls_vote_empty),
                            style = NoirType.rowSub,
                            color = NoirT3,
                        )
                    }
                    Button(onClick = component::onConfirmVoteClick, enabled = state.selectedOptionIds.isNotEmpty()) {
                        Text(stringResource(R.string.polls_vote))
                    }
                }
            }
            if (snapshot.ownVote == null && !state.voteQueued) {
                Text(
                    stringResource(R.string.polls_distribution_hidden),
                    style = NoirType.rowSub,
                    color = NoirT3,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun AuthorForm(
    component: PollsComponent,
    draft: PollAuthorDraft,
    publishQueued: Boolean,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = draft.title,
                onValueChange = component::onAuthorTitleChange,
                label = { Text(stringResource(R.string.polls_title_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(draft.questions.size) { questionIndex ->
            val question = draft.questions[questionIndex]
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.polls_question_label, questionIndex + 1),
                            style = NoirType.rowTitle,
                        )
                        if (draft.questions.size > 1) {
                            TextButton(onClick = { component.onAuthorRemoveQuestionClick(questionIndex) }) {
                                Text(stringResource(R.string.polls_remove_question))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = question.text,
                        onValueChange = { component.onAuthorQuestionTextChange(questionIndex, it) },
                        label = { Text(stringResource(R.string.polls_question_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = question.mode == PollMode.SINGLE,
                            onClick = { component.onAuthorModeChange(questionIndex, PollMode.SINGLE) },
                            label = { Text(stringResource(R.string.polls_mode_single)) },
                        )
                        FilterChip(
                            selected = question.mode == PollMode.MULTIPLE,
                            onClick = { component.onAuthorModeChange(questionIndex, PollMode.MULTIPLE) },
                            label = { Text(stringResource(R.string.polls_mode_multiple)) },
                        )
                    }
                    question.optionTexts.forEachIndexed { optionIndex, optionText ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = optionText,
                                onValueChange = { component.onAuthorOptionTextChange(questionIndex, optionIndex, it) },
                                label = { Text(stringResource(R.string.polls_option_hint, optionIndex + 1)) },
                                modifier = Modifier.weight(1f),
                            )
                            if (question.optionTexts.size > 2) {
                                TextButton(
                                    onClick = { component.onAuthorRemoveOptionClick(questionIndex, optionIndex) },
                                ) {
                                    Text(stringResource(R.string.polls_remove_option))
                                }
                            }
                        }
                    }
                    if (question.optionTexts.size < 8) {
                        OutlinedButton(onClick = { component.onAuthorAddOptionClick(questionIndex) }) {
                            Text(stringResource(R.string.polls_add_option))
                        }
                    }
                }
            }
        }
        item {
            if (draft.questions.size < 50) {
                OutlinedButton(onClick = component::onAuthorAddQuestionClick) {
                    Text(stringResource(R.string.polls_add_question))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = component::onPublishClick, enabled = !publishQueued) {
                    Text(stringResource(R.string.polls_publish))
                }
                OutlinedButton(onClick = component::onAuthorCancelClick) {
                    Text(stringResource(R.string.polls_cancel))
                }
            }
            if (publishQueued) {
                Text(stringResource(R.string.polls_publish_queued), style = NoirType.rowSub, color = NoirT3)
            }
        }
    }
}

private fun percentOf(
    optionId: String,
    distribution: PollDistribution?,
): Int {
    distribution ?: return 0
    if (distribution.respondentCount <= 0) return 0
    val count = distribution.optionCounts[optionId] ?: 0
    return 100 * count / distribution.respondentCount
}

@Composable
private fun PollsUiMessage.resolve(): String =
    when (this) {
        is PollsUiMessage.Res -> stringResource(id)
        is PollsUiMessage.Raw -> value
    }
