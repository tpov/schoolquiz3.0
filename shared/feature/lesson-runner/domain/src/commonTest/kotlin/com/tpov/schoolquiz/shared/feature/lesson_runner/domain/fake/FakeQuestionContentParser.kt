package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake

import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser

class FakeQuestionContentParser(
    private val responses: MutableMap<String, Result<QuestionContent>> = mutableMapOf(),
    private var defaultResult: Result<QuestionContent>? = null,
) : QuestionContentParser {

    var parseCallCount: Int = 0
        private set

    fun addResponse(payload: String, content: QuestionContent) {
        responses[payload] = Result.success(content)
    }

    fun addFailure(payload: String, error: Throwable = RuntimeException("parse error: $payload")) {
        responses[payload] = Result.failure(error)
    }

    fun setDefaultContent(content: QuestionContent) {
        defaultResult = Result.success(content)
    }

    fun setDefaultFailure() {
        defaultResult = Result.failure(RuntimeException("parse error"))
    }

    override fun parse(payload: String): Result<QuestionContent> {
        parseCallCount++
        return responses[payload]
            ?: defaultResult
            ?: Result.failure(IllegalArgumentException("FakeParser: no response configured for payload='$payload'"))
    }
}
