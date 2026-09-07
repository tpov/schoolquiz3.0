package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.core.scoring.evaluateAnswer
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingFeedback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RawAnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId

private const val MILLIS_PER_SECOND = 1_000L

/** Pure initial state; the journal must atomically persist this together with its LAUNCHED claim. */
fun initialArenaRunnerCheckpoint(
    binding: ArenaRunnerCheckpointBinding,
    request: ArenaInitialCheckpoint,
): ArenaRunnerCheckpoint {
    validateBinding(binding)
    val snapshot = binding.snapshot
    val h = snapshot.header
    val duration =
        computeTimer(
            snapshot.questions.first().display,
            h.difficulty,
            TimerCoefficients.Default,
            request.sessionMode,
        ).seconds * MILLIS_PER_SECOND
    require(request.questionStartedAtMs >= 0L && request.questionStartedAtMs <= Long.MAX_VALUE - duration)
    require(request.deadlineMs == request.questionStartedAtMs + duration)
    return ArenaRunnerCheckpoint(
        ArenaRunnerCheckpoint.VERSION, h.ownerUid, h.ticketId, h.attemptId,
        binding.snapshotSha256, request.sessionMode, request.seed, ArenaRunnerPhase.ANSWERING, 0,
        request.questionStartedAtMs, request.deadlineMs, null, null, emptyList(),
        ChargeClaimMask.none(snapshot.questions.size), null,
    ).also { validateArenaRunnerCheckpoint(it, binding) }
}

/** Every accepted answer names the original frozen position, not a reloaded or reshuffled pool. */
fun validateArenaRunnerCheckpoint(
    checkpoint: ArenaRunnerCheckpoint,
    binding: ArenaRunnerCheckpointBinding,
) {
    validateBinding(binding)
    val h = binding.snapshot.header
    val questions = binding.snapshot.questions
    val size = questions.size
    require(checkpoint.schemaVersion == ArenaRunnerCheckpoint.VERSION)
    require(
        checkpoint.ownerUid == h.ownerUid && checkpoint.ticketId == h.ticketId &&
            checkpoint.attemptId == h.attemptId && checkpoint.snapshotSha256 == binding.snapshotSha256,
    )
    require(checkpoint.indexInPool in 0..size)
    require(checkpoint.questionStartedAtMs >= 0L && checkpoint.deadlineMs >= 0L)
    require(checkpoint.acceptedAnswers.size <= checkpoint.indexInPool)
    var previous = -1
    checkpoint.acceptedAnswers.forEach { answer ->
        require(answer.codeAnswerIndex > previous && answer.codeAnswerIndex < checkpoint.indexInPool)
        val question = questions[answer.codeAnswerIndex]
        require(answer.questionId.value == question.questionId)
        require(answer.answeredAtMs >= 0L && answer.durationMs >= 0L)
        validateAnswer(question.display, answer.answer)
        previous = answer.codeAnswerIndex
    }
    // Existing EASY runner records every closed question, including timeout/survey empty answers.
    if (h.difficulty == Difficulty.EASY) require(checkpoint.acceptedAnswers.size == checkpoint.indexInPool)
    require(checkpoint.chargeClaims.raw.length == size)
    val allowed = if (h.difficulty == Difficulty.HARD) ChargeClaimMask.PLASMA else ChargeClaimMask.STANDARD
    checkpoint.chargeClaims.raw.forEachIndexed { index, claim ->
        require(claim == ChargeClaimMask.NONE || claim == allowed)
        if (claim != ChargeClaimMask.NONE) require(index <= checkpoint.indexInPool)
        if (claim == ChargeClaimMask.PLASMA) {
            require(checkpoint.acceptedAnswers.none { it.codeAnswerIndex == index })
        }
    }
    validateCheckpointPhase(checkpoint, binding)
}

