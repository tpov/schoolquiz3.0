@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChrome
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassFill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeXl
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.CopyEditLoadState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.CopyEditRowState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.CopyEditSheetState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.CopyEditSubmissionState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.ErrorReportSubmissionState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ResultAdvice
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptSyncMark
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptReward
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.EditCopyMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote

// Accuracy chart geometry, lifted from the design's SVG (viewBox 340×120 rendered at 104×44):
// the plot spans x 10..330, score digits 1..9 sit on nine levels 11.5 units apart with '9' on
// y=14, and hairline gridlines cross at y=14/60/106.

// Chart paint, straight from the design: success wash under the line (0.10), danger wash above
// it (0.08), the stroke at three-quarter accent with a heavier head dot.

/**
 * The end of an attempt.
 *
 * One glass card holds the score, the personal best it is measured against, and the four figures
 * that changed. Below it the leaderboard, and at the foot two words instead of two buttons —
 * running again and moving on are both ordinary, and neither deserves a filled slab.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun ResultContent(
    state: RunnerUiState.Result,
    comments: List<LessonComment>,
    onSubmitRating: (Int) -> Unit,
    onRunAgain: () -> Unit,
    onNextLesson: () -> Unit,
    onPostComment: (String) -> Unit,
    onSyncResult: () -> Unit,
    /** Цитата платы предложения (4.22/4.23); null — не приходила/сервер недоступен («—»). */
    proposalFeeQuote: LessonProposalFeeQuote?,
    errorReportState: ErrorReportSubmissionState,
    onReportDialogOpened: () -> Unit,
    onReportError: (String, String?) -> Unit,
    /** «Предложить правку» (4.23): шторка редактора копии без секретных ключей. */
    copyEditSheetState: CopyEditSheetState,
    onCopyEditDialogOpened: () -> Unit,
    onCopyEditMarkChanged: (String, EditCopyMark?) -> Unit,
    onCopyEditSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHard = state.mode == Difficulty.HARD
    var discussionOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var copyEditOpen by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text =
                stringResource(
                    if (isHard) R.string.runner_result_kicker_hard else R.string.runner_result_kicker_easy,
                ).uppercase(),
            style = NoirType.kicker,
        )

        ResultScoreCard(
            state = state,
        )

        if (state.saveWarning) {
            Text(
                stringResource(R.string.runner_result_save_warning),
                style = NoirType.rowSub.copy(color = NoirDanger),
            )
        }

        // Пометка очереди на самом прохождении (AD-14). Молчание до сих пор значило и «уехало», и
        // «не уедет никогда» — на карточке результата это два разных исхода и две разные строки.
        // `when` исчерпывающий и без `else`: новое значение набора не соберётся молча.
        when (state.syncMark) {
            AttemptSyncMark.NONE -> Unit
            AttemptSyncMark.NOT_SENT ->
                Text(
                    stringResource(R.string.runner_result_sync_not_sent),
                    style = NoirType.rowSub.copy(color = NoirT3),
                )
            AttemptSyncMark.QUARANTINED ->
                Text(
                    stringResource(R.string.runner_result_sync_quarantined),
                    style = NoirType.rowSub.copy(color = NoirDanger),
                )
        }

        if (state.canRequestSync) {
            TextButton(onClick = onSyncResult) {
                Text(stringResource(R.string.runner_result_sync_now), style = NoirType.button)
            }
        }

        if (state.canSubmitRating) {
            RatingPromptSection(
                ratingSubmissionState = state.ratingSubmissionState,
                onSubmitRating = onSubmitRating,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.top3.isNotEmpty()) {
            Top3Section(top3 = state.top3)
        }

        DiscussionRow(count = comments.size, onOpen = { discussionOpen = true })

        // «Сообщить об ошибке» — предложение автору (CAP-6/27, UX-23): текст про весь урок и
        // (по желанию) свой вариант; проходит проверки 4.21 до показа автору.
        ErrorReportRow(
            onOpen = {
                reportOpen = true
                onReportDialogOpened()
            },
        )

        // «Предложить правку» — второй вход CAP-27 (4.23, UX-23): копия урока без секретных
        // ключей, предложивший отмечает верные ответы, «Факты» проверяет отметки.
        CopyEditRow(
            onOpen = {
                copyEditOpen = true
                onCopyEditDialogOpened()
            },
        )

        // Existing advice is local. Show it only when the server confirms the exact same answers.
        if (state.serverSettlement.isConfirmedCompletion &&
            state.serverSettlement.codeAnswer?.raw?.map { it - '0' } == state.questionScores
        ) {
            state.advice?.let { AdviceSection(advice = it) }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.runner_result_again),
                style = NoirType.button.copy(color = NoirT3),
                modifier = Modifier.clickable(onClick = onRunAgain),
            )
            Text(
                stringResource(R.string.runner_result_next),
                style = NoirType.button.copy(color = LocalNoirAccent.current),
                modifier = Modifier.clickable(onClick = onNextLesson),
            )
        }
    }

    if (discussionOpen) {
        DiscussionSheet(
            comments = comments,
            onPostComment = onPostComment,
            onDismiss = { discussionOpen = false },
        )
    }

    if (reportOpen) {
        ErrorReportSheet(
            proposalFeeQuote = proposalFeeQuote,
            submissionState = errorReportState,
            onSubmit = onReportError,
            onDismiss = { reportOpen = false },
        )
    }

    if (copyEditOpen) {
        CopyEditSheet(
            sheetState = copyEditSheetState,
            proposalFeeQuote = proposalFeeQuote,
            onMarkChanged = onCopyEditMarkChanged,
            onSubmit = onCopyEditSubmit,
            onDismiss = { copyEditOpen = false },
        )
    }
}

