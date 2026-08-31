package com.tpov.schoolquiz.shared.core.scoring

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec scenarios 17-20: PercentScore formula.
 *
 * Formula (Business Rule 10):
 *   nonZero = codeAnswer.filter { it != '0' }
 *   if nonZero.isEmpty → 0
 *   else sum((digit - 1) * 100 / 8) / nonZero.size   (integer division)
 *
 * NOTE: Requires `computePercentScore(codeAnswer: CodeAnswer): PercentScore`
 * in RunnerLogic.kt — not in Stage A skeleton; expected addition in Stage B.
 */
class PercentScoreComputeTest {

    @Test
    fun `given codeAnswer 9999 then percentScore 100`() {
        // Spec scenario #17: all '9' → each contributes (9-1)*100/8 = 100; avg = 100
        val result = computePercentScore(CodeAnswer("9999"))
        assertEquals(100, result.raw, "Spec scenario #17")
    }

    @Test
    fun `given codeAnswer 5555 then percentScore 50`() {
        // Spec scenario #18: each '5' → (5-1)*100/8 = 400/8 = 50; avg = 50
        val result = computePercentScore(CodeAnswer("5555"))
        assertEquals(50, result.raw, "Spec scenario #18")
    }

    @Test
    fun `given codeAnswer 9050 then percentScore 75`() {
        // Spec scenario #19: nonZero = '9','5'; (100 + 50) / 2 = 75
        // Note: "9050" chars: '9','0','5','0' — '0' are not shown positions
        val result = computePercentScore(CodeAnswer("9050"))
        assertEquals(75, result.raw, "Spec scenario #19")
    }

    @Test
    fun `given codeAnswer 1111 then percentScore 0`() {
        // Spec scenario #20: each '1' → (1-1)*100/8 = 0; avg = 0
        val result = computePercentScore(CodeAnswer("1111"))
        assertEquals(0, result.raw, "Spec scenario #20")
    }

    @Test
    fun `given codeAnswer all 0 then percentScore 0`() {
        // Edge: all not-shown → nonZero empty → 0
        val result = computePercentScore(CodeAnswer("000"))
        assertEquals(0, result.raw, "all zeros → 0 percent")
    }
}
