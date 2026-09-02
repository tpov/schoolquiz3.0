package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.core.scoring.Score
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.core.scoring.Stars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerDuration
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.core.scoring.allShownAnswersAre9
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import kotlin.random.Random
import com.tpov.schoolquiz.shared.core.scoring.evaluateAnswer
import com.tpov.schoolquiz.shared.core.scoring.computeStars

// ── Public pure functions ──────────────────────────────────────────────────

/**
 * Records [answer] score for current question and advances [indexInPool].
 * When the last question is answered, returns Ready with indexInPool == playOrder.size (sentinel).
 * Component checks this sentinel and calls CompleteAttemptUseCase.
 */
fun submitAnswer(
    state: RunnerState.Ready,
    answer: UserAnswer,
    nowMs: Long,
    wasTimeout: Boolean = false,
): RunnerState.Ready {
    val currentQuestion = state.playOrder[state.indexInPool]
    val score = evaluateAnswer(currentQuestion.content, answer)

    val codeAnswerChars = state.codeAnswer.raw.toCharArray()
    codeAnswerChars[currentQuestion.codeAnswerIndex] = ('0' + score.raw)
    val newCodeAnswer = CodeAnswer(String(codeAnswerChars))

    val nextIndex = state.indexInPool + 1
    val newDeadlineMs = if (nextIndex < state.playOrder.size) {
        val nextQuestion = state.playOrder[nextIndex]
        val duration = computeTimer(
            content = nextQuestion.content,
            mode = state.mode,
            coefficients = TimerCoefficients.Default,
            sessionMode = state.sessionMode,
        )
        nowMs + duration.seconds * 1000L
    } else {
        state.deadlineMs
    }

    // The digit alone cannot answer "which option did they pick" or "how long did it take", so the
    // whole answer is recorded here and persisted with the attempt. A zero/absent start timestamp
    // means the duration is unknown, which is truer than reporting a bogus one.
    val answered = AnsweredQuestion(
        questionId = currentQuestion.sourceId,
        codeAnswerIndex = currentQuestion.codeAnswerIndex,
        score = score,
        answer = answer,
        answeredAtMs = nowMs,
        durationMs = if (state.questionStartedAtMs > 0L) {
            (nowMs - state.questionStartedAtMs).coerceAtLeast(0L)
        } else {
            0L
        },
        wasTimeout = wasTimeout,
    )

    return state.copy(
        indexInPool = nextIndex,
        codeAnswer = newCodeAnswer,
        deadlineMs = newDeadlineMs,
        currentDraftAnswer = null,
        questionStartedAtMs = nowMs,
        answers = state.answers + answered,
    )
}

/**
 * Generates a deterministic random [UserAnswer] from [randomSeed] and delegates to [submitAnswer].
 * Called on timer expiry or onStop.
 */
fun autoAnswerOnTimeout(state: RunnerState.Ready, randomSeed: Long, nowMs: Long): RunnerState.Ready {
    val currentQuestion = state.playOrder[state.indexInPool]
    val answer = generateTimeoutAnswer(currentQuestion.content, state.currentDraftAnswer, randomSeed)
    return submitAnswer(state, answer, nowMs, wasTimeout = true)
}

/**
 * Returns max [Stars] across all [attempts] for a lesson.
 * Returns Stars(0) if list is empty.
 */
fun computeBestStars(attempts: List<Attempt>): Stars {
    if (attempts.isEmpty()) return Stars(0)
    return Stars(attempts.maxOf { computeStars(it.percentScore, it.mode).rawTenths })
}

/**
 * Returns true if [attempts] contain at least one EASY attempt with allShownAnswersAre9.
 * String-based, not percent-based. HARD attempts never unlock HARD.
 */
fun computeHardUnlocked(attempts: List<Attempt>): Boolean =
    attempts.any { it.mode == Difficulty.EASY && it.codeAnswer.allShownAnswersAre9 }

/**
 * Computes per-question timer in seconds.
 * Formula: max(5, round(charsCount × k)); +100 chars if image present.
 */
fun computeTimer(
    content: QuestionContent,
    mode: Difficulty,
    coefficients: TimerCoefficients,
    sessionMode: SessionMode = SessionMode.LEARNING,
): TimerDuration {
    val charsCount = computeCharsCount(content)
    val base = when (mode) {
        Difficulty.EASY -> coefficients.kEasy
        Difficulty.HARD -> coefficients.kHard
    }
    val k = if (sessionMode == SessionMode.EXAM) base * coefficients.examFactor else base
    val seconds = maxOf(5, (charsCount * k + 0.5).toInt())
    return TimerDuration(seconds)
}

/**
 * Selects a random subset of [eligible] using [seed].
 * Size = min([poolSize], eligible.size). Returned list order is shuffled (caller sorts for playOrder).
 */
fun selectSubset(
    eligible: List<RunnerQuestion.Valid>,
    poolSize: Int,
    seed: Long,
): List<RunnerQuestion.Valid> {
    if (eligible.isEmpty()) return emptyList()
    val count = minOf(poolSize, eligible.size)
    return eligible.shuffled(Random(seed)).take(count)
}

/**
 * Builds final [CodeAnswer] for an aborted attempt.
 * Unanswered subset positions (indexInPool and beyond) → '1'.
 * Out-of-subset positions remain '0'. Already answered positions stay as-is.
 */
/**
 * Заявки, сделанные в этом прогоне, — пустая маска, если ни одной.
 *
 * Пустую отдаём, а не `null`: маска той же длины, что `codeAnswer`, и сервер сверяет длины.
 */
fun RunnerState.Ready.chargeClaimsOrEmpty(): ChargeClaimMask =
    chargeClaims ?: ChargeClaimMask.none(codeAnswer.raw.length)

