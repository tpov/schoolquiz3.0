@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointCompletion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointFromReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointToReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.claimCharge
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.initialArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.toCheckpointAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.toCheckpointDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.validateArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.validateArenaRunnerTransition
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingFeedback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunnerCheckpointTest {
    @Test
    fun initialUsesServerAttemptAndFrozenOrderWithoutSelection() {
        val binding = binding(size = 20)
        val checkpoint = initial(binding)
        val ready = arenaCheckpointToReady(checkpoint, binding)
        assertEquals(binding.snapshot.header.attemptId, ready.attemptId.value)
        assertEquals(binding.snapshot.questions.map { it.questionId }, ready.playOrder.map { it.sourceId.value })
        assertEquals((0 until 20).toList(), ready.playOrder.map { it.codeAnswerIndex })
        assertEquals(20, ready.eligibleSize)
        assertEquals("0".repeat(20), ready.codeAnswer?.raw)
        assertEquals(checkpoint, arenaCheckpointFromReady(ready, binding, ArenaRunnerPhase.ANSWERING))
    }

    @Test
    fun hardIncludingSurveyOnlyNeverCreatesLocalScore() {
        val survey =
            QuestionContent.Survey(
                "q1",
                Difficulty.HARD,
                "Survey",
                null,
                listOf(QuestionContent.Option(optId("A"), "A"), QuestionContent.Option(optId("B"), "B")),
            )
        for (binding in listOf(binding(hard = true), binding(hard = true, display = survey, size = 1))) {
            val ready = arenaCheckpointToReady(initial(binding), binding)
            assertNull(ready.codeAnswer)
            assertTrue(ready.answers.isEmpty())
        }
    }

    @Test
    fun easyAnswerReconstructsExistingEvaluatorAndDropsNumericAuthority() {
        val binding = binding()
        val ready = arenaCheckpointToReady(initial(binding), binding)
        val answered = submitAnswer(ready, UserAnswer.SingleChoiceAnswer(optId("A")), 1_100L)
        val checkpoint = arenaCheckpointFromReady(answered, binding, ArenaRunnerPhase.ANSWERING)
        val restored = arenaCheckpointToReady(checkpoint, binding)
        assertEquals(answered.answers, restored.answers)
        assertEquals(answered.codeAnswer, restored.codeAnswer)
        assertEquals(answered.deadlineMs, restored.deadlineMs)
        assertEquals(answered.seed, restored.seed)
        assertEquals(1, restored.indexInPool)
        assertEquals(100L, checkpoint.acceptedAnswers.single().durationMs)
    }

    @Test
    fun hardTimeoutOmissionRetainsClosedPositionAndFullDenominator() {
        val binding = binding(hard = true, size = 1)
        val ready = arenaCheckpointToReady(initial(binding), binding)
        val ended = autoAnswerOnTimeout(ready, ready.seed, 6_000L)
        val checkpoint = arenaCheckpointFromReady(ended, binding, ArenaRunnerPhase.COMPLETING, endedAtMs = 6_001L)
        val intent = arenaCheckpointCompletion(checkpoint, binding)
        assertTrue(intent.answers.isEmpty())
        assertEquals(listOf(QuestionId("q1")), intent.served.map { it.questionId })
        assertEquals(".", intent.claims.raw)
        assertNull(arenaCheckpointToReady(checkpoint, binding).codeAnswer)
    }

    @Test
    fun easyTimeoutKeepsActualGeneratedAnswerRatherThanInventingOnRestore() {
        val binding = binding(size = 1)
        val before = arenaCheckpointToReady(initial(binding), binding)
        val timed = autoAnswerOnTimeout(before, before.seed, 6_000L)
        val checkpoint = arenaCheckpointFromReady(timed, binding, ArenaRunnerPhase.COMPLETING, endedAtMs = 6_000L)
        assertEquals(timed.answers, arenaCheckpointToReady(checkpoint, binding).answers)
        assertTrue(checkpoint.acceptedAnswers.single().wasTimeout)
    }

    @Test
    fun pendingFeedbackSurvivesWithoutPrematureSubmission() {
        val binding = binding()
        val before = initial(binding)
        val selected = UserAnswerDraft.SingleChoiceDraft(optId("A"))
        val feedback =
            before.copy(
                phase = ArenaRunnerPhase.FEEDBACK,
                currentDraft = selected,
                pendingFeedback = ArenaPendingFeedback("q1", 0, selected),
            )
        validateArenaRunnerTransition(before, feedback, binding)
        assertEquals(selected, arenaCheckpointToReady(feedback, binding).currentDraftAnswer)
        assertEquals(selected, feedback.pendingFeedback?.answer)
        assertTrue(feedback.acceptedAnswers.isEmpty())
        assertFailsWith<IllegalArgumentException> { arenaCheckpointCompletion(feedback, binding) }
    }

    @Test
    fun feedbackRejectsMissingOrContradictoryRestoredSelection() {
        val binding = binding()
        val selected = UserAnswerDraft.SingleChoiceDraft(optId("A"))
        val feedback =
            initial(binding).copy(
                phase = ArenaRunnerPhase.FEEDBACK,
                currentDraft = selected,
                pendingFeedback = ArenaPendingFeedback("q1", 0, selected),
            )
        for (draft in listOf(null, UserAnswerDraft.SingleChoiceDraft(optId("B")))) {
            assertFailsWith<IllegalArgumentException> {
                arenaCheckpointToReady(feedback.copy(currentDraft = draft), binding)
            }
        }
    }

    @Test
    fun untouchedOrderingSubmissionRestoresTheCapturedFeedbackAnswerBeforeContinue() {
        val ordering =
            QuestionContent.Ordering(
                "q1",
                Difficulty.EASY,
                "Order",
                null,
                listOf(QuestionContent.OrderItem(optId("A"), "A"), QuestionContent.OrderItem(optId("B"), "B")),
            )
        val binding = binding(size = 1, display = ordering)
        val before = initial(binding)
        val ready = arenaCheckpointToReady(before, binding)
        assertNull(ready.currentDraftAnswer)
        val submitted = UserAnswerDraft.OrderingDraft(listOf(optId("B"), optId("A")))
        val checkpoint =
            arenaCheckpointFromReady(
                ready,
                binding,
                ArenaRunnerPhase.FEEDBACK,
                ArenaPendingFeedback("q1", 0, submitted),
            )
        validateArenaRunnerTransition(before, checkpoint, binding)
        val restored = arenaCheckpointToReady(checkpoint, binding)
        assertEquals(submitted, restored.currentDraftAnswer)
        val answered = submitAnswer(restored, restored.currentDraftAnswer!!.toCheckpointAnswer(), 1_200L)
        val final = arenaCheckpointFromReady(answered, binding, ArenaRunnerPhase.COMPLETING, endedAtMs = 1_201L)
        validateArenaRunnerTransition(checkpoint, final, binding)
        assertEquals(submitted.toCheckpointAnswer(), arenaCheckpointCompletion(final, binding).answers.single().answer)
    }

    @Test
    fun feedbackAnswerCannotChangeAfterRevealingCorrectness() {
        val binding = binding()
        val selected = UserAnswerDraft.SingleChoiceDraft(optId("A"))
        val feedback =
            initial(binding).copy(
                phase = ArenaRunnerPhase.FEEDBACK,
                currentDraft = selected,
                pendingFeedback = ArenaPendingFeedback("q1", 0, selected),
            )
        val wrong = UserAnswerDraft.SingleChoiceDraft(optId("B"))
        assertFailsWith<IllegalArgumentException> {
            validateArenaRunnerTransition(
                feedback,
                feedback.copy(pendingFeedback = ArenaPendingFeedback("q1", 0, wrong)),
                binding,
            )
        }
        val answered = submitAnswer(arenaCheckpointToReady(feedback, binding), wrong.toCheckpointAnswer(), 1_200L)
        assertFailsWith<IllegalArgumentException> {
            validateArenaRunnerTransition(
                feedback,
                arenaCheckpointFromReady(answered, binding, ArenaRunnerPhase.ANSWERING),
                binding,
            )
        }
    }

    @Test
    fun sameFeedbackContinueAdvancesExactlyOneFrozenSlot() {
        val binding = binding()
        val selected = UserAnswerDraft.SingleChoiceDraft(optId("A"))
        val feedback =
            initial(binding).copy(
                phase = ArenaRunnerPhase.FEEDBACK,
                currentDraft = selected,
                pendingFeedback = ArenaPendingFeedback("q1", 0, selected),
            )
        val answered = submitAnswer(arenaCheckpointToReady(feedback, binding), selected.toCheckpointAnswer(), 1_200L)
        val next = arenaCheckpointFromReady(answered, binding, ArenaRunnerPhase.ANSWERING)
        validateArenaRunnerTransition(feedback, next, binding)
        assertEquals(1, next.indexInPool)
        assertNull(next.pendingFeedback)
    }

    @Test
    fun ownerTicketAttemptOrFrozenHashMismatchIsNotResumePermission() {
        val binding = binding()
        val c = initial(binding)
        for (bad in listOf(
            c.copy(ownerUid = "other"),
            c.copy(ticketId = "c".repeat(64)),
            c.copy(attemptId = "d".repeat(64)),
            c.copy(snapshotSha256 = "e".repeat(64)),
        )) {
            assertFailsWith<IllegalArgumentException> { validateArenaRunnerCheckpoint(bad, binding) }
        }
    }

    @Test
    fun terminalTicketAndChangedFrozenOrderFailClosed() {
        val binding = binding()
        val checkpoint = initial(binding)
        for (status in listOf(ArenaTicketStatus.CANCELLED, ArenaTicketStatus.SETTLED)) {
            val terminal =
                binding.copy(
                    snapshot = binding.snapshot.copy(header = binding.snapshot.header.copy(status = status)),
                )
            assertFailsWith<IllegalArgumentException> { arenaCheckpointToReady(checkpoint, terminal) }
        }
        val reversed = binding.copy(snapshot = binding.snapshot.copy(questions = binding.snapshot.questions.reversed()))
        assertFailsWith<IllegalArgumentException> { arenaCheckpointToReady(checkpoint, reversed) }
    }

    @Test
    fun corruptedQuestionIdentityOrAnswerOptionCannotBeRestored() {
        val binding = binding()
        val c = afterOne(binding)
        val raw = c.acceptedAnswers.single()
        for (bad in listOf(
            raw.copy(questionId = QuestionId("q2")),
            raw.copy(codeAnswerIndex = 1),
            raw.copy(answer = UserAnswer.SingleChoiceAnswer(optId("foreign"))),
        )) {
            assertFailsWith<IllegalArgumentException> {
                validateArenaRunnerCheckpoint(c.copy(acceptedAnswers = listOf(bad)), binding)
            }
        }
    }

    @Test
    fun closedAnswersCannotBeDroppedReplacedOrReanswered() {
        val binding = binding(hard = true)
        val before = afterOne(binding)
        val raw = before.acceptedAnswers.single()
        for (changed in listOf(emptyList(), listOf(raw.copy(answeredAtMs = raw.answeredAtMs + 1L)), listOf(raw, raw))) {
            assertFailsWith<IllegalArgumentException> {
                validateArenaRunnerTransition(before, before.copy(acceptedAnswers = changed), binding)
            }
        }
    }

    @Test
    fun frozenSeedAndTimerCannotResetDuringOrdinaryRestoreOrDraftEdit() {
        val binding = binding()
        val before = initial(binding)
        for (bad in listOf(
            before.copy(seed = 2L),
            before.copy(deadlineMs = before.deadlineMs + 1L),
            before.copy(questionStartedAtMs = before.questionStartedAtMs + 1L),
            before.copy(sessionMode = SessionMode.EXAM),
        )) {
            assertFailsWith<IllegalArgumentException> { validateArenaRunnerTransition(before, bad, binding) }
        }
        assertEquals(before.deadlineMs, arenaCheckpointToReady(before, binding).deadlineMs)
    }

    @Test
    fun existingPauseThenExplicitContinueMayResetDeadlineNotIdentity() {
        val binding = binding()
        val paused = afterOne(binding).copy(phase = ArenaRunnerPhase.PAUSED)
        val restored = arenaCheckpointToReady(paused, binding)
        assertTrue(restored.isPaused)
        assertEquals(paused.deadlineMs, restored.deadlineMs)
        val continued = paused.copy(phase = ArenaRunnerPhase.ANSWERING, deadlineMs = paused.deadlineMs + 100L)
        validateArenaRunnerTransition(paused, continued, binding)
        assertEquals(paused.questionStartedAtMs, continued.questionStartedAtMs)
        assertEquals(paused.attemptId, continued.attemptId)
    }

    @Test
    fun initialTimerMustUseExistingFormulaAndCannotOverflow() {
        val binding = binding()
        val first = initial(binding)
        assertFailsWith<IllegalArgumentException> {
            initialArenaRunnerCheckpoint(
                binding,
                ArenaInitialCheckpoint(SessionMode.LEARNING, 1L, 1_000L, first.deadlineMs + 1L),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            initialArenaRunnerCheckpoint(
                binding,
                ArenaInitialCheckpoint(SessionMode.LEARNING, 1L, Long.MAX_VALUE, Long.MAX_VALUE),
            )
        }
    }

    @Test
    fun chargeClaimIsRetainedButNeverLocallyDebitsWallet() {
        val binding = binding()
        val before = initial(binding)
        val ready = claimCharge(arenaCheckpointToReady(before, binding), ChargeClaimMask.Claim.STANDARD_HINT)
        val claimed = arenaCheckpointFromReady(ready, binding, ArenaRunnerPhase.ANSWERING)
        validateArenaRunnerTransition(before, claimed, binding)
        assertEquals("S.", claimed.chargeClaims.raw)
        assertFailsWith<IllegalArgumentException> { validateArenaRunnerTransition(claimed, before, binding) }
        assertFailsWith<IllegalArgumentException> {
            validateArenaRunnerCheckpoint(before.copy(chargeClaims = ChargeClaimMask(".S")), binding)
        }
    }

    @Test
    fun plasmaCannotOverlayARecordedHardAnswerOrCrossDifficulty() {
        val hard = binding(hard = true)
        val answered = afterOne(hard)
        assertFailsWith<IllegalArgumentException> {
            validateArenaRunnerCheckpoint(answered.copy(chargeClaims = ChargeClaimMask("P.")), hard)
        }
        assertFailsWith<IllegalArgumentException> {
            validateArenaRunnerCheckpoint(initial(hard).copy(chargeClaims = ChargeClaimMask("S.")), hard)
        }
        val easy = binding()
        assertFailsWith<IllegalArgumentException> {
            validateArenaRunnerCheckpoint(initial(easy).copy(chargeClaims = ChargeClaimMask("P.")), easy)
        }
        val skipped = initial(hard).copy(indexInPool = 1, chargeClaims = ChargeClaimMask("P."))
        validateArenaRunnerCheckpoint(skipped, hard)
        assertTrue(arenaCheckpointToReady(skipped, hard).rawAnswers.isEmpty())
    }

    @Test
    fun completeCheckpointHasOneFixedEndAndCannotReturnToGameplay() {
        val binding = binding(size = 1)
        val ready =
            submitAnswer(
                arenaCheckpointToReady(initial(binding), binding),
                UserAnswer.SingleChoiceAnswer(optId("A")),
                1_500L,
            )
        val final = arenaCheckpointFromReady(ready, binding, ArenaRunnerPhase.COMPLETING, endedAtMs = 1_501L)
        validateArenaRunnerTransition(final, final, binding)
        assertEquals(arenaCheckpointCompletion(final, binding), arenaCheckpointCompletion(final, binding))
        assertEquals(1_501L, arenaCheckpointCompletion(final, binding).endedAtMs)
        assertFailsWith<IllegalArgumentException> {
            validateArenaRunnerTransition(final, final.copy(endedAtMs = 1_502L), binding)
        }
        assertFailsWith<IllegalArgumentException> {
            validateArenaRunnerCheckpoint(final.copy(phase = ArenaRunnerPhase.ANSWERING), binding)
        }
    }

    @Test
    fun boundIsTwentyQuestionsAndNoSilentPoolTruncation() {
        assertEquals(20, arenaCheckpointToReady(initial(binding(size = 20)), binding(size = 20)).playOrder.size)
        assertFailsWith<IllegalArgumentException> { initial(binding(size = 21)) }
        assertFailsWith<IllegalArgumentException> { initial(binding(size = 0)) }
    }

    @Test
    fun allDraftVariantsPreserveNullAndOrderWithoutGrading() {
        val drafts =
            listOf(
                UserAnswerDraft.SingleChoiceDraft(null),
                UserAnswerDraft.MultipleChoiceDraft(setOf(optId("B"), optId("A"))),
                UserAnswerDraft.OrderingDraft(listOf(optId("B"), optId("A"))),
                UserAnswerDraft.FillBlankDraft(mapOf(blankId("b1") to null, blankId("b2") to candId("x"))),
                UserAnswerDraft.SurveyDraft(emptySet()),
            )
        drafts.forEach { assertEquals(it, it.toCheckpointAnswer().toCheckpointDraft()) }
    }

    private fun initial(binding: ArenaRunnerCheckpointBinding): ArenaRunnerCheckpoint {
        val duration =
            binding.snapshot.questions.firstOrNull()?.let {
                computeTimer(it.display, binding.snapshot.header.difficulty, TimerCoefficients.Default).seconds * 1_000L
            } ?: 0L
        return initialArenaRunnerCheckpoint(
            binding,
            ArenaInitialCheckpoint(SessionMode.LEARNING, 91L, 1_000L, 1_000L + duration),
        )
    }

    private fun afterOne(binding: ArenaRunnerCheckpointBinding): ArenaRunnerCheckpoint {
        val option = if (binding.snapshot.header.difficulty == Difficulty.HARD) "ri-A" else "A"
        val ready =
            submitAnswer(
                arenaCheckpointToReady(initial(binding), binding),
                UserAnswer.SingleChoiceAnswer(optId(option)),
                1_100L,
            )
        return arenaCheckpointFromReady(ready, binding, ArenaRunnerPhase.ANSWERING)
    }

    private fun binding(
        hard: Boolean = false,
        size: Int = 2,
        display: QuestionDisplay? = null,
    ): ArenaRunnerCheckpointBinding {
        val difficulty = if (hard) Difficulty.HARD else Difficulty.EASY
        val header =
            ArenaTicketHeader(
                1, "a".repeat(64), "b".repeat(64), "owner", "request", ArenaEntryMode.SELECTED,
                difficulty, "lesson", 3L, ArenaTicketStatus.RESERVED, 10, 50L, 100L, "2026-09-07",
            )
        val questions =
            (0 until size).map { index ->
                val id = "q${index + 1}"
                val content =
                    display ?: if (hard) {
                        redactedSingleChoiceContent(difficulty = "HARD").copy(id = id)
                    } else {
                        singleChoiceContent().copy(id = id)
                    }
                ArenaTicketQuestion(id, 2L, index, "en", "not-read-by-domain", content)
            }
        return ArenaRunnerCheckpointBinding(ArenaTicketSnapshot(header, "author", 2L, null, questions), "c".repeat(64))
    }
}
