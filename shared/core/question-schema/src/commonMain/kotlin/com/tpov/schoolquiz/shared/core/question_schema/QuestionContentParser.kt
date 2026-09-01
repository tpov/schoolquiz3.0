package com.tpov.schoolquiz.shared.core.question_schema

/**
 * Parses a raw JSON [payload] string into a typed question.
 *
 * Two contracts, not one. The [parse] overloads answer "give me a question **with its answer
 * key**", and so read [QuestionContent] alone — a redacted payload is refused there, which is what
 * keeps the authoring and review screens from re-encoding one over a stored question.
 * [parseForDisplay] answers "give me whatever is there, because I only mean to show it", and reads
 * either hierarchy as a [QuestionDisplay].
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

    /**
     * Parses a payload of *either* hierarchy into the half of it that can be shown.
     *
     * The two [parse] overloads answer "give me a question with its answer key" and must keep
     * refusing a redacted payload: `DefaultQuestCreateComponent` and `DefaultReviewQueueComponent`
     * re-encode whatever they parse, so a redacted value arriving there as a [QuestionContent]
     * would overwrite the stored question with its own public half. This one answers the different
     * question "parse this, whichever kind it is, because I only mean to display it" — so it
     * returns [QuestionDisplay], which carries nothing about an answer and cannot be saved back.
     *
     * Same parameters as the enriched [parse] on purpose: the fallbacks exist for the one legacy
     * shape still in the corpus, and a caller that moves from that overload to this one should be
     * changing a type, not rethinking what it passes. They are never applied to a redacted payload,
     * which is not legacy and carries its own values verbatim.
     *
     * Has a default body so that implementations which only override [parse] — both
     * `FakeQuestionContentParser`s do — keep compiling and keep behaving: they yield the
     * [QuestionContent] they were configured with, and refuse a redacted payload exactly as before.
     * `Result` is covariant, so no wrapping is needed. Only the production parser reads the
     * redacted hierarchy.
     */
    fun parseForDisplay(
        payload: String,
        fallbackId: String,
        fallbackText: String,
        fallbackDifficulty: Difficulty,
    ): Result<QuestionDisplay> = parse(payload, fallbackId, fallbackText, fallbackDifficulty)
}
