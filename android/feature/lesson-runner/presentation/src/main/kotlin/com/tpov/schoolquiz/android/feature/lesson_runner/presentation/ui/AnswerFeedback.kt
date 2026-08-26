package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

enum class AnswerFeedbackTone {
    Neutral,
    Correct,
    Wrong,

    /** A right answer nobody chose: dimmed rather than lit (design decision F6). */
    Muted,
}

sealed interface AnswerFeedback {
    val answer: UserAnswerDraft

    /**
     * Whether the right answer may be shown. False during an exam and on hard questions, where
     * the answer is the assessment — see SessionMode.revealsCorrectAnswer.
     */
    val revealCorrect: Boolean

    data class SingleChoice(
        override val answer: UserAnswerDraft.SingleChoiceDraft,
        val selectedId: String,
        val correctId: String?,
        override val revealCorrect: Boolean = true,
    ) : AnswerFeedback

    data class MultipleChoice(
        override val answer: UserAnswerDraft.MultipleChoiceDraft,
        val selectedIds: Set<String>,
        val correctIds: Set<String>,
        override val revealCorrect: Boolean = true,
    ) : AnswerFeedback

    data class Ordering(
        override val answer: UserAnswerDraft.OrderingDraft,
        val orderIds: List<String>,
        val correctOrderIds: List<String>,
        override val revealCorrect: Boolean = true,
    ) : AnswerFeedback

    /** Survey answers are never graded, so there is nothing to compare against. */
    data class Survey(
        override val answer: UserAnswerDraft.SurveyDraft,
        val selectedIds: Set<String>,
        override val revealCorrect: Boolean = false,
    ) : AnswerFeedback

    data class FillBlank(
        override val answer: UserAnswerDraft.FillBlankDraft,
        val filledCandidateIdsByBlankIndex: Map<Int, String>,
        val correctCandidateIdsByBlankIndex: Map<Int, String>,
        override val revealCorrect: Boolean = true,
    ) : AnswerFeedback
}

/** Score digits as the runner code-answer uses them: 9 is perfect, 1 is a miss. */
internal const val PERFECT_DIGIT = 9
internal const val WORST_DIGIT = 1

// digit = round(correctShare × DIGIT_SPAN) + 1, integer math with ROUND_HALF_UP.
private const val DIGIT_SPAN = 8
private const val ROUND_HALF_UP = 2

/**
 * Score digit of a revealed answer — same formula as domain evaluateAnswer:
 * digit = round(correctShare × 8) + 1, integer round-half-up.
 * Null when nothing may be revealed (exam/hard) or for surveys, which are never graded.
 */
internal fun AnswerFeedback.revealDigit(): Int? {
    if (!revealCorrect) return null
    return when (this) {
        is AnswerFeedback.Survey -> null
        is AnswerFeedback.SingleChoice -> {
            val hit = if (correctId != null && selectedId == correctId) 1 else 0
            shareDigit(hit = hit, total = 1)
        }
        // Jaccard share: hits over the union of picked and correct.
        is AnswerFeedback.MultipleChoice ->
            shareDigit(
                hit = selectedIds.count { it in correctIds },
                total = (selectedIds + correctIds).size,
            )
        is AnswerFeedback.Ordering ->
            shareDigit(
                hit = orderIds.indices.count { i -> orderIds.getOrNull(i) == correctOrderIds.getOrNull(i) },
                total = correctOrderIds.size.coerceAtLeast(orderIds.size),
            )
        is AnswerFeedback.FillBlank -> {
            val hit =
                correctCandidateIdsByBlankIndex.count { (index, candidateId) ->
                    filledCandidateIdsByBlankIndex[index] == candidateId
                }
            shareDigit(
                hit = hit,
                total =
                    correctCandidateIdsByBlankIndex.size.coerceAtLeast(
                        filledCandidateIdsByBlankIndex.size,
                    ),
            )
        }
    }
}

private fun shareDigit(
    hit: Int,
    total: Int,
): Int {
    if (total <= 0) return WORST_DIGIT
    val digit = (hit * DIGIT_SPAN + total / ROUND_HALF_UP) / total + 1
    return digit.coerceIn(WORST_DIGIT, PERFECT_DIGIT)
}
