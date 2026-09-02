package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId

/**
 * One question dealt into the attempt's play order, and where it sits in the attempt's `codeAnswer`.
 *
 * Dealt, not necessarily rendered: after quitting on question 4 of 20, questions 5–20 are still
 * sent, because `buildCodeAnswerOnAbort` already wrote `'1'` for them and counts them as shown.
 * Sending only the reached ones would shrink the denominator and silently inflate the percent.
 *
 * The digit string alone cannot say which positions were put to the player: a `'0'` means "not
 * shown", but inferring "not shown" from "not submitted" is exactly the gap a client scores 100%
 * through by staying silent about its wrong answers. So the attempt names its served questions
 * outright, and the server reads them: on a hard attempt it scores itself, every digit is placed
 * from this list; on an attempt this device scored, the list is checked against the digits and one
 * that disagrees is stored, marked and paid nothing.
 *
 * Mirrors the two fields it shares with [AnsweredQuestion], so the two rows read the same on the wire.
 */
data class ServedQuestion(
    val questionId: QuestionId,
    val codeAnswerIndex: Int,
) {
    init {
        require(codeAnswerIndex >= 0) { "codeAnswerIndex must be non-negative" }
    }
}

/**
 * The served list of a play order: every question in it, in position order, each position and
 * each id exactly once.
 *
 * The play order is presentation order — dealt at random — while the server walks positions.
 * Sorting here is what makes the queued list independent of the order the subset happened to be
 * dealt in. Only [RunnerQuestion.Valid] can be dealt, which is what keeps the `-1` placeholder
 * index of an unparsed question out of this list.
 */
fun List<RunnerQuestion.Valid>.toServedQuestions(): List<ServedQuestion> {
    val served = map { ServedQuestion(questionId = it.sourceId, codeAnswerIndex = it.codeAnswerIndex) }
        .sortedBy { it.codeAnswerIndex }
    require(served.distinctBy { it.codeAnswerIndex }.size == served.size) {
        "served positions must be unique: ${served.map { it.codeAnswerIndex }}"
    }
    require(served.distinctBy { it.questionId }.size == served.size) {
        "served question ids must be unique: ${served.map { it.questionId.value }}"
    }
    return served
}
