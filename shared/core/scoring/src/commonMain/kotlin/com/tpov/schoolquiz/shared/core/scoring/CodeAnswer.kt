package com.tpov.schoolquiz.shared.core.scoring

/**
 * Encoded per-question score string.
 * Length = eligibleQuestions(mode).size.
 * Each char ∈ '0'..'9': '0' = not shown; '1'..'9' = shown with correctness digit.
 */
@JvmInline
value class CodeAnswer(val raw: String) {
    init {
        require(raw.isNotEmpty()) { "CodeAnswer must not be empty" }
        require(raw.all { it in '0'..'9' }) { "CodeAnswer chars must be in '0'..'9'" }
    }
}

/**
 * True if all shown positions (non-'0') are '9' AND at least one '9' exists.
 * String-based — no Float precision issues.
 */
val CodeAnswer.allShownAnswersAre9: Boolean
    get() = raw.all { it == '0' || it == '9' } && raw.any { it == '9' }