/**
 * Отмечает заявку на заряд за текущий вопрос.
 *
 * Вид определяет сложность попытки, а не вызывающий: обычный заряд покупает подсказку на лёгком
 * вопросе, плазменный — пропуск сложного, и перепутать их значит заявить пропуск там, где ответ был
 * показан (CAP-1). Списания здесь не происходит: это заявка, и платит по ней сервер при отправке.
 */
fun claimCharge(
    state: RunnerState.Ready,
    claim: ChargeClaimMask.Claim,
): RunnerState.Ready {
    val expected =
        when (state.mode) {
            Difficulty.EASY -> ChargeClaimMask.Claim.STANDARD_HINT
            Difficulty.HARD -> ChargeClaimMask.Claim.PLASMA_SKIP
        }
    require(claim == expected) { "На попытке ${state.mode} заявка бывает только $expected, получено $claim" }
    val index = state.playOrder[state.indexInPool].codeAnswerIndex
    return state.copy(chargeClaims = state.chargeClaimsOrEmpty().with(index, claim))
}

fun buildCodeAnswerOnAbort(state: RunnerState.Ready): CodeAnswer {
    val chars = state.codeAnswer.raw.toCharArray()
    for (i in state.indexInPool until state.playOrder.size) {
        chars[state.playOrder[i].codeAnswerIndex] = '1'
    }
    return CodeAnswer(String(chars))
}



/** Total character count for timer formula: text + option/item/candidate texts + optional +100. */
private fun computeCharsCount(content: QuestionContent): Int {
    val imageBonus = if (content.imageUrl != null) 100 else 0
    val optionChars = when (content) {
        is QuestionContent.SingleChoice -> content.options.sumOf { it.text.length }
        is QuestionContent.MultipleChoice -> content.options.sumOf { it.text.length }
        is QuestionContent.Ordering -> content.items.sumOf { it.text.length }
        is QuestionContent.FillBlank -> content.candidates.sumOf { it.text.length }
        is QuestionContent.Survey -> content.options.sumOf { it.text.length }
    }
    return content.text.length + optionChars + imageBonus
}

/** Generates a deterministic random answer for [content] using [seed]. */
private fun generateRandomAnswer(content: QuestionContent, seed: Long): UserAnswer {
    val random = Random(seed)
    return when (content) {
        is QuestionContent.SingleChoice -> {
            UserAnswer.SingleChoiceAnswer(content.options.random(random).id)
        }
        is QuestionContent.MultipleChoice -> {
            val count = content.correctOptionIds.size
            val selected = content.options.shuffled(random).take(count).map { it.id }.toSet()
            UserAnswer.MultipleChoiceAnswer(selected)
        }
        is QuestionContent.Ordering -> {
            UserAnswer.OrderingAnswer(content.items.shuffled(random).map { it.id })
        }
        is QuestionContent.Survey -> {
            // A survey has no right answer, so a random pick is as valid as any other.
            UserAnswer.SurveyAnswer(setOf(content.options.random(random).id))
        }
        is QuestionContent.FillBlank -> {
            val filled = content.blanks.associate { blank ->
                blank.id to content.candidates.random(random).id
            }
            UserAnswer.FillBlankAnswer(filled)
        }
    }
}

internal fun generateTimeoutAnswer(
    content: QuestionContent,
    draft: UserAnswerDraft?,
    seed: Long,
): UserAnswer {
    val randomAnswer = generateRandomAnswer(content, seed)
    return when {
        // A survey records what the respondent actually picked, or nothing at all. Inventing a
        // random opinion on timeout would poison the very distribution the survey exists to collect.
        content is QuestionContent.Survey ->
            UserAnswer.SurveyAnswer(
                (draft as? UserAnswerDraft.SurveyDraft)?.selected.orEmpty()
                    .intersect(content.options.map { it.id }.toSet()),
            )

        content is QuestionContent.SingleChoice &&
            draft is UserAnswerDraft.SingleChoiceDraft &&
            draft.selected != null ->
            UserAnswer.SingleChoiceAnswer(draft.selected)

        content is QuestionContent.MultipleChoice &&
            draft is UserAnswerDraft.MultipleChoiceDraft &&
            randomAnswer is UserAnswer.MultipleChoiceAnswer -> {
            val validIds = content.options.map { it.id }.toSet()
            val selected = draft.selected.intersect(validIds)
            val targetSize = content.correctOptionIds.size
            val randomRemainder =
                randomAnswer.selected
                    .filter { it !in selected }
                    .take((targetSize - selected.size).coerceAtLeast(0))
            UserAnswer.MultipleChoiceAnswer(selected + randomRemainder)
        }

        content is QuestionContent.Ordering &&
            draft is UserAnswerDraft.OrderingDraft -> {
            val correctIds = content.items.map { it.id }.toSet()
            val draftOrder = draft.order.filter { it in correctIds }
            val missing = content.items.map { it.id }.filter { it !in draftOrder }
            UserAnswer.OrderingAnswer(draftOrder + missing)
        }

        content is QuestionContent.FillBlank &&
            draft is UserAnswerDraft.FillBlankDraft &&
            randomAnswer is UserAnswer.FillBlankAnswer -> {
            val validBlankIds = content.blanks.map { it.id }.toSet()
            val validCandidateIds = content.candidates.map { it.id }.toSet()
            val filled =
                randomAnswer.filled.toMutableMap().also { merged ->
                    draft.filled.forEach { (blankId, candidateId) ->
                        if (blankId in validBlankIds && candidateId in validCandidateIds) {
                            merged[blankId] = candidateId
                        }
                    }
                }
            UserAnswer.FillBlankAnswer(filled)
        }

        else -> randomAnswer
    }
}
