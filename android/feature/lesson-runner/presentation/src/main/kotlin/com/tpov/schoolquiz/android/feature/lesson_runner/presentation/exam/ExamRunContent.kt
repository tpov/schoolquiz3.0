package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        Text(text = view.mode.name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = ExamText.terms(view.requiredTermsRevision),
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = view.termsAccepted,
                onCheckedChange = onTermsChanged,
            )
            Text(text = ExamText.terms(view.requiredTermsRevision))
        }
        view.refusal?.let { refusal ->
            // Отказ различает недоступность и купленный-но-непройденный доступ; текст — из
            // ресурсов по типу отказа, не по строке сервера.
            Text(
                text = ExamText.refusal(refusal.javaClass.simpleName),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (refusal is com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamRefusal.Cooldown) {
                Text(text = ExamText.nextAvailable(refusal.availableAtMs))
            }
        }
        Button(onClick = onStart, enabled = view.canRequestStart) {
            Text(text = ExamText.start())
        }
        if (view.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** Сидинг: вопрос, отсчёт отображения, ответ. Квитанция без правильностных пометок. */
@Composable
internal fun ExamSittingContent(
    view: ExamRunView.Running,
    onDraftChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${view.sitting.currentIndex + 1} / ${view.sitting.totalQuestions}",
            style = MaterialTheme.typography.labelLarge,
        )
        val question = view.question
        if (question == null || view.awaitingAck) {
            Text(text = ExamText.awaitingAck())
        } else {
            // Остаток = закреплённый сервером дедлайн минус серверное «сейчас»; локальные часы
            // отсчёт не придумывают.
            val remaining = question.ack?.answerDeadlineMs?.minus(question.serverNowMs) ?: 0L
            Text(text = ExamText.awaitingAck().let { "${question.answerSeconds}s" })
            Text(text = "$remaining")
            // Keystrokes — локальный черновик; транспорт видит ответ только с кнопки:
            // первый частичный ответ побеждал бы на сервере.
            androidx.compose.material3.OutlinedTextField(
                value = view.answerDraft,
                onValueChange = onDraftChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = ExamText.answerHint()) },
            )
        }
        Button(onClick = { onSubmit(view.answerDraft) }, enabled = !view.busy) {
            Text(text = ExamText.submit())
        }
        Button(onClick = onFinish, enabled = !view.busy) {
            Text(text = ExamText.finish())
        }
    }
}

/** Ожидание терминализации: worker закрывает; повтор чтения — единственное действие. */
@Composable
internal fun ExamAwaitingClosureContent(view: ExamRunView.AwaitingClosure) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = ExamText.awaitingClosure())
        if (view.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** Результат: серверные значения дословно — процент, звёзды, проход, совет. */
@Composable
internal fun ExamResultContent(result: com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamResultProjection) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = ExamText.resultPercent(result.percent), style = MaterialTheme.typography.headlineMedium)
        result.starsTenths?.let { tenths ->
            Text(text = ExamText.resultStars(tenths))
        }
        Text(text = ExamText.passed(result.passed))
        // Совет фиксированной длины и безопасен: никаких имён уроков и повопросных данных.
        for (code in result.advice) {
            Text(text = ExamText.advice(code))
        }
        Text(text = ExamText.nextAvailable(result.nextAvailableAtMs))
    }
}
