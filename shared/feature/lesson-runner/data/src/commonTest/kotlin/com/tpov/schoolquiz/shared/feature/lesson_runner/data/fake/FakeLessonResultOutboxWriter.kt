package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake

import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion

/**
 * Запоминает, что репозиторий передал писателю, и строки не ставит: тесты репозитория проверяют,
 * что доходит до писателя, а не что он из этого собирает.
 */
class FakeLessonResultOutboxWriter : LessonResultOutboxWriter {

    data class AttemptCall(
        val attempt: Attempt,
        val answers: List<QuestionAnswerEntity>,
        /** `null` — список не передан вовсе, что для сервера «неизвестно», а не «ничего». */
        val served: List<ServedQuestion>?,
    )

    val attemptCalls = mutableListOf<AttemptCall>()
    val lastAttemptCall: AttemptCall get() = attemptCalls.last()

    override suspend fun buildAttemptRow(
        attempt: Attempt,
        answers: List<QuestionAnswerEntity>,
        served: List<ServedQuestion>?,
    ): OutboxEntity? {
        attemptCalls += AttemptCall(attempt, answers, served)
        return null
    }
}
