package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/**
 * Derived integer percent [0..100].
 * Formula: nonZero = codeAnswer.filter { it != '0' };
 *   if empty → 0, else sum((digit-1)*100/8) / nonZero.size (integer division).
 */
@JvmInline
value class PercentScore(val raw: Int) {
    init {
        require(raw in 0..100) { "PercentScore.raw must be in 0..100, got $raw" }
    }
}
