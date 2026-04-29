package com.tpov.schoolquiz.shared.core.question_schema

/**
 * Parses a raw JSON [payload] string into a typed [QuestionContent].
 *
 * Domain-layer interface. Production implementation (kotlinx.serialization)
 * lives in the data source set of the question-schema module.
 */
interface QuestionContentParser {
    fun parse(payload: String): Result<QuestionContent>

    /**
     * Enriched overload: same as [parse] but supplies fallback values used when
     * the payload is in legacy format and is missing required fields (id, text, difficulty).
     *
     * Default implementation ignores fallback params and delegates to [parse].
     * Production implementation uses fallbacks for legacy-format payloads.
     */
    fun parse(
        payload: String,
        fallbackId: String,
        fallbackText: String,
        fallbackDifficulty: Difficulty,
    ): Result<QuestionContent> = parse(payload)
}
