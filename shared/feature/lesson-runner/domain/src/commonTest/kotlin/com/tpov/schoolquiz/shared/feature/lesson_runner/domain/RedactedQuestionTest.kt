package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A question whose answer key has been removed fails by its own name.
 *
 * Before this, [StartLessonAttemptUseCase] parsed with `parse`, which refuses a redacted payload,
 * and `mapNotNull` dropped the refusal — so a whole lesson of redacted questions reported itself as
 * `NoValidQuestions`, the same message a corrupt payload produces. Nobody reading that screen, or a
 * support report of it, could tell the two apart.
 *
 * The runner still will not play a redacted question; that is step 9 of the E2 plan, which deletes
 * `RedactedNotSupported` when it lands. What is settled here is only which failure is reported.
 *
 * Two neighbouring behaviours are pinned elsewhere and deliberately not duplicated here:
 * `EdgeCasesTest` test 58 pins an easy-only lesson opened as hard reporting `EmptyPool`, and test 59
 * pins all-invalid-payloads reporting `NoValidQuestions`. The cases below are the ones that only
 * exist once something is redacted.
 */
class RedactedQuestionTest {

    private val clock = FakeClock()
    private val authRepo = FakeAuthRepository(uid = "user1")
    private val lessonRepo = FakeLessonRepository()
    private val questionRepo = FakeQuestionRepository()
    private val parser = FakeQuestionContentParser()

    private val startUseCase get() = StartLessonAttemptUseCase(
        questionRepository = questionRepo,
        lessonRepository = lessonRepo,
        parser = parser,
        authRepository = authRepo,
        clock = clock,
        randomSeedProvider = { 12345L },
    )

    // ── Matrix row: every question redacted ──────────────────────────────────

    @Test
    fun `a lesson whose questions all lost their answer key says so`() = runTest {
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val q1 = makeQuestion(id = "q1", order = 0)
        val q2 = makeQuestion(id = "q2", order = 1)
        parser.addRedacted(q1.payload, redactedSingleChoiceContent())
        parser.addRedacted(q2.payload, redactedSingleChoiceContent())
        questionRepo.setQuestions(listOf(q1, q2))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)

