package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser

class FakeQuestionContentParser : QuestionContentParser {
    private val contentByPayload = mutableMapOf<String, QuestionContent>()
    var defaultContent: QuestionContent? = null
    var parseCallCount = 0
        private set

    fun setContent(payload: String, content: QuestionContent) {
        contentByPayload[payload] = content
    }

    override fun parse(payload: String): Result<QuestionContent> {
        parseCallCount++
        val content = contentByPayload[payload] ?: defaultContent
        return if (content != null) {
            Result.success(content)
        } else {
            Result.failure(IllegalArgumentException("No content configured for payload: $payload"))
        }
    }
}