private fun validateCheckpointPhase(
    checkpoint: ArenaRunnerCheckpoint,
    binding: ArenaRunnerCheckpointBinding,
) {
    val questions = binding.snapshot.questions
    val size = questions.size
    if (checkpoint.phase == ArenaRunnerPhase.COMPLETING) {
        require(checkpoint.indexInPool == size && checkpoint.endedAtMs != null && checkpoint.endedAtMs >= 0L)
        require(checkpoint.currentDraft == null && checkpoint.pendingFeedback == null)
    } else {
        require(checkpoint.indexInPool < size && checkpoint.endedAtMs == null)
        checkpoint.currentDraft?.let {
            validateAnswer(
                questions[checkpoint.indexInPool].display,
                it.toCheckpointAnswer(),
            )
        }
        if (checkpoint.phase == ArenaRunnerPhase.FEEDBACK) {
            val feedback = requireNotNull(checkpoint.pendingFeedback)
            require(
                feedback.questionId == questions[checkpoint.indexInPool].questionId &&
                    feedback.codeAnswerIndex == checkpoint.indexInPool,
            )
            validateAnswer(questions[checkpoint.indexInPool].display, feedback.answer.toCheckpointAnswer())
            require(checkpoint.currentDraft == feedback.answer) { "Feedback must restore the submitted answer" }
        } else {
            require(checkpoint.pendingFeedback == null)
        }
    }
}

/** CAS revision is checked by Room. This validates the content transition before its write. */
fun validateArenaRunnerTransition(
    previous: ArenaRunnerCheckpoint,
    next: ArenaRunnerCheckpoint,
    binding: ArenaRunnerCheckpointBinding,
) {
    validateArenaRunnerCheckpoint(previous, binding)
    validateArenaRunnerCheckpoint(next, binding)
    require(previous.sessionMode == next.sessionMode && previous.seed == next.seed)
    if (previous.phase == ArenaRunnerPhase.COMPLETING) {
        require(previous == next) { "Final checkpoint is immutable" }
        return
    }
    require(next.indexInPool in previous.indexInPool..(previous.indexInPool + 1))
    require(next.acceptedAnswers.take(previous.acceptedAnswers.size) == previous.acceptedAnswers)
    previous.chargeClaims.raw.forEachIndexed { index, old ->
        val new = next.chargeClaims.raw[index]
        if (old != ChargeClaimMask.NONE) require(old == new) { "A committed charge claim cannot disappear" }
        if (new != old) require(index == previous.indexInPool)
    }
    if (next.indexInPool == previous.indexInPool) {
        require(next.acceptedAnswers == previous.acceptedAnswers)
        require(next.questionStartedAtMs == previous.questionStartedAtMs)
        val resumes = previous.phase == ArenaRunnerPhase.PAUSED && next.phase == ArenaRunnerPhase.ANSWERING
        if (!resumes) require(next.deadlineMs == previous.deadlineMs)
        when (previous.phase) {
            ArenaRunnerPhase.ANSWERING ->
                require(
                    next.phase in setOf(ArenaRunnerPhase.ANSWERING, ArenaRunnerPhase.FEEDBACK),
                )
            ArenaRunnerPhase.FEEDBACK ->
                require(
                    next.phase == ArenaRunnerPhase.FEEDBACK &&
                        next.pendingFeedback == previous.pendingFeedback && next.currentDraft == previous.currentDraft,
                )
            ArenaRunnerPhase.PAUSED -> require(resumes || next == previous)
            ArenaRunnerPhase.COMPLETING -> error("Already handled")
        }
    } else {
        require(next.phase != ArenaRunnerPhase.FEEDBACK && previous.phase != ArenaRunnerPhase.PAUSED)
        require(
            next.acceptedAnswers.drop(previous.acceptedAnswers.size).all { it.codeAnswerIndex == previous.indexInPool },
        )
        previous.pendingFeedback?.let { feedback ->
            require(
                next.acceptedAnswers.singleOrNull { it.codeAnswerIndex == previous.indexInPool }?.answer ==
                    feedback.answer.toCheckpointAnswer(),
            ) { "A revealed answer cannot be replaced on resume/Continue" }
        }
    }
}

