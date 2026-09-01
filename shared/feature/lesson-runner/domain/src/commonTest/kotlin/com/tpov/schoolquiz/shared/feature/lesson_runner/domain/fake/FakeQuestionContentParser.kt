package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent

class FakeQuestionContentParser(
    private val responses: MutableMap<String, Result<QuestionContent>> = mutableMapOf(),
    private var defaultResult: Result<QuestionContent>? = null,
) : QuestionContentParser {

    private val redactedResponses: MutableMap<String, RedactedQuestionContent> = mutableMapOf()

    /**
     * How many times the parser was consulted, through **either** entry point.
     *
     * Counted in [parseForDisplay] as well as [parse], because a redacted payload short-circuits
     * before [parse] is reached. Counting only [parse] would under-report by one per redacted
     * question, and any test asserting "consulted once per question" would pass for the wrong
     * reason.
     */
    var parseCallCount: Int = 0
        private set

    fun addResponse(payload: String, content: QuestionContent) {
        responses[payload] = Result.success(content)
    }

    fun addFailure(payload: String, error: Throwable = RuntimeException("parse error: $payload")) {
        responses[payload] = Result.failure(error)
    }

    /**
     * Teaches the fake the one thing the interface's default [parseForDisplay] body cannot do: hand
     * back a redacted value.
     *
     * Registers a [parse] **failure** for the same payload at the same time, and that second half
     * is not decoration. Production keeps [QuestionContent] and [RedactedQuestionContent] in
     * disjoint hierarchies precisely so that a redacted payload can never surface as a
     * [QuestionContent] — the authoring components re-encode whatever they parse, and one arriving
     * there would overwrite the stored question with its own public half. Without this line a fake
     * that had also been given [setDefaultContent] would return a [QuestionContent] for a redacted
     * payload and quietly model the impossible.
     */
    fun addRedacted(payload: String, content: RedactedQuestionContent) {
        redactedResponses[payload] = content
        addFailure(payload, IllegalArgumentException("redacted payload has no answer key: $payload"))
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

    override fun parseForDisplay(
        payload: String,
        fallbackId: String,
        fallbackText: String,
        fallbackDifficulty: Difficulty,
    ): Result<QuestionDisplay> {
        val redacted = redactedResponses[payload]
        if (redacted == null) {
            return parse(payload, fallbackId, fallbackText, fallbackDifficulty)
        }
        parseCallCount++
        return Result.success(redacted)
    }
}
