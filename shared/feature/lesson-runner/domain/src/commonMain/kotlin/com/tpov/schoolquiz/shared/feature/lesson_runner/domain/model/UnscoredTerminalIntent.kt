package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId

/** What the player actually entered. No numeric score exists until the server grades it. */
data class RawAnsweredQuestion(
    val questionId: QuestionId,
    val codeAnswerIndex: Int,
    val answer: UserAnswer,
    val answeredAtMs: Long,
    val durationMs: Long,
    val wasTimeout: Boolean,
) {
    init {
        require(codeAnswerIndex >= 0)
        require(answeredAtMs >= 0L && durationMs >= 0L)
    }
}

/**
 * Immutable terminal request, not a successful Attempt. The durable intent and outbox store
 * its exact raw body atomically; no zero score or repetition/history placeholder is created.
 */
data class UnscoredTerminalIntent(
    val identity: AttemptIdentity,
    val completionKind: AttemptCompletionKind,
    val endedAtMs: Long,
    val answers: List<RawAnsweredQuestion>,
    val served: List<ServedQuestion>,
    val claims: ChargeClaimMask,
) {
    init {
        require(endedAtMs >= 0L)
        require(served.map { it.questionId }.distinct().size == served.size)
        require(served.map { it.codeAnswerIndex }.distinct().size == served.size)
        require(answers.map { it.questionId }.distinct().size == answers.size)
        require(answers.all { answer -> served.any {
            it.questionId == answer.questionId && it.codeAnswerIndex == answer.codeAnswerIndex
        } })
        require(served.all { it.codeAnswerIndex in claims.raw.indices })
    }
}