/** Rebuilds only local EASY feedback with the existing evaluator. HARD never gets a fake zero score. */
fun arenaCheckpointToReady(
    checkpoint: ArenaRunnerCheckpoint,
    binding: ArenaRunnerCheckpointBinding,
): RunnerState.Ready {
    validateArenaRunnerCheckpoint(checkpoint, binding)
    val h = binding.snapshot.header
    val hard = h.difficulty == Difficulty.HARD
    val playOrder =
        binding.snapshot.questions.map { question ->
            when (val display = question.display) {
                is QuestionContent ->
                    RunnerQuestion.Valid(
                        QuestionId(question.questionId),
                        question.order,
                        question.order,
                        display,
                    )
                is RedactedQuestionContent ->
                    RunnerQuestion.Redacted(
                        QuestionId(question.questionId),
                        question.order,
                        question.order,
                        display,
                    )
                else -> error("Unsupported display")
            }
        }
    val answers =
        if (hard) {
            emptyList()
        } else {
            checkpoint.acceptedAnswers.map { raw ->
                val score = evaluateAnswer(playOrder[raw.codeAnswerIndex].content as QuestionContent, raw.answer)
                AnsweredQuestion(
                    raw.questionId,
                    raw.codeAnswerIndex,
                    score,
                    raw.answer,
                    raw.answeredAtMs,
                    raw.durationMs,
                    raw.wasTimeout,
                )
            }
        }
    val code =
        if (hard) {
            null
        } else {
            CharArray(playOrder.size) { '0' }.also { chars ->
                answers.forEach { chars[it.codeAnswerIndex] = '0' + it.score.raw }
            }.concatToString().let(::CodeAnswer)
        }
    return RunnerState.Ready(
        AttemptId(h.attemptId), h.ownerUid, LessonId(h.lessonId), h.lessonVersion,
        h.difficulty, playOrder, playOrder.size, checkpoint.indexInPool, code, checkpoint.deadlineMs,
        checkpoint.seed, checkpoint.currentDraft, checkpoint.phase == ArenaRunnerPhase.PAUSED,
        checkpoint.sessionMode, checkpoint.questionStartedAtMs, answers,
        if (hard) checkpoint.acceptedAnswers.toList() else emptyList(), checkpoint.chargeClaims,
    )
}

/** Caller must persist the returned checkpoint before publishing its next question/feedback. */
fun arenaCheckpointFromReady(
    state: RunnerState.Ready,
    binding: ArenaRunnerCheckpointBinding,
    phase: ArenaRunnerPhase,
    pendingFeedback: ArenaPendingFeedback? = null,
    endedAtMs: Long? = null,
): ArenaRunnerCheckpoint {
    validateBinding(binding)
    val h = binding.snapshot.header
    require(
        state.attemptId.value == h.attemptId && state.userId == h.ownerUid &&
            state.lessonId.value == h.lessonId && state.lessonVersion == h.lessonVersion && state.mode == h.difficulty,
    )
    require(state.eligibleSize == binding.snapshot.questions.size && state.playOrder.size == state.eligibleSize)
    state.playOrder.zip(binding.snapshot.questions).forEach { (played, frozen) ->
        require(
            played.sourceId.value == frozen.questionId && played.order == frozen.order &&
                played.codeAnswerIndex == frozen.order && played.content == frozen.display,
        )
    }
    require(state.isPaused == (phase == ArenaRunnerPhase.PAUSED))
    val hard = h.difficulty == Difficulty.HARD
    require(
        if (hard) {
            state.codeAnswer == null && state.answers.isEmpty()
        } else {
            state.codeAnswer != null && state.rawAnswers.isEmpty()
        },
    )
    val answers =
        if (hard) {
            state.rawAnswers.toList()
        } else {
            state.answers.map {
                RawAnsweredQuestion(
                    it.questionId,
                    it.codeAnswerIndex,
                    it.answer,
                    it.answeredAtMs,
                    it.durationMs,
                    it.wasTimeout,
                )
            }
        }
    return ArenaRunnerCheckpoint(
        ArenaRunnerCheckpoint.VERSION, h.ownerUid, h.ticketId, h.attemptId,
        binding.snapshotSha256, state.sessionMode, state.seed, phase, state.indexInPool,
        state.questionStartedAtMs, state.deadlineMs,
        if (phase == ArenaRunnerPhase.FEEDBACK) requireNotNull(pendingFeedback).answer else state.currentDraftAnswer,
        pendingFeedback, answers,
        state.chargeClaimsOrEmpty(), endedAtMs,
    ).also { validateArenaRunnerCheckpoint(it, binding) }
}