/**
 * Where the points went, and what to read to get them next time.
 *
 * A percentage tells somebody how it went and nothing about what to do about it. Naming the count
 * of weak answers turns the score into something specific, and the prerequisite lesson turns it
 * into a next step — which is the difference between a result screen and a verdict.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun AdviceSection(advice: ResultAdvice) {
    val accent = LocalNoirAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NoirShapeLg)
            .background(NoirGlassFill)
            .border(1.dp, NoirGlassStroke, NoirShapeLg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(stringResource(R.string.runner_advice_kicker), style = NoirType.kicker.copy(color = NoirT2))
        Text(
            text =
                pluralStringResource(
                    R.plurals.runner_advice_weak_answers,
                    advice.weakAnswers,
                    advice.weakAnswers,
                ),
            style = NoirType.rowSub.copy(color = NoirT2),
        )
        advice.suggestedLessonTitle?.let { title ->
            Text(
                text = stringResource(R.string.runner_advice_suggested_lesson, title),
                style = NoirType.rowSub.copy(color = accent),
            )
        }
    }
}

/**
 * The discussion, as one row: how many have said something, and a way in.
 *
 * A door, not the room. The thread itself is a modal — on a result screen the comments are the
 * least of what somebody came for, and inline they push the actions off the bottom.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DiscussionRow(
    count: Int,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .defaultMinSize(minHeight = 60.dp)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.runner_discussion_title),
                style = NoirType.rowTitle.copy(fontSize = 15.sp, color = NoirT1),
            )
            Text(
                text = discussionCountWording(count),
                style = NoirType.rowSub.copy(fontSize = 12.5.sp, color = NoirT2),
            )
        }
        Text(
            stringResource(R.string.runner_discussion_open),
            style = NoirType.num.copy(fontSize = 12.sp, color = LocalNoirAccent.current),
        )
    }
}

/** "1 комментарий", "2 комментария", "5 комментариев" — and the teens count like many. */
private fun discussionCountWording(count: Int): String {
    if (count == 0) return "Пока никто не написал"
    val tail = count % 100
    val last = count % 10
    val teens = tail in 11..14
    val noun =
        when {
            teens -> "комментариев"
            last == 1 -> "комментарий"
            last in 2..4 -> "комментария"
            else -> "комментариев"
        }
    return "$count $noun"
}

