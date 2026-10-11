package com.tpov.schoolquiz.shared.feature.internet.social.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxNamespace
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollOption
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishCommand
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestion
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteCommand
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteSubmission
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Строки очереди опросов (UX-O7 §2, история 8.2): ключ голоса выводится из тройки идентичности
 * и потому не меняется (§6.1), тело — один замороженный набор + момент фиксации, ссылка живёт
 * в пространстве имён операций polls (NFR4), локальной половины и версии нет (AD-25).
 */
class PollOutboxWritersTest {
    private val vote =
        PollVoteSubmission(
            id = "poll-vote-abc123",
            ownerUid = "participant-1",
            requestedAtMs = 1_760_000_000_000,
            confirmedAtMs = 1_760_000_123_000,
            command = PollVoteCommand(
                seriesId = "poll-77",
                revisionId = 2,
                questionIndex = 1,
                selectedOptionIds = listOf("opt-a", "opt-c"),
            ),
        )

    @Test
    fun `vote key is the identity triple and never varies`() {
        val first = PollOutboxWriters.voteRecord(vote)
        val again = PollOutboxWriters.voteRecord(vote)

        assertEquals(OutboxOperations.SUBMIT_POLL_VOTE, first.operation)
        assertEquals(
            OutboxOperations.mutationKey(OutboxOperations.SUBMIT_POLL_VOTE, "series-poll-77-r2-q1"),
            first.mutationId,
        )
        assertEquals(first.mutationId, again.mutationId, "§6.1: retry — не второй голос, ключ тот же")
        assertEquals(OutboxState.WAITING, first.state)
        assertNull(first.expectedVersion, "опрос клиентом не версионируется — версии в очереди нет")
        assertEquals("participant-1", first.ownerUid)
        assertEquals(1_760_000_000_000, first.createdAtMs)
    }

    @Test
    fun `different identity triples yield different keys, the same triple does not`() {
        val otherQuestion = vote.copy(command = vote.command.copy(questionIndex = 2))
        val otherRevision = vote.copy(command = vote.command.copy(revisionId = 3))
        val same = vote.copy(id = "poll-vote-other-id", confirmedAtMs = 42)

        val keys =
            setOf(
                PollOutboxWriters.voteRecord(vote).mutationId,
                PollOutboxWriters.voteRecord(otherQuestion).mutationId,
                PollOutboxWriters.voteRecord(otherRevision).mutationId,
            )
        assertEquals(3, keys.size, "каждая тройка — свой ключ")
        assertEquals(
            PollOutboxWriters.voteRecord(vote).mutationId,
            PollOutboxWriters.voteRecord(same).mutationId,
            "тот же вопрос той же revision — то же намерение, даже под другим id записи",
        )
    }

    @Test
    fun `vote entity ref and payload follow the contract`() {
        val record = PollOutboxWriters.voteRecord(vote)

        OutboxNamespace.requireSameNamespace(record.operation, record.entityRef)
        assertEquals("polls.SERIES:poll-77", record.entityRef)

        val body = Json.parseToJsonElement(record.payload).jsonObject
        assertEquals(
            setOf("seriesId", "revisionId", "questionIndex", "selectedOptionIds", "confirmedAtMs"),
            body.keys,
        )
        assertEquals("poll-77", body["seriesId"]!!.jsonPrimitive.content)
        assertEquals(2L, body["revisionId"]!!.jsonPrimitive.content.toLong())
        assertEquals(1L, body["questionIndex"]!!.jsonPrimitive.content.toLong())
        assertEquals(
            listOf("opt-a", "opt-c"),
            body["selectedOptionIds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(1_760_000_123_000L, body["confirmedAtMs"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `publish key is bound to the series and the expected next revision`() {
        val submission =
            PollPublishSubmission(
                id = "poll-publish-xyz",
                ownerUid = "author-1",
                requestedAtMs = 1_760_000_000_000,
                command = PollPublishCommand(
                    seriesId = "poll-77",
                    title = "Demo poll",
                    expectedRevisionId = 1,
                    questions =
                        listOf(
                            PollQuestion(
                                questionIndex = 0,
                                text = "Favorite color?",
                                mode = PollMode.MULTIPLE,
                                options =
                                    listOf(
                                        PollOption("opt-1", "Red"),
                                        PollOption("opt-2", "Green"),
                                    ),
                            ),
                        ),
                ),
            )

        val record = PollOutboxWriters.publishRecord(submission)
        assertEquals(OutboxOperations.PUBLISH_POLL_SERIES, record.operation)
        assertEquals(
            OutboxOperations.mutationKey(OutboxOperations.PUBLISH_POLL_SERIES, "series-poll-77-r2"),
            record.mutationId,
            "ключ публикации — серия + ожидаемая следующая revision",
        )
        OutboxNamespace.requireSameNamespace(record.operation, record.entityRef)
        assertEquals("polls.SERIES:poll-77", record.entityRef)
        assertNull(record.expectedVersion)

        val body = Json.parseToJsonElement(record.payload).jsonObject
        assertEquals(setOf("seriesId", "title", "expectedRevisionId", "questions"), body.keys)
        val questions = body["questions"]!!.jsonArray
        assertEquals(1, questions.size)
        val question = questions[0].jsonObject
        assertEquals("MULTIPLE", question["mode"]!!.jsonPrimitive.content)
        assertTrue(question.keys.containsAll(setOf("questionIndex", "text", "options")))
        assertEquals(2, question["options"]!!.jsonArray.size)
    }
}