        assertIs<RunnerState.InitFailed>(result)
        assertEquals(InitFailureReason.RedactedNotSupported, result.reason)
    }

    @Test
    fun `an ordering question counts the same as any other redacted shape`() = runTest {
        // The type check spans all four redacted variants; ordering is the one whose redacted form
        // differs most from its twin, because the emitter shuffles the items and re-issues the ids.
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val q = makeQuestion(id = "q1", order = 0)
        parser.addRedacted(q.payload, redactedOrderingContent())
        questionRepo.setQuestions(listOf(q))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)

        assertIs<RunnerState.InitFailed>(result)
        assertEquals(InitFailureReason.RedactedNotSupported, result.reason)
    }

    // ── Matrix row: some redacted, some playable ─────────────────────────────

    @Test
    fun `a mixed lesson plays on the questions that kept their answer key`() = runTest {
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val playable1 = makeQuestion(id = "q1", order = 0)
        val redacted = makeQuestion(id = "q2", order = 1)
        val playable2 = makeQuestion(id = "q3", order = 2)
        parser.addResponse(playable1.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        parser.addRedacted(redacted.payload, redactedSingleChoiceContent())
        parser.addResponse(playable2.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        questionRepo.setQuestions(listOf(playable1, redacted, playable2))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)

        assertIs<RunnerState.Ready>(result)
        assertEquals(2, result.eligibleSize, "the redacted question is not part of the pool")
        assertEquals(
            listOf("q1", "q3"),
            result.playOrder.map { it.sourceId.value }.sorted(),
            "only the questions that kept their answer key are offered",
        )
        assertEquals("00", result.codeAnswer.raw, "the code is sized to the playable pool")
    }

    // ── Matrix row: some redacted, none playable for this difficulty ─────────

    @Test
    fun `a redacted hard question is why hard is unplayable`() = runTest {
        // The easy question is readable and fine; it is simply not what was opened. The hard pool is
        // empty because the one hard question had its answer key removed, so that is what is said.
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val easyPlayable = makeQuestion(id = "q1", order = 0)
        val redactedHard = makeQuestion(id = "q2", order = 1)
        parser.addResponse(easyPlayable.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        parser.addRedacted(redactedHard.payload, redactedSingleChoiceContent(difficulty = Difficulty.HARD.name))
        questionRepo.setQuestions(listOf(easyPlayable, redactedHard))

        val result = startUseCase(LessonId("lesson1"), Difficulty.HARD)

        assertIs<RunnerState.InitFailed>(result)
        assertEquals(InitFailureReason.RedactedNotSupported, result.reason)
    }

    // ── The difficulty scoping: a redacted question must explain *this* pool ──

    @Test
    fun `a redacted easy question does not explain an empty hard pool`() = runTest {
        // An easy-only lesson opened as hard has no hard questions whether or not anything was
        // redacted. Blaming redaction here would name the wrong cause; EmptyPool is the truth.
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val easyPlayable = makeQuestion(id = "q1", order = 0)
        val redactedEasy = makeQuestion(id = "q2", order = 1)
        parser.addResponse(easyPlayable.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        parser.addRedacted(redactedEasy.payload, redactedSingleChoiceContent(difficulty = Difficulty.EASY.name))
        questionRepo.setQuestions(listOf(easyPlayable, redactedEasy))

        val result = startUseCase(LessonId("lesson1"), Difficulty.HARD)

        assertIs<RunnerState.InitFailed>(result)
        assertEquals(InitFailureReason.EmptyPool, result.reason)
    }

    @Test
    fun `a redacted easy question does explain an empty easy pool`() = runTest {
        // The mirror of the case above, so that the scoping is pinned in both directions and not
        // satisfied by a rule that simply never counts anything.
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val hardPlayable = makeQuestion(id = "q1", order = 0)
        val redactedEasy = makeQuestion(id = "q2", order = 1)
        parser.addResponse(hardPlayable.payload, singleChoiceContent(difficulty = Difficulty.HARD))
        parser.addRedacted(redactedEasy.payload, redactedSingleChoiceContent(difficulty = Difficulty.EASY.name))
        questionRepo.setQuestions(listOf(hardPlayable, redactedEasy))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)

        assertIs<RunnerState.InitFailed>(result)
        assertEquals(InitFailureReason.RedactedNotSupported, result.reason)
    }

    // ── An unreadable difficulty counts toward whichever pool was opened ─────

    @Test
    fun `a redacted question whose difficulty is absent counts for either pool`() = runTest {
        assertUnreadableDifficultyCountsForBothPools(difficulty = null)
    }

    @Test
    fun `a redacted question whose difficulty is empty counts for either pool`() = runTest {
        assertUnreadableDifficultyCountsForBothPools(difficulty = "")
    }

    @Test
    fun `a redacted question whose difficulty is an unknown name counts for either pool`() = runTest {
        assertUnreadableDifficultyCountsForBothPools(difficulty = "MEDIUM")
    }

    /**
     * Absent, empty and unrecognised are all shapes `question-redaction.js` really emits, which is
     * why the wire field is a nullable string. None of them can be resolved to a pool, so the
     * question might belong to either — and naming the redaction is the safer answer for both,
     * since the alternative is telling a player there is simply nothing here when a withheld answer
     * key is the likelier cause.
     */
    private suspend fun assertUnreadableDifficultyCountsForBothPools(difficulty: String?) {
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val q = makeQuestion(id = "q1", order = 0)
        parser.addRedacted(q.payload, redactedSingleChoiceContent(difficulty = difficulty))
        questionRepo.setQuestions(listOf(q))

        val easy = startUseCase(LessonId("lesson1"), Difficulty.EASY)
        val hard = startUseCase(LessonId("lesson1"), Difficulty.HARD)

        assertIs<RunnerState.InitFailed>(easy)
        assertIs<RunnerState.InitFailed>(hard)
        assertEquals(InitFailureReason.RedactedNotSupported, easy.reason, "difficulty=$difficulty, opened EASY")
        assertEquals(InitFailureReason.RedactedNotSupported, hard.reason, "difficulty=$difficulty, opened HARD")
    }

    // ── Matrix row: redacted and broken together ─────────────────────────────

    @Test
    fun `a removed answer key outranks a broken payload`() = runTest {
        // Both facts are true; the removed answer key is the more specific and the more actionable.
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val redacted = makeQuestion(id = "q1", order = 0)
        val broken = makeQuestion(id = "q2", order = 1, payload = "malformed")
        parser.addRedacted(redacted.payload, redactedSingleChoiceContent())
        parser.addFailure(broken.payload)
        questionRepo.setQuestions(listOf(redacted, broken))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)

        assertIs<RunnerState.InitFailed>(result)
        assertEquals(InitFailureReason.RedactedNotSupported, result.reason)
    }

    // ── The fake's own contract, which the cases above lean on ───────────────

    @Test
    fun `a redacted payload is still refused by parse`() = runTest {
        // Production keeps the two hierarchies disjoint so that a redacted payload can never
        // surface as a QuestionContent. A fake that returned one from parse would model the
        // impossible, and every case above would be resting on it.
        parser.setDefaultContent(singleChoiceContent())
        val q = makeQuestion(id = "q1", order = 0)
        parser.addRedacted(q.payload, redactedSingleChoiceContent())

        assertTrue(parser.parse(q.payload).isFailure, "parse must refuse a redacted payload")
        assertTrue(
            parser.parseForDisplay(q.payload, "q1", "text", Difficulty.EASY).isSuccess,
            "parseForDisplay is the entry point that reads it",
        )
    }

    @Test
    fun `the parser is consulted once per question whichever entry point answers`() = runTest {
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val playable = makeQuestion(id = "q1", order = 0)
        val redacted = makeQuestion(id = "q2", order = 1)
        parser.addResponse(playable.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        parser.addRedacted(redacted.payload, redactedSingleChoiceContent())
        questionRepo.setQuestions(listOf(playable, redacted))

        startUseCase(LessonId("lesson1"), Difficulty.EASY)

        assertEquals(2, parser.parseCallCount, "a redacted question must not go uncounted")
    }
}