/**
 * The score, as the drawing builds it: a lifted plate with a wash coming out of its top-left
 * corner, one very large figure, the mark to beat under it, and the rewards along the foot.
 *
 * The plate is a fill step rather than an outlined card. The set this comes from dropped strokes
 * everywhere — at this palette a 1px white edge reads grey and chops the screen into boxes — so
 * depth here is carried by how light the fill is and by the shadow under it.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultScoreCard(state: RunnerUiState.Result) {
    val settlement = state.serverSettlement
    val tone = if (settlement.isConfirmedCompletion) NoirSuccess else NoirT3
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NoirShapeXl)
            .background(NoirChrome)
            .drawBehind {
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(tone.copy(alpha = 0.32f), Color.Transparent),
                            center = Offset.Zero,
                            radius = size.maxDimension * SCORE_WASH_REACH,
                        ),
                )
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = state.displayedPercentScore?.raw?.toString() ?: "—",
                    style = NoirType.num.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold, color = NoirT1),
                )
                if (state.displayedPercentScore != null) {
                    Text(
                        "%",
                        style = NoirType.num.copy(fontSize = 20.sp, color = NoirT2),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Text(
                text = stringResource(settlementStatusText(settlement)),
                style = NoirType.rowSub.copy(color = tone),
            )
        }
        if (settlement.hasAccountingProof) ResultRewardStrip(state)
    }
}

/**
 * What the run paid, along the foot of the card.
 *
 * Sunk rather than raised — a darker wash with a light hairline over it — so it reads as the base
 * of the card and not as three more things to tap.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ResultRewardStrip(state: RunnerUiState.Result) {
    val reward = state.displayedReward ?: return
    val settlement = state.serverSettlement
    val lifePointsSpent = settlement.lifePointsSpent ?: return
    val plasmaPointsSpent = settlement.plasmaPointsSpent ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f))
            .drawBehind {
                drawRect(color = NoirGlassStroke, size = Size(size.width, 1.dp.toPx()))
            },
    ) {
        Row(Modifier.fillMaxWidth()) {
            RewardCell(
                NoirIcons.Star,
                "+${reward.skillPoints}",
                LocalNoirAccent.current,
                stringResource(R.string.runner_settlement_skill_points),
            )
            RewardCell(
                NoirIcons.Nolic,
                "+${reward.nolics}",
                LocalNoirAccent.current,
                stringResource(R.string.runner_settlement_nolics),
            )
        }
        Text(
            stringResource(R.string.runner_settlement_charges, lifePointsSpent, plasmaPointsSpent),
            style = NoirType.rowSub.copy(color = NoirT2),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RowScope.RewardCell(
    icon: ImageVector,
    value: String,
    tint: Color,
    label: String,
) {
    Row(
        Modifier
            .weight(1f)
            .drawBehind {
                drawRect(color = NoirHair, size = Size(1.dp.toPx(), size.height))
            }
            .padding(horizontal = 8.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Text(value, style = NoirType.num.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NoirT1))
    }
}

/**
 * «Сообщить об ошибке» — вход предложения (UX-23), как строка-дверь рядом с обсуждением.
 * null-хост (без авторинг-графа) строку не показывает: мёртвой кнопки не бывает.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ErrorReportRow(
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .defaultMinSize(minHeight = 60.dp)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.runner_report_title),
                style = NoirType.rowTitle.copy(fontSize = 15.sp, color = NoirT1),
            )
            Text(
                stringResource(R.string.runner_report_subtitle),
                style = NoirType.rowSub.copy(fontSize = 12.5.sp, color = NoirT2),
            )
        }
        Text(
            stringResource(R.string.runner_report_open),
            style = NoirType.num.copy(fontSize = 12.sp, color = LocalNoirAccent.current),
        )
    }
}

/**
 * Диалог отчёта: текст про весь урок, сворачиваемое поле «свой вариант», строка платы из цитаты
 * («—», пока цитаты нет) и «Предложить автору». Границы текста — серверные; use case дублирует
 * их до постановки в очередь, длинный текст просто не отправится.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ErrorReportSheet(
    proposalFeeQuote: LessonProposalFeeQuote?,
    submissionState: ErrorReportSubmissionState,
    onSubmit: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        var text by remember { mutableStateOf("") }
        var variant by remember { mutableStateOf("") }
        var variantVisible by remember { mutableStateOf(false) }
        val accent = LocalNoirAccent.current
        val trimmed = text.trim()
        val canSend =
            trimmed.isNotEmpty() &&
                trimmed.length <= MAX_REPORT_CHARS &&
                submissionState != ErrorReportSubmissionState.Sending
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = stringResource(R.string.runner_report_header),
                style = NoirType.kicker.copy(color = NoirT2),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .clip(NoirShapeMd)
                    .background(NoirS2)
                    .border(1.dp, NoirOutline, NoirShapeMd)
                    .padding(12.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.runner_report_placeholder),
                        style = NoirType.rowSub.copy(color = NoirTOff),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = NoirType.rowSub.copy(color = NoirT1),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (variantVisible) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(NoirShapeMd)
                        .background(NoirS2)
                        .border(1.dp, NoirOutline, NoirShapeMd)
                        .padding(12.dp),
                ) {
                    if (variant.isEmpty()) {
                        Text(
                            text = stringResource(R.string.runner_report_variant_placeholder),
                            style = NoirType.rowSub.copy(color = NoirTOff),
                        )
                    }
                    BasicTextField(
                        value = variant,
                        onValueChange = { variant = it },
                        textStyle = NoirType.rowSub.copy(color = NoirT1),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.runner_report_add_variant),
                    style = NoirType.rowSub.copy(color = NoirT2),
                    modifier = Modifier.clickable { variantVisible = true },
                )
            }
            // Плата до отправки (UX-23): показ — не власть, плату считает приёмка (4.21).
            Text(
                text =
                    if (proposalFeeQuote == null) {
                        stringResource(R.string.runner_report_fee_unknown)
                    } else if (proposalFeeQuote.feeNolics == 0) {
                        stringResource(R.string.runner_report_fee_free)
                    } else {
                        stringResource(R.string.runner_report_fee_value, proposalFeeQuote.feeNolics)
                    },
                style = NoirType.rowSub.copy(color = NoirT2),
            )
            when (submissionState) {
                ErrorReportSubmissionState.Failed ->
                    Text(
                        text = stringResource(R.string.runner_report_failed),
                        style = NoirType.rowSub.copy(color = NoirDanger),
                    )
                ErrorReportSubmissionState.Done -> Unit
                ErrorReportSubmissionState.Idle -> Unit
                ErrorReportSubmissionState.Sending -> Unit
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.runner_report_cancel),
                    style = NoirType.button.copy(color = NoirT3),
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
                Text(
                    text =
                        if (submissionState == ErrorReportSubmissionState.Sending) {
                            stringResource(R.string.runner_report_sending)
                        } else {
                            stringResource(R.string.runner_report_send)
                        },
                    style = NoirType.button.copy(color = if (canSend) accent else NoirTOff),
                    modifier =
                        Modifier.clickable(enabled = canSend) {
                            onSubmit(trimmed, variant.trim().takeUnless { it.isEmpty() })
                        },
                )
            }
        }
    }
}

private const val MAX_REPORT_CHARS = 2000

/** Строка-вход «Предложить правку» (4.23, UX-23): копия без секретных ключей, отметки — за игроком. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun CopyEditRow(
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .defaultMinSize(minHeight = 60.dp)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.runner_copyedit_title),
                style = NoirType.rowTitle.copy(fontSize = 15.sp, color = NoirT1),
            )
            Text(
                stringResource(R.string.runner_copyedit_subtitle),
                style = NoirType.rowSub.copy(fontSize = 12.5.sp, color = NoirT2),
            )
        }
        Text(
            stringResource(R.string.runner_report_open),
            style = NoirType.num.copy(fontSize = 12.sp, color = LocalNoirAccent.current),
        )
    }
}

/**
 * Шторка «Предложить правку» (4.23): копия урока строками; редактируемые — публичные половины
 * без ключа, предложивший отмечает верный ответ сам; запертые (Survey, легаси) показываются
 * как есть и в предложение не едут. Плата — цитата COPY_EDIT × изменённые; показ, не власть
 * (платит приёмка 4.21 из тела подачи).
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun CopyEditSheet(
    sheetState: CopyEditSheetState,
    proposalFeeQuote: LessonProposalFeeQuote?,
    onMarkChanged: (String, EditCopyMark?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val accent = LocalNoirAccent.current
        val editedCount = sheetState.editedCount
        val hasInvalidMark = sheetState.rows.any { it.mark != null && !it.marked }
        val canSend = editedCount > 0 && !hasInvalidMark &&
            sheetState.submission != CopyEditSubmissionState.Sending
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = stringResource(R.string.runner_copyedit_header),
                style = NoirType.kicker.copy(color = NoirT2),
            )
            when (sheetState.load) {
                CopyEditLoadState.Loading, CopyEditLoadState.Idle ->
                    Text(
                        text = stringResource(R.string.runner_copyedit_loading),
                        style = NoirType.rowSub.copy(color = NoirT2),
                    )
                CopyEditLoadState.Failed ->
                    Text(
                        text = stringResource(R.string.runner_copyedit_load_failed),
                        style = NoirType.rowSub.copy(color = NoirDanger),
                    )
                CopyEditLoadState.Loaded ->
                    sheetState.rows.forEach { rowState ->
                        CopyEditQuestionRow(
                            rowState = rowState,
                            onMarkChanged = { mark -> onMarkChanged(rowState.row.id, mark) },
                        )
                    }
            }
            if (hasInvalidMark) {
                Text(
                    text = stringResource(R.string.runner_copyedit_need_two),
                    style = NoirType.rowSub.copy(color = NoirDanger),
                )
            }
            Text(
                text = stringResource(R.string.runner_copyedit_edited, editedCount),
                style = NoirType.rowSub.copy(color = NoirT2),
            )
            // Плата до отправки (UX-23): показ — не власть, плату считает приёмка (4.21).
            Text(
                text =
                    if (proposalFeeQuote == null || editedCount == 0) {
                        stringResource(R.string.runner_report_fee_unknown)
                    } else if (proposalFeeQuote.feeNolics == 0) {
                        stringResource(R.string.runner_report_fee_free)
                    } else {
                        stringResource(R.string.runner_report_fee_value, proposalFeeQuote.feeNolics)
                    },
                style = NoirType.rowSub.copy(color = NoirT2),
            )
            when (sheetState.submission) {
                CopyEditSubmissionState.Failed ->
                    Text(
                        text = stringResource(R.string.runner_report_failed),
                        style = NoirType.rowSub.copy(color = NoirDanger),
                    )
                CopyEditSubmissionState.Done ->
                    Text(
                        text = stringResource(R.string.runner_copyedit_sent),
                        style = NoirType.rowSub.copy(color = NoirSuccess),
                    )
                CopyEditSubmissionState.Idle -> Unit
                CopyEditSubmissionState.Sending -> Unit
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.runner_report_cancel),
                    style = NoirType.button.copy(color = NoirT3),
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
                Text(
                    text =
                        if (sheetState.submission == CopyEditSubmissionState.Sending) {
                            stringResource(R.string.runner_report_sending)
                        } else {
                            stringResource(R.string.runner_report_send)
                        },
                    style = NoirType.button.copy(color = if (canSend) accent else NoirTOff),
                    modifier = Modifier.clickable(enabled = canSend, onClick = onSubmit),
                )
            }
        }
    }
}

/** Строка вопроса копии: текст, метка редактируемости и — по раскрытию — отметка ответа. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming", "CyclomaticComplexMethod")
@Composable
private fun CopyEditQuestionRow(
    rowState: CopyEditRowState,
    onMarkChanged: (EditCopyMark?) -> Unit,
) {
    var expanded by remember(rowState.row.id) { mutableStateOf(false) }
    val editable = rowState.row.editable && rowState.parsed != null
    val accent = LocalNoirAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NoirShapeMd)
            .background(NoirS2)
            .border(1.dp, NoirOutline, NoirShapeMd)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rowState.row.text,
                style = NoirType.rowSub.copy(color = NoirT1),
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            Text(
                text =
                    when {
                        !editable -> stringResource(R.string.runner_copyedit_locked)
                        rowState.marked -> stringResource(R.string.runner_copyedit_marked)
                        else -> stringResource(R.string.runner_copyedit_editable)
                    },
                style = NoirType.num.copy(
                    fontSize = 11.sp,
                    color = when {
                        !editable -> NoirTOff
                        rowState.marked -> NoirSuccess
                        else -> accent
                    },
                ),
            )
        }
        if (editable) {
            Text(
                text = stringResource(R.string.runner_copyedit_edit_hint),
                style = NoirType.num.copy(fontSize = 11.sp, color = NoirT2),
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
        if (expanded && rowState.parsed != null) {
            MarkingEditor(
                parsed = rowState.parsed,
                mark = rowState.mark,
                onMarkChanged = onMarkChanged,
            )
        }
    }
}

/** Отметка верного ответа по типу публичной половины; запертые строки сюда не попадают. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming", "CyclomaticComplexMethod")
@Composable
private fun MarkingEditor(
    parsed: RedactedQuestionContent,
    mark: EditCopyMark?,
    onMarkChanged: (EditCopyMark?) -> Unit,
) {
    val accent = LocalNoirAccent.current
    when (parsed) {
        is RedactedQuestionContent.SingleChoice -> {
            val selected = (mark as? EditCopyMark.SingleChoice)?.correctOptionId
            parsed.options.forEach { option ->
                val isSelected = option.id == selected
                Text(
                    text = ("● ").takeIf { isSelected }.orEmpty() + option.text,
                    style = NoirType.rowSub.copy(color = if (isSelected) accent else NoirT1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onMarkChanged(
                                if (isSelected) null else EditCopyMark.SingleChoice(option.id),
                            )
                        },
                )
            }
        }
        is RedactedQuestionContent.MultipleChoice -> {
            val selected = (mark as? EditCopyMark.MultipleChoice)?.correctOptionIds ?: emptySet()
            parsed.options.forEach { option ->
                val isSelected = option.id in selected
                Text(
                    text = ("✓ ").takeIf { isSelected }.orEmpty() + option.text,
                    style = NoirType.rowSub.copy(color = if (isSelected) accent else NoirT1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = selected.toMutableSet().apply {
                                if (isSelected) remove(option.id) else add(option.id)
                            }
                            onMarkChanged(
                                if (next.isEmpty()) null else EditCopyMark.MultipleChoice(next),
                            )
                        },
                )
            }
        }
        is RedactedQuestionContent.Ordering -> {
            val orderedIds = (mark as? EditCopyMark.Ordering)?.orderedItemIds
                ?: parsed.items.map { it.id }
            val textById = parsed.items.associate { it.id to it.text }
            orderedIds.forEachIndexed { index, itemId ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}. ${textById[itemId].orEmpty()}",
                        style = NoirType.rowSub.copy(color = NoirT1),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "↑",
                        style = NoirType.button.copy(color = if (index > 0) accent else NoirTOff),
                        modifier = Modifier.clickable(enabled = index > 0) {
                            val next = orderedIds.toMutableList().apply {
                                removeAt(index)
                                add(index - 1, itemId)
                            }
                            onMarkChanged(EditCopyMark.Ordering(next))
                        },
                    )
                    Text(
                        text = "↓",
                        style = NoirType.button.copy(
                            color = if (index < orderedIds.lastIndex) accent else NoirTOff,
                        ),
                        modifier = Modifier.clickable(enabled = index < orderedIds.lastIndex) {
                            val next = orderedIds.toMutableList().apply {
                                removeAt(index)
                                add(index + 1, itemId)
                            }
                            onMarkChanged(EditCopyMark.Ordering(next))
                        },
                    )
                }
            }
        }
        is RedactedQuestionContent.FillBlank -> {
            val marked = (mark as? EditCopyMark.FillBlank)?.correctCandidateByBlank ?: emptyMap()
            parsed.blanks.forEachIndexed { blankIndex, blankId ->
                Text(
                    text = stringResource(R.string.runner_copyedit_blank, blankIndex + 1),
                    style = NoirType.num.copy(fontSize = 11.sp, color = NoirT2),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    parsed.candidates.forEach { candidate ->
                        val isSelected = marked[blankId] == candidate.id
                        Text(
                            text = candidate.text,
                            style = NoirType.rowSub.copy(
                                color = when {
                                    isSelected -> accent
                                    candidate.id in marked.values -> NoirTOff
                                    else -> NoirT1
                                },
                            ),
                            modifier = Modifier
                                .clip(NoirShapeMd)
                                .border(1.dp, if (isSelected) accent else NoirOutline, NoirShapeMd)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable {
                                    val next = marked.toMutableMap()
                                    if (isSelected) next.remove(blankId) else next[blankId] = candidate.id
                                    onMarkChanged(
                                        if (next.isEmpty()) null else EditCopyMark.FillBlank(next),
                                    )
                                },
                        )
                    }
                }
            }
        }
    }
}

/** How far the corner wash reaches across the card, as a share of its longest side. */
private const val SCORE_WASH_REACH = 1.32f

