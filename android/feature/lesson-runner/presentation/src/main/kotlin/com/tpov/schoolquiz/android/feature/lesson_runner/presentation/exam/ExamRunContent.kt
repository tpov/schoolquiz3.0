package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirInk
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSwitch
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType

/*
 * NOIR (эпик 9.6): token-переодевка структурных экранов экзамена. Строки ExamText.* — плейсхолдеры
 * до подтверждения владельцем — НЕ тронуты байт-в-байт; структура состояний и проводка не менялись.
 * Согласие с условиями — on/off, поэтому это свитч (NOIR-SPEC §6.4), не чекбокс.
 */

/**
 * Экраны экзамена — отображение серверных фактов, без вычислений и без подсветки правильности.
 *
 * Содержимое каждого экрана требует отдельного подтверждения владельца (протокол §Screen-content
 * confirmation): здесь только структура — состояния, данные и действия, — а не окончательные
 * формулировки. Место для локализованных строк помечено [ExamText]; тексты приходят из ресурсов
 * после подтверждения владельцем, поэтому сейчас вынесены в одну таблицу-заглушку.
 */
internal object ExamText {
    fun refusal(kind: String): String = "EXAM_REFUSAL_$kind"
    fun runRefusal(reason: String): String = "EXAM_RUN_REFUSAL_$reason"
    fun terms(requiredRevision: Long): String = "EXAM_TERMS_REQUIRED_$requiredRevision"
    fun start(): String = "EXAM_START"
    fun awaitingAck(): String = "EXAM_LOADING_QUESTION"
    fun answerHint(): String = "EXAM_ANSWER_PLACEHOLDER"
    fun submit(): String = "EXAM_SUBMIT_ANSWER"
    fun finish(): String = "EXAM_FINISH"
    fun awaitingClosure(): String = "EXAM_CLOSING"
    fun resultPercent(percent: Int): String = "EXAM_RESULT_PERCENT_$percent"
    fun resultStars(tenths: Int): String = "EXAM_RESULT_STARS_$tenths"
    fun passed(passed: Boolean): String = if (passed) "EXAM_PASSED" else "EXAM_NOT_PASSED"
    fun advice(code: String): String = "EXAM_ADVICE_$code"
    fun nextAvailable(atMs: Long?): String = "EXAM_NEXT_AVAILABLE_${atMs ?: "PENDING"}"
}

/** Витрина допуска: типизированный отказ, согласие на условия, старт. */
@Composable
internal fun ExamEntryContent(
    view: ExamRunView.Entry,
    onStart: () -> Unit,
    onTermsChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = view.mode.name, style = NoirType.groupTitle)
        Text(
            text = ExamText.terms(view.requiredTermsRevision),
            style = NoirType.rowSub,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoirSwitch(
                checked = view.termsAccepted,
                onCheckedChange = onTermsChanged,
                // У переключателя нет встроенного ярлыка: смысловая метка — та же строка условий.
                contentDescription = ExamText.terms(view.requiredTermsRevision),
            )
            Text(text = ExamText.terms(view.requiredTermsRevision), style = NoirType.rowSub)
        }
        view.refusal?.let { refusal ->
            // Отказ различает недоступность и купленный-но-непройденный доступ; текст — из
            // ресурсов по типу отказа, не по строке сервера.
            Text(
                text = ExamText.refusal(refusal.javaClass.simpleName),
                style = NoirType.rowSub,
                color = NoirT2,
            )
            if (refusal is com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamRefusal.Cooldown) {
                Text(text = ExamText.nextAvailable(refusal.availableAtMs), style = NoirType.num)
            }
        }
        NoirButton(text = ExamText.start(), onClick = onStart, enabled = view.canRequestStart)
        if (view.busy) CircularProgressIndicator(color = LocalNoirAccent.current)
    }
}

/** Сидинг: вопрос, отсчёт отображения, ответ. Квитанция без правильностных пометок. */
@Composable
internal fun ExamSittingContent(
    view: ExamRunView.Running,
    onDraftChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFinish: () -> Unit,
    onQuestionRendered: () -> Unit = {},
) {
    // Первый ограниченный ACK — подтверждение того, что текст вопроса отрисован: без этого
    // триггера проводка экрана повисла бы в ожидании до ACK_REQUIRED-отказа. Отказ входит
    // в ключи: после отказавшего ACK эффект перезапускается — иначе повторить некому.
    androidx.compose.runtime.LaunchedEffect(view.awaitingAck, view.question?.index, view.refusal) {
        if (view.awaitingAck) onQuestionRendered()
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${view.sitting.currentIndex + 1} / ${view.sitting.totalQuestions}",
            style = NoirType.num,
        )
        // Причина последнего отказа видна, пока не придёт первый успешный шаг: тихая петля
        // повторов хуже честного сообщения.
        view.refusal?.let { Text(text = ExamText.runRefusal(it), style = NoirType.rowSub) }
        val question = view.question
        if (question == null || view.awaitingAck) {
            Text(text = ExamText.awaitingAck(), style = NoirType.rowSub)
        } else {
            // Остаток = закреплённый сервером дедлайн минус серверное «сейчас»; локальные часы
            // отсчёт не придумывают.
            val remaining = question.ack?.answerDeadlineMs?.minus(question.serverNowMs) ?: 0L
            Text(text = ExamText.awaitingAck().let { "${question.answerSeconds}s" }, style = NoirType.num)
            Text(text = "$remaining", style = NoirType.num)
            // Keystrokes — локальный черновик; транспорт видит ответ только с кнопки:
            // первый частичный ответ побеждал бы на сервере.
            OutlinedTextField(
                value = view.answerDraft,
                onValueChange = onDraftChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = ExamText.answerHint(), style = NoirType.rowSub) },
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
            )
        }
        NoirButton(text = ExamText.submit(), onClick = { onSubmit(view.answerDraft) }, enabled = !view.busy)
        NoirButton(text = ExamText.finish(), onClick = onFinish, enabled = !view.busy)
    }
}

/** Ожидание терминализации: worker закрывает; повтор чтения — единственное действие. */
@Composable
internal fun ExamAwaitingClosureContent(view: ExamRunView.AwaitingClosure) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = ExamText.awaitingClosure(), style = NoirType.rowSub)
        // Неудача запроса закрытия видима: не вечный спиннер, а честный отказ.
        view.refusal?.let { Text(text = ExamText.runRefusal(it), style = NoirType.rowSub) }
        if (view.busy) CircularProgressIndicator(color = LocalNoirAccent.current)
    }
}

/** Результат: серверные значения дословно — процент, звёзды, проход, совет. */
@Composable
internal fun ExamResultContent(result: com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamResultProjection) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = ExamText.resultPercent(result.percent), style = NoirType.price)
        result.starsTenths?.let { tenths ->
            Text(text = ExamText.resultStars(tenths), style = NoirType.num)
        }
        Text(text = ExamText.passed(result.passed), style = NoirType.rowTitle)
        // Совет фиксированной длины и безопасен: никаких имён уроков и повопросных данных.
        for (code in result.advice) {
            Text(text = ExamText.advice(code), style = NoirType.rowSub)
        }
        Text(text = ExamText.nextAvailable(result.nextAvailableAtMs), style = NoirType.rowSub)
    }
}