/** This intent must use ArenaTicketJournal.freezeCompletion, never the live-hierarchy writer. */
fun arenaCheckpointCompletion(
    checkpoint: ArenaRunnerCheckpoint,
    binding: ArenaRunnerCheckpointBinding,
): UnscoredTerminalIntent {
    validateArenaRunnerCheckpoint(checkpoint, binding)
    require(checkpoint.phase == ArenaRunnerPhase.COMPLETING)
    val h = binding.snapshot.header
    return UnscoredTerminalIntent(
        AttemptIdentity(
            AttemptId(h.attemptId),
            h.ownerUid,
            LessonId(h.lessonId),
            h.lessonVersion,
            h.difficulty,
        ),
        AttemptCompletionKind.COMPLETED,
        requireNotNull(checkpoint.endedAtMs),
        checkpoint.acceptedAnswers.map { it.copy(answer = it.answer.canonicalArenaCheckpointAnswer()) },
        binding.snapshot.questions.map {
            ServedQuestion(QuestionId(it.questionId), it.order)
        },
        checkpoint.chargeClaims,
    )
}

/** Stable terminal/checkpoint bytes only; selection sets are unordered, Ordering answers are not. */
fun UserAnswer.canonicalArenaCheckpointAnswer(): UserAnswer =
    when (this) {
        is UserAnswer.SingleChoiceAnswer -> this
        is UserAnswer.MultipleChoiceAnswer -> copy(selected = selected.sortedBy { it.raw }.toSet())
        is UserAnswer.OrderingAnswer -> copy(order = order.toList())
        is UserAnswer.FillBlankAnswer -> copy(filled = filled.entries.sortedBy { it.key.raw }.associate { it.toPair() })
        is UserAnswer.SurveyAnswer -> copy(selected = selected.sortedBy { it.raw }.toSet())
    }

fun UserAnswerDraft.toCheckpointAnswer(): UserAnswer =
    when (this) {
        is UserAnswerDraft.SingleChoiceDraft -> UserAnswer.SingleChoiceAnswer(selected)
        is UserAnswerDraft.MultipleChoiceDraft -> UserAnswer.MultipleChoiceAnswer(selected.toSet())
        is UserAnswerDraft.OrderingDraft -> UserAnswer.OrderingAnswer(order.toList())
        is UserAnswerDraft.FillBlankDraft -> UserAnswer.FillBlankAnswer(filled.toMap())
        is UserAnswerDraft.SurveyDraft -> UserAnswer.SurveyAnswer(selected.toSet())
    }

fun UserAnswer.toCheckpointDraft(): UserAnswerDraft =
    when (this) {
        is UserAnswer.SingleChoiceAnswer -> UserAnswerDraft.SingleChoiceDraft(selected)
        is UserAnswer.MultipleChoiceAnswer -> UserAnswerDraft.MultipleChoiceDraft(selected.toSet())
        is UserAnswer.OrderingAnswer -> UserAnswerDraft.OrderingDraft(order.toList())
        is UserAnswer.FillBlankAnswer -> UserAnswerDraft.FillBlankDraft(filled.toMap())
        is UserAnswer.SurveyAnswer -> UserAnswerDraft.SurveyDraft(selected.toSet())
    }

