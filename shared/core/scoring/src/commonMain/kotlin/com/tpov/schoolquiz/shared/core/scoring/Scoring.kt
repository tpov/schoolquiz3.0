package com.tpov.schoolquiz.shared.core.scoring

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent

// The scoring arithmetic, and the only Kotlin implementation of it.
//
// It lives in core rather than in the lesson runner because four callers now need it — offline
// lesson play, theme tests, the final exam, and arena's hard-question unlock — and a formula with
// four callers inside a feature module makes that feature a dependency of everything that scores.
//
// The server carries the only other implementation, in JavaScript. The two change together.

/**
 * Evaluates [answer] against [content] and returns a [Score] in 1..9.
 * Score formula: digit = round(correct_share × 8) + 1 (integer math, round-half-up).
 */
fun evaluateAnswer(content: QuestionContent, answer: UserAnswer): Score {
    return when {
        content is QuestionContent.SingleChoice && answer is UserAnswer.SingleChoiceAnswer -> {
            val validIds = content.options.map { it.id }.toSet()
            val selected = answer.selected?.takeIf { it in validIds }
            if (selected == content.correctOptionId) Score(9) else Score(1)
        }
        content is QuestionContent.MultipleChoice && answer is UserAnswer.MultipleChoiceAnswer -> {
            val validIds = content.options.map { it.id }.toSet()
            val picked = answer.selected.intersect(validIds)
            val correctPicked = picked.intersect(content.correctOptionIds).size
            val wrongPicked = (picked - content.correctOptionIds).size
            val missed = (content.correctOptionIds - picked).size
            scoreDigit(correctPicked, correctPicked + wrongPicked + missed)
        }
        content is QuestionContent.Ordering && answer is UserAnswer.OrderingAnswer -> {
            val correctOrder = content.items.map { it.id }
            val isValidPerm = answer.order.size == correctOrder.size &&
                answer.order.toSet() == correctOrder.toSet()
            if (!isValidPerm) {
                Score(1)
            } else {
                val matched = correctOrder.indices.count { i -> answer.order[i] == correctOrder[i] }
                scoreDigit(matched, correctOrder.size)
            }
        }
        content is QuestionContent.FillBlank && answer is UserAnswer.FillBlankAnswer -> {
            val validCandidates = content.candidates.map { it.id }.toSet()
            val filledCorrect = content.blanks.count { blank ->
                val c = answer.filled[blank.id]
                c != null && c in validCandidates && c == blank.correctCandidateId
            }
            scoreDigit(filledCorrect, content.blanks.size)
        }
        content is QuestionContent.Survey && answer is UserAnswer.SurveyAnswer -> {
            // A survey has no right answer, so it is scored on participation alone: responding at
            // all counts as full marks. Grading it any lower would drag down percentScore, stars
            // and the hard-mode unlock for a question that was never a test.
            val validIds = content.options.map { it.id }.toSet()
            if (answer.selected.any { it in validIds }) Score(9) else Score(1)
        }
        else -> Score(1)
    }
}

/**
 * Computes [Stars] for a single attempt.
 * EASY: rawTenths = (percentScore × 20 + 50) / 100 → [0..20].
 * HARD: rawTenths = 20 + (percentScore × 10 + 50) / 100 → [20..30].
 * Integer math, round-half-up via +50 constant.
 */
fun computeStars(percentScore: PercentScore, mode: Difficulty): Stars {
    val rawTenths = when (mode) {
        Difficulty.EASY -> (percentScore.raw * 20 + 50) / 100
        Difficulty.HARD -> 20 + (percentScore.raw * 10 + 50) / 100
    }
    return Stars(rawTenths)
}

/**
 * Derives [PercentScore] from [codeAnswer] using integer division.
 * Formula: sum((digit-1)*100/8) for non-zero digits / count of non-zero digits.
 */
fun computePercentScore(codeAnswer: CodeAnswer): PercentScore {
    val nonZero = codeAnswer.raw.filter { it != '0' }
    if (nonZero.isEmpty()) return PercentScore(0)
    val sum = nonZero.sumOf { (it.digitToInt() - 1) * 100 / 8 }
    return PercentScore(sum / nonZero.length)
}

/**
 * Score digit via integer round-half-up: (num * 8 + den/2) / den + 1.
 * Returns Score(1) if denominator is zero.
 */
private fun scoreDigit(numerator: Int, denominator: Int): Score {
    if (denominator == 0) return Score(1)
    val digit = (numerator * 8 + denominator / 2) / denominator + 1
    return Score(digit.coerceIn(1, 9))
}