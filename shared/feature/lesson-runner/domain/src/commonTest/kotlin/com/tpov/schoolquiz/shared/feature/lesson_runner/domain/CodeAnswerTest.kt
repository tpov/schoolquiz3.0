package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec scenarios 49a-49c: CodeAnswer.allShownAnswersAre9 extension.
 * Spec scenarios 67-68: CodeAnswer value object guards.
 *
 * Tests the `allShownAnswersAre9` extension property and CodeAnswer validation.
 */
class CodeAnswerTest {

    // ── allShownAnswersAre9 (tests 49a-49b-49c) ───────────────────────────────

    @Test
    fun `given CodeAnswer 9090 then allShownAnswersAre9 is true`() {
        // Spec scenario #49a: shown = '9', not shown = '0' → all shown are 9 AND at least one 9
        val ca = CodeAnswer("9090")
        assertTrue(ca.allShownAnswersAre9, "Spec scenario #49a: '9' and '0' only with at least one '9' → true")
    }

    @Test
    fun `given CodeAnswer 0000 then allShownAnswersAre9 is false`() {
        // Spec scenario #49b: all '0' → no '9' at all → guard `any { '9' }` fails
        val ca = CodeAnswer("0000")
        assertFalse(ca.allShownAnswersAre9, "Spec scenario #49b: all '0' → no '9' → false")
    }

    @Test
    fun `given CodeAnswer 9908 then allShownAnswersAre9 is false`() {
        // Spec scenario #49c: digit '8' breaks the condition (not '0' or '9')
        val ca = CodeAnswer("9908")
        assertFalse(ca.allShownAnswersAre9, "Spec scenario #49c: digit '8' → false")
    }

    @Test
    fun `given CodeAnswer 9999 then allShownAnswersAre9 is true`() {
        assertTrue(CodeAnswer("9999").allShownAnswersAre9, "all 9 → true")
    }

    @Test
    fun `given CodeAnswer 9919 then allShownAnswersAre9 is false`() {
        assertFalse(CodeAnswer("9919").allShownAnswersAre9, "'1' breaks condition → false")
    }

    // ── Value object guards (tests 67-68) ────────────────────────────────────

    @Test
    fun `given CodeAnswer empty string then throws`() {
        // Spec scenario #67
        assertFailsWith<IllegalArgumentException>("Spec scenario #67: empty CodeAnswer throws") {
            CodeAnswer("")
        }
    }

    @Test
    fun `given CodeAnswer with non-digit char then throws`() {
        // Spec scenario #68: '12X45' has 'X' which is not in '0'..'9'
        assertFailsWith<IllegalArgumentException>("Spec scenario #68: non-digit char throws") {
            CodeAnswer("12X45")
        }
    }

    @Test
    fun `given CodeAnswer with valid chars then constructs successfully`() {
        val ca = CodeAnswer("1234567890")
        assertEquals("1234567890", ca.raw)
    }
}