private fun validateBinding(binding: ArenaRunnerCheckpointBinding) {
    val snapshot = binding.snapshot
    require(Regex("[0-9a-f]{64}").matches(binding.snapshotSha256))
    require(snapshot.header.status == ArenaTicketStatus.RESERVED)
    require(snapshot.questions.size in 1..ArenaRunnerCheckpoint.MAX_QUESTIONS)
    require(snapshot.questions.map { it.questionId }.distinct().size == snapshot.questions.size)
    snapshot.questions.forEachIndexed { index, question ->
        require(question.order == index && question.questionVersion >= 1L)
        val display = question.display
        require(display.id == null || display.id == question.questionId)
        require(display.difficultyOrNull == null || display.difficultyOrNull == snapshot.header.difficulty)
        require(
            if (snapshot.header.difficulty == Difficulty.EASY) {
                display is QuestionContent
            } else {
                display is RedactedQuestionContent || display is QuestionContent.Survey
            },
        )
    }
}

private fun validateAnswer(
    display: QuestionDisplay,
    answer: UserAnswer,
) {
    val options = answerOptionIds(display)
    when (answer) {
        is UserAnswer.SingleChoiceAnswer -> {
            require(display is QuestionContent.SingleChoice || display is RedactedQuestionContent.SingleChoice)
            require(answer.selected?.let { it.raw in options } != false)
        }
        is UserAnswer.MultipleChoiceAnswer -> {
            require(display is QuestionContent.MultipleChoice || display is RedactedQuestionContent.MultipleChoice)
            require(answer.selected.all { it.raw in options })
        }
        is UserAnswer.SurveyAnswer -> {
            require(display is QuestionContent.Survey)
            require(answer.selected.all { it.raw in options })
            require(display.allowMultiple || answer.selected.size <= 1)
        }
        is UserAnswer.OrderingAnswer -> validateOrderingAnswer(display, answer)
        is UserAnswer.FillBlankAnswer -> validateFillBlankAnswer(display, answer)
    }
}

private fun answerOptionIds(display: QuestionDisplay): Set<String> =
    when (display) {
        is QuestionContent.SingleChoice -> display.options.map { it.id.raw }
        is QuestionContent.MultipleChoice -> display.options.map { it.id.raw }
        is QuestionContent.Survey -> display.options.map { it.id.raw }
        is RedactedQuestionContent.SingleChoice -> display.options.map { it.id }
        is RedactedQuestionContent.MultipleChoice -> display.options.map { it.id }
        else -> emptyList()
    }.toSet()

private fun validateOrderingAnswer(
    display: QuestionDisplay,
    answer: UserAnswer.OrderingAnswer,
) {
    val ids =
        when (display) {
            is QuestionContent.Ordering -> display.items.map { it.id.raw }
            is RedactedQuestionContent.Ordering -> display.items.map { it.id }
            else -> throw IllegalArgumentException("Ordering answer does not match frozen type")
        }.toSet()
    require(answer.order.distinct().size == answer.order.size && answer.order.all { it.raw in ids })
}

private fun validateFillBlankAnswer(
    display: QuestionDisplay,
    answer: UserAnswer.FillBlankAnswer,
) {
    val ids =
        when (display) {
            is QuestionContent.FillBlank ->
                display.blanks.map { it.id.raw }.toSet() to display.candidates.map { it.id.raw }.toSet()
            is RedactedQuestionContent.FillBlank ->
                display.blanks.toSet() to display.candidates.map { it.id }.toSet()
            else -> throw IllegalArgumentException("Fill answer does not match frozen type")
        }
    require(
        answer.filled.all {
                (blank, candidate) ->
            blank.raw in ids.first && (candidate == null || candidate.raw in ids.second)
        },
    )
}
