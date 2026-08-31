package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.core.scoring.Score
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer

/**
 * One answer, recorded per question.
 *
 * The attempt itself only keeps [CodeAnswer] — a digit per question — which is enough to score the
 * lesson but throws away everything else. Keeping the answer itself unlocks four things at once:
 * spaced repetition, per-lesson statistics, the answer distribution a survey needs, and the timing
 * signals that make automated play detectable.
 *
 * @param codeAnswerIndex position of this question inside the attempt's [CodeAnswer].
 * @param score the 1..9 digit written into that position.
 * @param durationMs time between the question appearing and being answered.
 * @param wasTimeout the timer answered on the player's behalf.
 */
data class AnsweredQuestion(
    val questionId: QuestionId,
    val codeAnswerIndex: Int,
    val score: Score,
    val answer: UserAnswer,
    val answeredAtMs: Long,
    val durationMs: Long,
    val wasTimeout: Boolean,
) {
    init {
        require(codeAnswerIndex >= 0) { "codeAnswerIndex must be non-negative" }
        require(answeredAtMs >= 0) { "answeredAtMs must be non-negative" }
        require(durationMs >= 0) { "durationMs must be non-negative" }
    }
}
