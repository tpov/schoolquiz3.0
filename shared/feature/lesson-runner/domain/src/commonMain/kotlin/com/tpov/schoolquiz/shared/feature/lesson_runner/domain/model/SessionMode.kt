package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import kotlinx.serialization.Serializable

/**
 * How a lesson is being played, as opposed to how hard its questions are.
 *
 * This is the second axis from ADR-0005 and it is independent of [Difficulty]: the same lesson can
 * be worked through for practice or sat as an exam. Only the session mode decides whether the right
 * answer is shown afterwards — difficulty decides which questions are asked and how the score maps
 * to stars.
 */
@Serializable
enum class SessionMode {
    /** Practice: the right answer is revealed on easy questions so the player learns from it. */
    LEARNING,

    /** Exam: nothing is revealed, and the result is what a course certificate is issued against. */
    EXAM,

    ;

    /**
     * Whether the correct answer may be shown after answering.
     *
     * The matrix comes from ADR-0005: revealed only while learning, and only on easy questions —
     * hard ones are the assessment even during practice.
     */
    fun revealsCorrectAnswer(difficulty: Difficulty): Boolean =
        this == LEARNING && difficulty == Difficulty.EASY
}
