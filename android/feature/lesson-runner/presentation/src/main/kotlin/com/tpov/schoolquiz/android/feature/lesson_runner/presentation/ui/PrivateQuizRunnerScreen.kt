package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.components.BrandPrimaryButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.PrivateQuizRunnerComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.PrivateQuizRunUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntryRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizOwnResult
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

/**
 * Экран прогона приватного квиза (AD-29/UX-16, история 8.4): дверь → вопросы одного захода →
 * подтверждение/результат. Вопрос рендерится теми же семьями компонентов, что и обычный ранер
 * (QuestionFrame/QuestionTypeContent), часы — исходные, без продления (AD-31).
 */
@Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")
@Composable
fun PrivateQuizRunnerScreen(
    component: PrivateQuizRunnerComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle()
    LaunchedEffect(component) {
        component.events.collect { event ->
            when (event) {
                RunnerEvent.NavigateBack -> Unit // хост закрывает экран по своему стеку
                else -> Unit
            }
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = component.title, style = NoirType.groupTitle, color = NoirT1)
        when (val current = state) {
            PrivateQuizRunUiState.DoorChecking -> Centered { CircularProgressIndicator() }

            is PrivateQuizRunUiState.Refused -> RefusedCard(current.reason)

            is PrivateQuizRunUiState.Question -> {
                QuestionTimer(current.deadlineMs, nowMs = component::nowMs, onTimeout = component::onTimeout)
                PrivateQuestionBody(
                    state = current,
                    onDraftChanged = component::onDraftChanged,
                    onAnswer = component::onAnswer,
                )
            }

            PrivateQuizRunUiState.Submitting -> Centered {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.private_run_submitting),
                    style = NoirType.rowSub,
                    color = NoirT2,
                )
            }

            PrivateQuizRunUiState.ResultPending -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.private_run_result_pending),
                    style = NoirType.rowSub,
                    color = NoirT2,
                )
                TextButton(onClick = component::onRefreshResult) {
                    Text(stringResource(R.string.private_run_refresh))
                }
            }

            is PrivateQuizRunUiState.ReadFailed -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            when (current.source) {
                                PrivateQuizRunUiState.ReadFailed.FailureSource.ENQUEUE -> R.string.private_run_submit_failed
                                PrivateQuizRunUiState.ReadFailed.FailureSource.READ -> R.string.private_run_read_failed
                            },
                        ),
                    color = NoirDanger,
                    style = NoirType.rowSub,
                )
                TextButton(onClick = component::onRefreshResult) {
                    Text(
                        text =
                            stringResource(
                                when (current.source) {
                                    // Неудавшаяся постановка переезжает той же подачей и тем же ключом.
                                    PrivateQuizRunUiState.ReadFailed.FailureSource.ENQUEUE -> R.string.private_run_resubmit
                                    PrivateQuizRunUiState.ReadFailed.FailureSource.READ -> R.string.private_run_refresh
                                },
                            ),
                    )
                }
            }

            is PrivateQuizRunUiState.Finished -> FinishedCard(current.result)
        }
    }
}

@Composable
private fun RefusedCard(reason: PrivateQuizEntryRefusal) {
    BrandCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.private_run_refused_title),
                color = NoirDanger,
                style = NoirType.rowSub,
            )
            Text(
                text =
                    when (reason) {
                        PrivateQuizEntryRefusal.LINK_DISABLED -> stringResource(R.string.private_run_link_disabled)
                        PrivateQuizEntryRefusal.LINK_EXPIRED -> stringResource(R.string.private_run_link_expired)
                        PrivateQuizEntryRefusal.ATTEMPT_LIMIT_REACHED -> stringResource(R.string.private_run_limit_reached)
                        PrivateQuizEntryRefusal.NOT_FOUND -> stringResource(R.string.private_run_not_found)
                        PrivateQuizEntryRefusal.RUN_CONTENT_CHANGED -> stringResource(R.string.private_run_content_changed)
                        PrivateQuizEntryRefusal.ATTEMPT_TERMINAL -> stringResource(R.string.private_run_terminal)
                        PrivateQuizEntryRefusal.AUTH_REQUIRED -> stringResource(R.string.private_run_auth_required)
                        PrivateQuizEntryRefusal.UNAVAILABLE -> stringResource(R.string.private_run_unavailable)
                    },
                style = NoirType.rowSub,
                color = NoirT1,
            )
        }
    }
}

@Composable
private fun QuestionTimer(
    deadlineMs: Long,
    nowMs: () -> Long,
    onTimeout: () -> Unit,
) {
    // Часы вопроса — часы машины (один источник с расписанием, AD-31); ни одна кнопка их
    // не двигает и не дублирует.
    var expired by remember(deadlineMs) { mutableStateOf(false) }
    LaunchedEffect(deadlineMs) {
        val wait = deadlineMs - nowMs()
        if (wait > 0) {
            kotlinx.coroutines.delay(wait)
        }
        if (!expired) {
            expired = true
            onTimeout()
        }
    }
    if (expired) {
        Text(
            text = stringResource(R.string.private_run_question_expired),
            style = NoirType.rowSub,
            color = NoirDanger,
        )
    }
}

@Composable
private fun PrivateQuestionBody(
    state: PrivateQuizRunUiState.Question,
    onDraftChanged: (UserAnswerDraft) -> Unit,
    onAnswer: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.private_run_progress, state.indexInPool + 1, state.totalInPool),
            style = NoirType.rowSub,
            color = NoirT2,
        )
        QuestionTypeContent(
            qState = state.question,
            currentDraft = state.currentDraft,
            feedback = null,
            revealCorrect = false,
            interaction =
                QuestionInteraction(
                    readDraft = { state.currentDraft },
                    changeDraft = onDraftChanged,
                    selectAnswer = onDraftChanged,
                    hint =
                        HintControl(
                            enabled = false,
                            // Приватный проход бесплатен: подсказок и платных пропусков не существует (8.5).
                            spend = { false },
                        ),
                    feedback = { Unit },
                ),
        )
        BrandPrimaryButton(
            text = stringResource(R.string.private_run_answer),
            onClick = onAnswer,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FinishedCard(result: PrivateQuizOwnResult) {
    BrandCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.private_run_finished),
                style = NoirType.groupTitle,
                color = NoirT1,
            )
            if (result.showsScore) {
                Text(
                    text =
                        stringResource(
                            R.string.private_run_score,
                            result.correctCount ?: 0,
                            result.questionCount ?: 0,
                            result.percentScore ?: 0,
                        ),
                    style = NoirType.rowSub,
                    color = NoirT1,
                )
            } else {
                // showResult=false: только подтверждение завершения — никаких нулей и прочерков
                // вместо скрытых чисел (AD-29/AD-21: неизвестное — не ноль).
                Text(
                    text = stringResource(R.string.private_run_completion_only),
                    style = NoirType.rowSub,
                    color = NoirT2,
                )
            }
            if (result.reason != null) {
                Text(
                    text =
                        stringResource(
                            if (result.reason == com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptReason.TIMEOUT) {
                                R.string.private_run_reason_timeout
                            } else {
                                R.string.private_run_reason_finished
                            },
                        ),
                    style = NoirType.rowSub,
                    color = NoirT2,
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
