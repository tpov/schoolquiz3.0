package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.exam

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamQuestionProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamResultProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSittingProjection

/**
 * Состояния экзаменационного прогона — то, что рисуют экраны.
 *
 * Значения результата и вопросов приходят с сервера готовыми: компонент НИЧЕГО не пересчитывает.
 * Отсчёт до дедлайна — чистое отображение серверных миллисекунд; правильность в квитанциях не
 * подсвечивается никогда (протокол: «Receipt UI has no correctness-dependent colour, mark or
 * duration»).
 */
sealed interface ExamRunView {

    /** Витрина допуска: доступность, типизированный отказ, согласие на условия. */
    data class Entry(
        val mode: ExamMode,
        val assessmentId: String,
        val entryPrice: Int,
        val termsShown: Boolean,
        val termsAccepted: Boolean,
        val requiredTermsRevision: Long,
        val refusal: ExamRefusal?,
        val busy: Boolean,
    ) : ExamRunView {

        /** Условия показаны и покрывают текущую ревизию: кнопку старта можно звать. */
        val canRequestStart: Boolean get() = !busy && termsShown && termsAccepted
    }

    /** Сидинг идёт: текущий вопрос, его серверные дедлайны и последний отказ транспорта. */
    data class Running(
        val sitting: ExamSittingProjection,
        val question: ExamQuestionProjection?,
        val awaitingAck: Boolean,
        val answerDraft: String,
        val busy: Boolean,
        /** Причина последнего отказа ACK/ответа; чистится первым успешным действием. */
        val refusal: String? = null,
    ) : ExamRunView {

        /** Остаток до закреплённого дедлайна — отображение серверного времени, не свой таймер. */
        fun remainingMs(serverNowMs: Long): Long? = question?.ack?.answerDeadlineMs?.let { it - serverNowMs }
    }

    /** Ожидание терминализации: закрытие запрошено, worker пишет результат. */
    data class AwaitingClosure(
        val sittingId: String,
        val busy: Boolean,
        val refusal: String? = null,
    ) : ExamRunView

    /** Терминальный результат: серверные значения, отрисованные дословно. */
    data class Finished(
        val result: ExamResultProjection,
    ) : ExamRunView
}