/**
 * The lesson's discussion: the comments people left, and a one-line field to add one.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DiscussionSheet(
    comments: List<LessonComment>,
    onPostComment: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DiscussionBody(comments = comments, onPostComment = onPostComment)
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DiscussionBody(
    comments: List<LessonComment>,
    onPostComment: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val accent = LocalNoirAccent.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.runner_discussion_header),
                style = NoirType.kicker.copy(color = NoirT2),
            )
            Text(
                text = comments.size.toString(),
                style = NoirType.kicker.copy(color = NoirT2),
            )
        }
        comments.forEach { comment -> CommentRow(comment) }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(NoirShapeMd)
                    .background(NoirS2)
                    .border(1.dp, NoirOutline, NoirShapeMd)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = stringResource(R.string.runner_discussion_placeholder),
                        style = NoirType.rowSub.copy(color = NoirTOff),
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = NoirType.rowSub.copy(color = NoirT1),
                    singleLine = true,
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.runner_discussion_send),
                style = NoirType.button.copy(color = accent),
                modifier =
                    Modifier.clickable {
                        if (draft.isNotBlank()) {
                            onPostComment(draft)
                            draft = ""
                        }
                    },
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun CommentRow(comment: LessonComment) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(NoirShapePill)
                .background(NoirS3)
                .border(1.dp, NoirOutline, NoirShapePill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = comment.authorNickname.take(1).uppercase(),
                style = NoirType.kicker.copy(color = NoirT2),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = comment.authorNickname,
                style = NoirType.rowTitle.copy(fontSize = 12.5.sp),
            )
            Text(
                text = comment.text,
                style = NoirType.rowSub.copy(color = NoirT2),
            )
        }
    }
}

internal fun settlementStatusText(settlement: ServerAttemptSettlement): Int =
    when (settlement.state) {
        ServerAttemptSettlementState.PENDING -> R.string.runner_settlement_pending
        ServerAttemptSettlementState.SETTLED ->
            when {
                settlement.isConfirmedCompletion -> R.string.runner_settlement_confirmed
                settlement.hasAccountingProof -> R.string.runner_settlement_accounting_only
                else -> R.string.runner_settlement_invalid
            }
        ServerAttemptSettlementState.UNSCORABLE -> R.string.runner_settlement_unscorable
        ServerAttemptSettlementState.REJECTED -> R.string.runner_settlement_rejected
        ServerAttemptSettlementState.LEGACY_RECORDED -> R.string.runner_settlement_legacy
        ServerAttemptSettlementState.INVALID_RECEIPT -> R.string.runner_settlement_invalid
        ServerAttemptSettlementState.ACCOUNT_UNAVAILABLE -> R.string.runner_settlement_account_unavailable
    }

@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun ResultContentPreview() {
    SchoolQuizTheme {
        ResultContent(
            state =
                RunnerUiState.Result(
                    percentScore = PercentScore(85),
                    serverSettlement =
                        ServerAttemptSettlement(
                            state = ServerAttemptSettlementState.SETTLED,
                            receiptId = "preview-receipt",
                            codeAnswer = CodeAnswer("9"),
                            percentScore = PercentScore(100),
                            completed = true,
                            settledAtMs = 0L,
                            lifePointsSpent = 50L,
                            plasmaPointsSpent = 0L,
                            reward = ServerAttemptReward(skillPoints = 100L, nolics = 10L),
                            ownerVerified = true,
                        ),
                    mode = Difficulty.EASY,
                    completedAt = 0L,
                    hardUnlocked = false,
                    bestStarsRawTenths = 20,
                    currentAttemptStarsRawTenths = 17,
                    lessonAverageRating = 2.5f,
                    lessonRatingCount = 10,
                    top3 =
                        listOf(
                            TopParticipant("Alice", null, 95),
                            TopParticipant("Bob", null, 88),
                        ),
                    userAttemptCount = 3,
                    userAveragePercentScore = 75,
                    userBestPercentScore = 92,
                    advice = null,
                    questionScores =
                        listOf(9, 9, 9, 8, 8, 7, 7, 7, 6, 6, 5, 5, 5, 4, 3, 3, 1, 1, 1, 1),
                    livesRemainingHearts = 3,
                    livesMaxHearts = 5,
                    showRatingPrompt = true,
                    saveWarning = false,
                ),
            onSubmitRating = {},
            comments = emptyList(),
            onRunAgain = {},
            onNextLesson = {},
            onPostComment = {},
            onSyncResult = {},
            proposalFeeQuote = null,
            errorReportState = ErrorReportSubmissionState.Idle,
            onReportDialogOpened = {},
            onReportError = { _, _ -> },
            copyEditSheetState = CopyEditSheetState(),
            onCopyEditDialogOpened = {},
            onCopyEditMarkChanged = { _, _ -> },
            onCopyEditSubmit = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun ResultContentSaveWarningPreview() {
    SchoolQuizTheme {
        ResultContent(
            state =
                RunnerUiState.Result(
                    percentScore = PercentScore(60),
                    mode = Difficulty.HARD,
                    completedAt = 0L,
                    hardUnlocked = false,
                    bestStarsRawTenths = 10,
                    currentAttemptStarsRawTenths = 26,
                    lessonAverageRating = null,
                    lessonRatingCount = 0,
                    top3 = emptyList(),
                    userAttemptCount = 1,
                    userAveragePercentScore = 60,
                    userBestPercentScore = 60,
                    questionScores = listOf(5, 6, 4, 7, 3, 2, 8, 1),
                    showRatingPrompt = false,
                    saveWarning = true,
                ),
            onSubmitRating = {},
            comments = emptyList(),
            onRunAgain = {},
            onNextLesson = {},
            onPostComment = {},
            onSyncResult = {},
            proposalFeeQuote = null,
            errorReportState = ErrorReportSubmissionState.Idle,
            onReportDialogOpened = {},
            onReportError = { _, _ -> },
            copyEditSheetState = CopyEditSheetState(),
            onCopyEditDialogOpened = {},
            onCopyEditMarkChanged = { _, _ -> },
            onCopyEditSubmit = {},
        )
    }
}
