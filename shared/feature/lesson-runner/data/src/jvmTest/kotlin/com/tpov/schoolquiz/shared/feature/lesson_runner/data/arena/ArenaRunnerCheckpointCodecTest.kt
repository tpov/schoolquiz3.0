@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointCompletion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointFromReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointToReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingFeedback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Uses the unchanged actual issuer/recovery fixture and the journal's real completion serializer. */
class ArenaRunnerCheckpointCodecTest {
    private val codec = ArenaRunnerCheckpointCodec()
    private val journal = ArenaJournalCodec(::sha256)
    private val parser = ArenaTicketWireParser(::sha256)
    private val fixtures =
        javaClass.classLoader!!.getResourceAsStream("arena-ticket-client-fixtures.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }

    @Test
    fun actualEasyAndHardFrozenTicketsRoundTripWithoutLiveHierarchy() {
        for (hard in listOf(false, true)) {
            val binding = binding(hard)
            val initial = initialJson(binding)
            assertEquals(initial, codec.encode(codec.decode(initial, binding), binding))
            codec.validate(binding.snapshot, binding.snapshotSha256, null, initial, null)
            assertEquals(binding.snapshot.header.attemptId, codec.decode(initial, binding).attemptId)
            assertEquals(
                binding.snapshot.questions.map { it.questionId },
                arenaCheckpointToReady(codec.decode(initial, binding), binding).playOrder.map { it.sourceId.value },
            )
        }
    }

    @Test
    fun codecImplementsRequiredJournalInterfaceWithNoDefaultValidator() {
        val validator: ArenaJournalCheckpointCodec = codec
        val binding = binding()
        val json = validator.initial(binding.snapshot, binding.snapshotSha256, initialRequest(binding))
        validator.validate(binding.snapshot, binding.snapshotSha256, json, json, null)
    }

    @Test
    fun nonInitialCheckpointCannotPassFreshLaunchGate() {
        val binding = binding()
        val finalJson = codec.encode(final(binding), binding)
        assertFailsWith<IllegalArgumentException> {
            codec.validate(binding.snapshot, binding.snapshotSha256, null, finalJson, null)
        }
    }

    @Test
    fun actualJournalFinalBodyMatchesDurableCheckpointInBothDifficulties() {
        for (hard in listOf(false, true)) {
            val binding = binding(hard)
            val checkpoint = final(binding)
            val json = codec.encode(checkpoint, binding)
            val body = finalBody(checkpoint, binding)
            codec.validate(binding.snapshot, binding.snapshotSha256, json, json, body)
            codec.validate(binding.snapshot, binding.snapshotSha256, json, json, body)
            assertFalse("percentScore" in Json.parseToJsonElement(body).jsonObject)
            assertFalse("codeAnswer" in Json.parseToJsonElement(body).jsonObject)
        }
    }

    @Test
    fun multipleChoiceTerminalBodyIsByteIdenticalBeforeAndAfterCheckpointRoundTrip() {
        assertStableTerminal(
            QuestionContent.MultipleChoice(
                "q1",
                Difficulty.EASY,
                "Select",
                null,
                choices(),
                setOf(OptionId("a"), OptionId("b")),
            ),
            UserAnswer.MultipleChoiceAnswer(linkedSetOf(OptionId("b"), OptionId("a"))),
        )
    }

    @Test
    fun surveyTerminalBodyIsByteIdenticalBeforeAndAfterCheckpointRoundTrip() {
        assertStableTerminal(
            QuestionContent.Survey("q1", Difficulty.EASY, "Survey", null, choices(), allowMultiple = true),
            UserAnswer.SurveyAnswer(linkedSetOf(OptionId("b"), OptionId("a"))),
        )
    }

    @Test
    fun fillBlankTerminalBodyIsByteIdenticalBeforeAndAfterCheckpointRoundTrip() {
        assertStableTerminal(
            QuestionContent.FillBlank(
                "q1",
                Difficulty.EASY,
                "Fill {b1} and {b2}",
                null,
                listOf(
                    QuestionContent.Blank(BlankId("b1"), CandidateId("c1")),
                    QuestionContent.Blank(BlankId("b2"), CandidateId("c2")),
                ),
                (1..5).map { QuestionContent.Candidate(CandidateId("c$it"), "Candidate $it") },
            ),
            UserAnswer.FillBlankAnswer(linkedMapOf(BlankId("b2") to null, BlankId("b1") to CandidateId("c1"))),
        )
    }

    @Test
    fun orderingTerminalProjectionNeverSortsTheSubmittedSequence() {
        val binding =
            bindingWithContent(
                QuestionContent.Ordering(
                    "q1",
                    Difficulty.EASY,
                    "Order",
                    null,
                    listOf(
                        QuestionContent.OrderItem(OptionId("a"), "A"),
                        QuestionContent.OrderItem(OptionId("b"), "B"),
                    ),
                ),
            )
        val answer = UserAnswer.OrderingAnswer(listOf(OptionId("b"), OptionId("a")))
        val checkpoint = final(binding, answer)
        val restored = codec.decode(codec.encode(checkpoint, binding), binding)
        assertEquals(answer, arenaCheckpointCompletion(checkpoint, binding).answers.single().answer)
        assertEquals(answer, arenaCheckpointCompletion(restored, binding).answers.single().answer)
        assertEquals(finalBody(checkpoint, binding), finalBody(restored, binding))
    }

    @Test
    fun changedOwnerAttemptVersionDifficultyOrEndRefusesFinalFreeze() {
        val binding = binding()
        val checkpoint = final(binding)
        val body = finalBody(checkpoint, binding)
        val changes =
            mapOf(
                "ownerUid" to JsonPrimitive("foreign"),
                "userId" to JsonPrimitive("foreign"),
                "ticketId" to JsonPrimitive("f".repeat(64)),
                "attemptId" to JsonPrimitive("f".repeat(64)),
                "lessonVersion" to JsonPrimitive(99),
                "difficulty" to JsonPrimitive("HARD"),
                "createdAtMs" to JsonPrimitive(99),
                "completedAtMs" to JsonPrimitive(99),
            )
        changes.forEach { (key, value) -> rejectsFinal(checkpoint, binding, change(body, key, value)) }
    }

    @Test
    fun partialOrReorderedServedListCannotShrinkFinalDenominator() {
        val binding = binding()
        val checkpoint = final(binding)
        val body = finalBody(checkpoint, binding)
        rejectsFinal(checkpoint, binding, change(body, "served", JsonArray(emptyList())))
        val served = Json.parseToJsonElement(body).jsonObject.getValue("served").jsonArray
        rejectsFinal(checkpoint, binding, change(body, "served", JsonArray(served + served)))
        val row = served.single().jsonObject
        rejectsFinal(
            checkpoint,
            binding,
            change(body, "served", JsonArray(listOf(JsonObject(row + ("codeAnswerIndex" to JsonPrimitive(1)))))),
        )
    }

    @Test
    fun differentAnswerDurationOrTimeoutCannotReplaceFinalIntent() {
        val binding = binding()
        val checkpoint = final(binding)
        val body = finalBody(checkpoint, binding)
        val row = Json.parseToJsonElement(body).jsonObject.getValue("answers").jsonArray.single().jsonObject
        for ((key, value) in mapOf(
            "answeredAtMs" to JsonPrimitive(999),
            "durationMs" to JsonPrimitive(999),
            "wasTimeout" to JsonPrimitive(true),
            "questionId" to JsonPrimitive("other"),
            "answerPayload" to
                JsonPrimitive(
                    Json.encodeToString(UserAnswer.serializer(), UserAnswer.SingleChoiceAnswer(null)),
                ),
        )) {
            rejectsFinal(
                checkpoint,
                binding,
                change(body, "answers", JsonArray(listOf(JsonObject(row + (key to value))))),
            )
        }
    }

    @Test
    fun finalClaimsAbortAndNumericScoresCannotOverrideCheckpoint() {
        val binding = binding()
        val checkpoint = final(binding)
        val body = finalBody(checkpoint, binding)
        rejectsFinal(checkpoint, binding, change(body, "chargeClaims", JsonPrimitive("S")))
        rejectsFinal(checkpoint, binding, change(body, "completionKind", JsonPrimitive("ABORTED")))
        rejectsFinal(checkpoint, binding, change(body, "percentScore", JsonPrimitive(100)))
        rejectsFinal(checkpoint, binding, change(body, "codeAnswer", JsonPrimitive("9")))
    }

    @Test
    fun finalContextMustBeFrozenNotAReparentedLesson() {
        val binding = binding()
        val checkpoint = final(binding)
        val body = finalBody(checkpoint, binding)
        for (field in listOf("catalogId", "questId", "sectionId", "themeId")) {
            rejectsFinal(checkpoint, binding, change(body, field, JsonPrimitive("edited-parent")))
        }
    }

    @Test
    fun pendingFeedbackRoundTripDoesNotBecomeAnAcceptedAnswer() {
        val binding = binding()
        val initial = codec.decode(initialJson(binding), binding)
        val draft = UserAnswerDraft.SingleChoiceDraft(OptionId("b"))
        val feedback =
            initial.copy(
                phase = ArenaRunnerPhase.FEEDBACK,
                currentDraft = draft,
                pendingFeedback = ArenaPendingFeedback("q1", 0, draft),
            )
        val json = codec.encode(feedback, binding)
        val restored = codec.decode(json, binding)
        assertEquals(feedback, restored)
        assertTrue(restored.acceptedAnswers.isEmpty())
        assertEquals(draft, restored.pendingFeedback?.answer)
        codec.validate(binding.snapshot, binding.snapshotSha256, initialJson(binding), json, null)
    }

    @Test
    fun contradictoryFeedbackDraftIsRejectedBeforeJournalPersistence() {
        val binding = binding()
        val selected = UserAnswerDraft.SingleChoiceDraft(OptionId("a"))
        val checkpoint =
            codec.decode(initialJson(binding), binding).copy(
                phase = ArenaRunnerPhase.FEEDBACK,
                currentDraft = selected,
                pendingFeedback = ArenaPendingFeedback("q1", 0, selected),
            )
        val json = codec.encode(checkpoint, binding)
        val differentDraft =
            Json.parseToJsonElement(
                codec.encode(
                    checkpoint.copy(
                        currentDraft = UserAnswerDraft.SingleChoiceDraft(OptionId("b")),
                        pendingFeedback =
                            ArenaPendingFeedback(
                                "q1",
                                0,
                                UserAnswerDraft.SingleChoiceDraft(OptionId("b")),
                            ),
                    ),
                    binding,
                ),
            ).jsonObject.getValue("currentDraft")
        for (draft in listOf(kotlinx.serialization.json.JsonNull, differentDraft)) {
            assertFailsWith<IllegalArgumentException> {
                codec.validate(
                    binding.snapshot,
                    binding.snapshotSha256,
                    initialJson(binding),
                    change(json, "currentDraft", draft),
                    null,
                )
            }
        }
    }

    @Test
    fun unknownScoreTokenAndNestedFieldsAreNotPersisted() {
        val binding = binding()
        val payload = initialJson(binding)
        for (key in listOf("percentScore", "codeAnswer", "authToken", "futureField")) {
            assertFailsWith<IllegalArgumentException> {
                codec.decode(
                    change(payload, key, JsonPrimitive("never-store")),
                    binding,
                )
            }
        }
        val withDraft =
            codec.encode(
                codec.decode(payload, binding).copy(currentDraft = UserAnswerDraft.SingleChoiceDraft(null)),
                binding,
            )
        val draft = Json.parseToJsonElement(withDraft).jsonObject.getValue("currentDraft").jsonObject
        assertFailsWith<IllegalArgumentException> {
            codec.decode(
                change(withDraft, "currentDraft", JsonObject(draft + ("secret" to JsonPrimitive("key")))),
                binding,
            )
        }
    }

    @Test
    fun duplicateObjectKeysAreRejectedInsteadOfKeepingTheLastOne() {
        val binding = binding()
        val json = initialJson(binding)
        val changed = json.replace("\"indexInPool\":0", "\"indexInPool\":9,\"indexInPool\":0")
        assertFailsWith<IllegalArgumentException> { codec.decode(changed, binding) }
        val escapedAlias = json.replace("\"seed\":91", "\"s\\u0065ed\":7,\"seed\":91")
        assertFailsWith<IllegalArgumentException> { codec.decode(escapedAlias, binding) }
    }

    @Test
    fun wrongPrimitiveTypesUnknownVersionAndNegativeClockFailClosed() {
        val binding = binding()
        val json = initialJson(binding)
        for ((key, value) in mapOf(
            "schemaVersion" to JsonPrimitive(2),
            "indexInPool" to JsonPrimitive("0"),
            "seed" to JsonPrimitive(1.25),
            "deadlineMs" to JsonPrimitive(-1),
            "phase" to JsonPrimitive("DONE"),
        )) {
            assertFailsWith<IllegalArgumentException> { codec.decode(change(json, key, value), binding) }
        }
    }

    @Test
    fun stringsWithEscapedBracesAreNotMistakenForJsonNesting() {
        val binding = binding()
        val json = initialJson(binding)
        // It fails as an unknown field, not by overflowing/reinterpreting a string as JSON.
        assertFailsWith<IllegalArgumentException> {
            codec.decode(change(json, "future", JsonPrimitive("[\"{\\}".repeat(100))), binding)
        }
        assertEquals(json, codec.encode(codec.decode(json, binding), binding))
    }

    @Test
    fun overOneMiBAndDeeplyNestedInputRejectBeforeDeserialization() {
        val binding = binding()
        assertFailsWith<IllegalArgumentException> {
            codec.decode(" ".repeat(ArenaRunnerCheckpointCodec.MAX_ENCODED_BYTES + 1), binding)
        }
        assertFailsWith<IllegalArgumentException> { codec.decode("[".repeat(40) + "0" + "]".repeat(40), binding) }
    }

    @Test
    fun localFormatContainsNeitherQuestionPayloadNorGradingKey() {
        val binding = binding(hard = true)
        val json = initialJson(binding)
        val keys = Json.parseToJsonElement(json).jsonObject.keys
        assertTrue(keys.containsAll(setOf("snapshotSha256", "ownerUid", "ticketId", "attemptId", "phase")))
        for (forbidden in listOf(
            "payload",
            "correctOptionId",
            "gradingKey",
            "accessToken",
            "lifePoints",
            "percentScore",
        )) {
            assertFalse(json.contains("\"$forbidden\""))
        }
    }

    @Test
    fun completedTimestampCannotChangeOnRetryEvenWithEquivalentAnswers() {
        val binding = binding()
        val checkpoint = final(binding)
        val before = codec.encode(checkpoint, binding)
        val after = codec.encode(checkpoint.copy(endedAtMs = checkpoint.endedAtMs!! + 1L), binding)
        assertFailsWith<IllegalArgumentException> {
            codec.validate(binding.snapshot, binding.snapshotSha256, before, after, null)
        }
    }

    private fun binding(hard: Boolean = false): ArenaRunnerCheckpointBinding {
        val name = if (hard) "issuedHard" else "recoveredSelected"
        val fixture = fixtures.getValue(name)
        val ticketId = fixture.jsonObject.getValue("ticket").jsonObject.getValue("ticketId").jsonPrimitive.content
        val observed =
            if (hard) {
                assertIs<ArenaPrepareResponse.Ticket>(
                    parser.prepare(
                        fixture.toString(),
                        ArenaPrepareRequest.Selected("player", "client-hard", Difficulty.HARD, "lesson", 10),
                    ),
                ).ticket
            } else {
                parser.get(fixture.toString(), ArenaTicketGetRequest("player", ticketId))
            }
        val ticket = assertIs<ArenaEntryTicket.Reserved>(observed)
        val row = journal.record(ticket, 1_000L)
        return ArenaRunnerCheckpointBinding(ticket.snapshot, requireNotNull(row.snapshotSha256))
    }

    private fun initialRequest(binding: ArenaRunnerCheckpointBinding): ArenaInitialCheckpoint {
        val seconds =
            computeTimer(
                binding.snapshot.questions.first().display,
                binding.snapshot.header.difficulty,
                TimerCoefficients.Default,
            ).seconds
        return ArenaInitialCheckpoint(SessionMode.LEARNING, 91L, 1_000L, 1_000L + seconds * 1_000L)
    }

    private fun initialJson(binding: ArenaRunnerCheckpointBinding) =
        codec.initial(binding.snapshot, binding.snapshotSha256, initialRequest(binding))

    /** Additional type cases do not rewrite or loosen the unchanged actual network fixtures. */
    private fun bindingWithContent(content: QuestionContent): ArenaRunnerCheckpointBinding {
        val base = binding()
        val question =
            base.snapshot.questions.single().copy(
                payload = Json.encodeToString(QuestionContent.serializer(), content),
                display = content,
            )
        val snapshot = base.snapshot.copy(questions = listOf(question))
        val row = journal.record(ArenaEntryTicket.Reserved(snapshot), 1_000L)
        return ArenaRunnerCheckpointBinding(journal.decode(row).snapshot, requireNotNull(row.snapshotSha256))
    }

    private fun choices() =
        listOf(
            QuestionContent.Option(OptionId("a"), "A"),
            QuestionContent.Option(OptionId("b"), "B"),
        )

    private fun assertStableTerminal(
        content: QuestionContent,
        answer: UserAnswer,
    ) {
        val binding = bindingWithContent(content)
        val checkpoint = final(binding, answer)
        val json = codec.encode(checkpoint, binding)
        val restored = codec.decode(json, binding)
        val body = finalBody(checkpoint, binding)
        assertEquals(body, finalBody(restored, binding))
        assertEquals(sha256(body), sha256(finalBody(restored, binding)))
        codec.validate(binding.snapshot, binding.snapshotSha256, json, json, body)
        // The old direct projection produced a semantically equal but different immutable command.
        val row = journal.record(ArenaEntryTicket.Reserved(binding.snapshot), 1_000L)
        val noncanonical =
            journal.completion(
                row,
                arenaCheckpointCompletion(checkpoint, binding).copy(answers = checkpoint.acceptedAnswers),
            ).bodyJson
        assertNotEquals(noncanonical, body)
        rejectsFinal(checkpoint, binding, noncanonical)
        assertEquals(answer, checkpoint.acceptedAnswers.single().answer)
    }

    private fun final(
        binding: ArenaRunnerCheckpointBinding,
        answer: UserAnswer,
    ): ArenaRunnerCheckpoint {
        val checkpoint = codec.decode(initialJson(binding), binding)
        val answered = submitAnswer(arenaCheckpointToReady(checkpoint, binding), answer, 1_100L)
        return arenaCheckpointFromReady(answered, binding, ArenaRunnerPhase.COMPLETING, endedAtMs = 1_101L)
    }

    private fun final(binding: ArenaRunnerCheckpointBinding): ArenaRunnerCheckpoint {
        val checkpoint = codec.decode(initialJson(binding), binding)
        val display = binding.snapshot.questions.single().display
        val option =
            when (display) {
                is QuestionContent.SingleChoice -> display.correctOptionId
                is RedactedQuestionContent.SingleChoice -> OptionId(display.options.first().id)
                else -> error("Actual fixture is single choice")
            }
        val answered =
            submitAnswer(arenaCheckpointToReady(checkpoint, binding), UserAnswer.SingleChoiceAnswer(option), 1_100L)
        return arenaCheckpointFromReady(answered, binding, ArenaRunnerPhase.COMPLETING, endedAtMs = 1_101L)
    }

    private fun finalBody(
        checkpoint: ArenaRunnerCheckpoint,
        binding: ArenaRunnerCheckpointBinding,
    ): String {
        val row = journal.record(ArenaEntryTicket.Reserved(binding.snapshot), 1_000L)
        return journal.completion(row, arenaCheckpointCompletion(checkpoint, binding)).bodyJson
    }

    private fun rejectsFinal(
        checkpoint: ArenaRunnerCheckpoint,
        binding: ArenaRunnerCheckpointBinding,
        body: String,
    ) {
        val json = codec.encode(checkpoint, binding)
        assertFailsWith<IllegalArgumentException> {
            codec.validate(
                binding.snapshot,
                binding.snapshotSha256,
                json,
                json,
                body,
            )
        }
    }

    private fun change(
        payload: String,
        key: String,
        value: JsonElement,
    ) = JsonObject(Json.parseToJsonElement(payload).jsonObject + (key to value)).toString()

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
