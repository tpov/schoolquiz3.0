package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [QuestionDisplay] — the supertype both question hierarchies implement — and
 * [QuestionContentParser.parseForDisplay], the one entry point that reads either.
 *
 * Nothing in production consumes either yet, so these tests are the whole of what holds the seam
 * still until the runner moves onto it. Three claims, in order of what they would cost:
 *
 * 1. **The supertype cannot reveal an answer.** Not through a field, and not through an order —
 *    which is why [QuestionDisplay] exposes [QuestionDisplay.charsCount] and not the option texts.
 *    For an [QuestionContent.Ordering] the stored order *is* the answer, and for a
 *    [QuestionContent.FillBlank] the authoring component writes the correct candidates first.
 * 2. **The number does not move.** [QuestionDisplay.charsCount] prices every lesson's reward and
 *    unlock, and `questionCharsCount` in `functions/lesson-reward.js` computes it independently.
 *    The cross-language half of that proof lives in `redacted-question-fixtures.json` and is
 *    asserted by `RedactedQuestionWireTest` and `question-redaction-wire.test.js`; this file holds
 *    the Kotlin-internal half — that the supertype agrees with the per-type code the runner uses
 *    today, and exactly where it deliberately does not.
 * 3. **The old entry points still refuse a redacted payload**, which is what keeps
 *    `DefaultQuestCreateComponent` and `DefaultReviewQueueComponent` — which re-encode whatever
 *    they parse — from overwriting a stored question with its own public half.
 */
class QuestionDisplayTest {

    private val parser = KotlinxSerializationQuestionContentParser()

    // ── The count, against the per-type code the runner uses today ────────────────────────────

    /**
     * `computeCharsCount` as `RunnerLogic.kt` computes it today, copied here because this module
     * does not depend on lesson-runner — the dependency runs the other way.
     *
     * The **only** copy of the per-type formula left in the tests. It exists to prove that moving
     * the runner onto [QuestionDisplay] is a deletion rather than a behaviour change, and it should
     * be deleted in the same commit as the original.
     */
    private fun runnerCharsCountToday(content: QuestionContent): Int {
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

    private fun assertSameCharsCount(content: QuestionContent, expected: Int) {
        assertEquals(
            expected,
            runnerCharsCountToday(content),
            "the copy of today's per-type code disagrees with the hand-computed number — " +
                "the fixture is wrong, not the supertype",
        )
        assertEquals(
            runnerCharsCountToday(content),
            content.charsCount,
            "the supertype changed ${content::class.simpleName}'s character count, and with it " +
                "every such question's timer, reward and unlock price",
        )
    }

    @Test
    fun `given a SingleChoice then the supertype counts what the per-type code counts`() {
        // "Q?" = 2, "Alpha" = 5, "Beta" = 4, no image.
        assertSameCharsCount(singleChoice(), expected = 11)
    }

    @Test
    fun `given a MultipleChoice then the supertype counts what the per-type code counts`() {
        // "Pick two" = 8, "Alpha" + "Beta" + "Gamma" = 14, no image.
        assertSameCharsCount(multipleChoice(), expected = 22)
    }

    @Test
    fun `given an Ordering then the supertype counts what the per-type code counts`() {
        // "Order these" = 11, "First" + "Second" = 11, no image.
        assertSameCharsCount(ordering(), expected = 22)
    }

    @Test
    fun `given a FillBlank then the supertype counts what the per-type code counts`() {
        // "A __ b" = 6, five candidates of 3 = 15, no image.
        assertSameCharsCount(fillBlank(), expected = 21)
    }

    @Test
    fun `given a Survey then the supertype counts what the per-type code counts`() {
        // "Favourite?" = 10, "Alpha" + "Beta" = 9, no image.
        assertSameCharsCount(survey(), expected = 19)
    }

    @Test
    fun `given a question with a real image then the flat hundred is counted`() {
        assertSameCharsCount(singleChoice().copy(imageUrl = "https://example.test/a.png"), expected = 111)
    }

    /**
     * The one place the supertype deliberately disagrees with `RunnerLogic.computeCharsCount`.
     *
     * The server writes `content.imageUrl ? IMAGE_CHARS : 0`, and JavaScript reads `""` as false,
     * so an empty-string `imageUrl` is worth nothing there. Today's per-type code tests
     * `imageUrl != null` and pays a hundred characters for the same question — a client/server
     * disagreement about the number the server pays real rewards from. The supertype follows the
     * server, because the server is what pays.
     *
     * It costs nothing today: `DefaultQuestCreateComponent` normalises a blank image path to `null`
     * (`cleanNullable`) before it ever reaches a [QuestionContent], so no question the app can
     * author has an empty one, and for `null` and for any real URL the two rules agree. Pinned as
     * an explicit disagreement rather than left implicit, so that whoever deletes
     * `computeCharsCount` when the runner moves has to read this.
     */
    @Test
    fun `given an empty-string imageUrl then the supertype follows the server and not the old rule`() {
        val empty = singleChoice().copy(imageUrl = "")

        assertEquals(11, empty.charsCount, "an empty imageUrl is worth nothing, as it is on the server")
        assertEquals(111, runnerCharsCountToday(empty), "today's per-type code still pays for it")
        assertNotEquals(
            runnerCharsCountToday(empty),
            empty.charsCount,
            "this disagreement is the point of the test; if it has gone, RunnerLogic was changed",
        )
        // Every other shape of imageUrl agrees, which is what makes the divergence safe to land.
        assertSameCharsCount(singleChoice(), expected = 11)
        assertSameCharsCount(singleChoice().copy(imageUrl = "https://example.test/a.png"), expected = 111)
    }

    /**
     * `protectedTextSegments` are answers spelled out in full. Neither the timer nor the server
     * counts them, and counting them would both move the number and re-price the question.
     */
    @Test
    fun `given a FillBlank with protected segments then they are not counted`() {
        val guarded = fillBlank().copy(protectedTextSegments = listOf("Alpha", "a very long protected answer"))

        assertSameCharsCount(guarded, expected = 21)
        assertEquals(fillBlank().charsCount, guarded.charsCount)
    }

    /** Redaction must not reprice a question: the two halves are worth the same to the timer. */
    @Test
    fun `given a redacted payload then it is worth what its full twin is worth`() {
        val full = singleChoice()
        val redacted = RedactedQuestionContent.SingleChoice(
            id = full.id,
            difficulty = full.difficulty.name,
            text = full.text,
            imageUrl = full.imageUrl,
            options = full.options.map { RedactedQuestionContent.Row(id = it.id.raw, text = it.text) },
        )

        assertEquals(full.charsCount, redacted.charsCount, "redaction must not change what a question is worth")
    }

    @Test
    fun `given every redacted shape then the choice characters are summed and the image counted`() {
        val rows = listOf(RedactedQuestionContent.Row("r0", "Alpha"), RedactedQuestionContent.Row("r1", "Beta"))

        // "Q?" = 2 plus "Alpha" + "Beta" = 9.
        assertEquals(11, redactedSingleChoice(options = rows).charsCount)
        assertEquals(
            111,
            RedactedQuestionContent.MultipleChoice(
                text = "Q?",
                imageUrl = "https://example.test/a.png",
                options = rows,
            ).charsCount,
        )
        assertEquals(
            11,
            RedactedQuestionContent.Ordering(text = "Q?", imageUrl = null, items = rows).charsCount,
        )
        assertEquals(
            11,
            RedactedQuestionContent.FillBlank(
                text = "Q?",
                imageUrl = null,
                blanks = listOf("rb-0"),
                candidates = rows,
                protectedTextSegments = listOf("a protected answer"),
            ).charsCount,
            "protectedTextSegments must not be counted on the redacted side either",
        )
        assertEquals(
            11,
            redactedSingleChoice(options = rows).copy(imageUrl = "").charsCount,
            "an empty imageUrl is worth nothing here too",
        )
    }

    // ── The supertype cannot reveal an answer ─────────────────────────────────────────────────

    /**
     * Everything [QuestionDisplay] declares, enumerated and bound to a local of its declared type.
     *
     * This is the test that would have caught the defect this member replaced: a `displayTexts:
     * List<String>` compiles here just as happily as an `Int`, but it hands a renderer the answer
     * to an [QuestionContent.Ordering] — whose stored order *is* the answer — and to a
     * [QuestionContent.FillBlank], whose correct candidates are written first. Widening the
     * supertype means editing this list, which is the point: a count is not orderable, a list is.
     */
    @Test
    fun `given a question seen as the supertype then only unorderable facts are reachable`() {
        val display: QuestionDisplay = ordering()

        val id: String? = display.id
        val text: String = display.text
        val imageUrl: String? = display.imageUrl
        val choiceCharsCount: Int = display.choiceCharsCount
        val charsCount: Int = display.charsCount
        val difficulty: Difficulty? = display.difficultyOrNull

        assertEquals("q3", id)
        assertEquals("Order these", text)
        assertNull(imageUrl)
        assertEquals(11, choiceCharsCount)
        assertEquals(22, charsCount)
        assertEquals(Difficulty.EASY, difficulty)
    }

    /**
     * The property stated directly: two questions that differ only in the order of their choices
     * are indistinguishable through the supertype. For an ordering question that order is the
     * answer, so anything that told them apart would be publishing it.
     */
    @Test
    fun `given two orderings differing only in order then the supertype cannot tell them apart`() {
        val authored = ordering()
        val shuffled = authored.copy(items = authored.items.reversed())

        assertEquals(authored.charsCount, shuffled.charsCount)
        assertEquals(authored.choiceCharsCount, shuffled.choiceCharsCount)
        assertEquals(
            listOf<Any?>(authored.id, authored.text, authored.imageUrl, authored.difficultyOrNull),
            listOf<Any?>(shuffled.id, shuffled.text, shuffled.imageUrl, shuffled.difficultyOrNull),
            "the supertype's remaining members must not distinguish two orderings either",
        )
        assertNotEquals(authored.items, shuffled.items, "the two really do differ underneath")
    }

    /** The same for a fill-blank, whose candidate order leaks the answer for the same reason. */
    @Test
    fun `given two fill-blanks differing only in candidate order then the supertype cannot tell them apart`() {
        val authored = fillBlank()
        val shuffled = authored.copy(candidates = authored.candidates.reversed())

        assertEquals(authored.charsCount, shuffled.charsCount)
        assertEquals(authored.choiceCharsCount, shuffled.choiceCharsCount)
        assertNotEquals(authored.candidates, shuffled.candidates)
    }

    // ── Difficulty: the form both implementors can answer ─────────────────────────────────────

    @Test
    fun `given a full question then difficultyOrNull is its enum`() {
        assertEquals(Difficulty.EASY, (singleChoice() as QuestionDisplay).difficultyOrNull)
        assertEquals(
            Difficulty.HARD,
            (singleChoice().copy(difficulty = Difficulty.HARD) as QuestionDisplay).difficultyOrNull,
        )
    }

    /**
     * Absent, empty, and a name the enum has no case for are all shapes `question-redaction.js` is
     * written to emit, because it copies a payload's difficulty verbatim rather than substituting
     * one. Read through the supertype they must report unknown, not throw.
     *
     * Note that the server does **not** agree here: `lessonAllocatedSeconds` reads a question's
     * pool as `String(content.difficulty || "EASY").toUpperCase()`, so an absent or empty
     * difficulty is EASY there. Reporting unknown is what the E2.6 specification froze, and that
     * divergence is recorded on [QuestionDisplay.difficultyOrNull] for whoever moves the runner.
     */
    @Test
    fun `given a redacted payload with an unreadable difficulty then the supertype reports unknown`() {
        for (raw in listOf(null, "", "MEDIUM", "easy", "  ")) {
            val display: QuestionDisplay = redactedSingleChoice(difficulty = raw)

            assertNull(
                display.difficultyOrNull,
                "difficulty ${raw?.let { "\"$it\"" } ?: "absent"} must read as unknown, not as a pool",
            )
        }
        assertEquals(Difficulty.EASY, (redactedSingleChoice(difficulty = "EASY") as QuestionDisplay).difficultyOrNull)
        assertEquals(Difficulty.HARD, (redactedSingleChoice(difficulty = "HARD") as QuestionDisplay).difficultyOrNull)
    }

    @Test
    fun `given a redacted payload with an unreadable difficulty when parsed for display then it still parses`() {
        val shapes = listOf(
            "absent" to "",
            "empty" to "\"difficulty\":\"\",",
            "a name the enum has no case for" to "\"difficulty\":\"MEDIUM\",",
        )
        for ((described, fragment) in shapes) {
            val payload = "{\"type\":\"SingleChoiceRedacted\",\"id\":\"q1\"," + fragment +
                "\"text\":\"Q?\",\"imageUrl\":null," +
                "\"options\":[{\"id\":\"r0\",\"text\":\"Alpha\"},{\"id\":\"r1\",\"text\":\"Beta\"}]}"

            val result = parser.parseForDisplay(payload, "fallback-id", "Fallback?", Difficulty.EASY)

            val display = assertIs<RedactedQuestionContent.SingleChoice>(
                result.getOrNull(),
                "difficulty $described: expected a redacted SingleChoice, got ${result.exceptionOrNull()}",
            )
            assertNull(display.difficultyOrNull, "difficulty $described must read as unknown")
        }
    }

    /** The fallback difficulty belongs to the legacy branch alone; it must not colour a redacted one. */
    @Test
    fun `given a redacted payload with no difficulty then the fallback difficulty is not applied`() {
        val payload =
            """{"type":"SingleChoiceRedacted","text":"Q?","imageUrl":null,""" +
                """"options":[{"id":"r0","text":"Alpha"},{"id":"r1","text":"Beta"}]}"""

        val display = parser.parseForDisplay(payload, "fallback-id", "Fallback?", Difficulty.HARD).getOrThrow()

        assertNull(display.difficultyOrNull, "a redacted payload is not legacy; fallbacks do not apply to it")
        assertNull(display.id, "the fallback id must not be applied either")
    }

    // ── parseForDisplay ───────────────────────────────────────────────────────────────────────

    @Test
    fun `given every ordinary shape when parsed for display then it is the full type parse returns`() {
        for ((name, payload) in ordinaryPayloads()) {
            val throughParse = parser.parse(payload)
            val throughDisplay = parser.parseForDisplay(payload, "fallback-id", "Fallback?", Difficulty.HARD)

            assertTrue(throughParse.isSuccess, "$name: parse() failed — ${throughParse.exceptionOrNull()}")
            assertEquals(
                throughParse.getOrNull(),
                throughDisplay.getOrNull(),
                "$name: the display entry point must return exactly what parse() returns",
            )
        }
    }

    @Test
    fun `given every redacted shape when parsed for display then it is the redacted type`() {
        for ((name, payload, expected) in redactedPayloads()) {
            val result = parser.parseForDisplay(payload, "fallback-id", "Fallback?", Difficulty.EASY)

            val decoded = result.getOrNull()
            assertTrue(decoded != null, "$name: expected a redacted value, got ${result.exceptionOrNull()}")
            assertEquals(expected, decoded::class.simpleName, "$name: wrong redacted variant")
            assertIs<RedactedQuestionContent>(decoded, name)
        }
    }

    /**
     * A hand-listed payload list quietly stops covering the hierarchy the day a fifth variant is
     * declared, so the list is checked against the variants the serializer actually knows about —
     * the same trick `RedactedQuestionWireTest.declaredVariants` uses on the fixture file.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the redacted payloads cover every variant the hierarchy declares`() {
        val variants = RedactedQuestionContent.serializer().descriptor.getElementDescriptor(1)
        val declared = (0 until variants.elementsCount).map { variants.getElementName(it) }.toSet()
        val covered = redactedPayloads().map { it.first }.toSet()

        assertEquals(
            declared,
            covered,
            "the hand-listed redacted payloads have drifted from the declared variants",
        )
    }

    /**
     * A redacted payload the redacted decoder cannot read must be diagnosed as a broken *redacted*
     * payload, not as an unregistered [QuestionContent].
     *
     * Trying both decoders and keeping whichever exception came first answers
     * `{"type":"FillBlankRedacted", …}` with "Serializer for subclass 'FillBlankRedacted' is not
     * found in the polymorphic scope of 'QuestionContent'" — naming a contract the payload has
     * nothing to do with and sending whoever reads the log to the wrong file. That is the same
     * wrong-contract diagnosis the previous slice removed from the legacy branch, and it comes back
     * through this door unless the dispatch decides on the discriminator *before* decoding.
     *
     * Each case asserts the field the redacted decoder actually complained about, so the test fails
     * if the message merely stops mentioning QuestionContent without becoming useful.
     */
    @Test
    fun `given a broken redacted payload then the failure is about the redacted contract`() {
        val brokenByShape = listOf(
            Triple(
                "missing candidates",
                """{"type":"FillBlankRedacted","text":"A __ b","imageUrl":null,"blanks":["rb-0"]}""",
                "candidates",
            ),
            Triple(
                "missing text",
                """{"type":"SingleChoiceRedacted","imageUrl":null,"options":[]}""",
                "text",
            ),
            Triple(
                "items is not a list",
                """{"type":"OrderingRedacted","text":"Q?","imageUrl":null,"items":"not-a-list"}""",
                "items",
            ),
        )
        for ((described, payload, complaint) in brokenByShape) {
            val result = parser.parseForDisplay(payload, "fallback-id", "Fallback?", Difficulty.EASY)

            assertTrue(result.isFailure, "$described: expected failure, got ${result.getOrNull()}")
            val message = result.exceptionOrNull()?.message.orEmpty()
            assertTrue(
                message.contains(complaint),
                "$described: the message should name '$complaint', the field the redacted decoder " +
                    "could not read — got: $message",
            )
            assertFalse(
                message.contains("polymorphic scope of 'QuestionContent'"),
                "$described: a redacted payload was diagnosed against the QuestionContent " +
                    "hierarchy — $message",
            )
            assertFalse(
                message.contains("legacy", ignoreCase = true),
                "$described: blames the legacy format — $message",
            )
        }
    }

    // ── The old entry points still refuse a redacted payload ─────────────────────────────────

    @Test
    fun `given every redacted shape when parsed by the old entry points then both still refuse it`() {
        for ((name, payload, _) in redactedPayloads()) {
            val plain = parser.parse(payload)
            assertTrue(plain.isFailure, "$name: parse() accepted a redacted payload — ${plain.getOrNull()}")

            val enriched = parser.parse(payload, "fallback-id", "Fallback?", Difficulty.EASY)
            assertTrue(
                enriched.isFailure,
                "$name: the enriched parse() accepted a redacted payload — ${enriched.getOrNull()}",
            )

            val message = enriched.exceptionOrNull()?.message.orEmpty()
            assertTrue(message.contains(name), "$name: the message does not name the discriminator — $message")
            assertFalse(message.contains("legacy", ignoreCase = true), "$name: blames the legacy format — $message")
        }
    }

    /**
     * The disjointness the dispatch rests on, in the direction nothing else checks: no ordinary or
     * legacy payload may be readable as a [RedactedQuestionContent]. Without this, a redacted
     * variant given a colliding `@SerialName` would route an ordinary question into the redacted
     * hierarchy — losing its answer — with every other test still green.
     */
    @Test
    fun `given every ordinary and legacy payload then the redacted decoder refuses it`() {
        val lenient = Json { ignoreUnknownKeys = true }
        val payloads = ordinaryPayloads() + ("legacy" to legacyPayload())
        for ((name, payload) in payloads) {
            val decoded = runCatching {
                lenient.decodeFromString(RedactedQuestionContent.serializer(), payload)
            }.getOrNull()

            assertNull(decoded, "$name: an ordinary payload decoded into a redacted ${decoded?.let { it::class.simpleName }}")
        }
    }

    // ── Legacy is unchanged through every entry point ────────────────────────────────────────

    @Test
    fun `given a legacy payload then every entry point reads it exactly as it did`() {
        val payload = legacyPayload()

        assertTrue(parser.parse(payload).isFailure, "the plain parse() never read a legacy payload")

        val enriched = parser.parse(payload, "legacy-1", "Legacy?", Difficulty.HARD).getOrThrow()
        val forDisplay = parser.parseForDisplay(payload, "legacy-1", "Legacy?", Difficulty.HARD).getOrThrow()

        assertEquals(enriched, forDisplay, "the display entry point must synthesise the same legacy value")
        val content = assertIs<QuestionContent.SingleChoice>(forDisplay)
        assertEquals("legacy-1", content.id)
        assertEquals(Difficulty.HARD, content.difficulty)
        assertEquals(OptionId("opt-2"), content.correctOptionId)
        // "Legacy?" = 7, "Alpha" + "Beta" + "Gamma" = 14, no image.
        assertSameCharsCount(content, expected = 21)
    }

    @Test
    fun `given a payload of neither hierarchy then the display entry point keeps the accurate diagnosis`() {
        for (payload in listOf("""{"type":"VideoQuestion","foo":"bar"}""", "", "{ not json")) {
            val throughParse = parser.parse(payload, "fallback-id", "Fallback?", Difficulty.EASY)
            val throughDisplay = parser.parseForDisplay(payload, "fallback-id", "Fallback?", Difficulty.EASY)

            assertTrue(throughDisplay.isFailure, "expected failure for $payload, got ${throughDisplay.getOrNull()}")
            assertEquals(
                throughParse.exceptionOrNull()?.message,
                throughDisplay.exceptionOrNull()?.message,
                "the display entry point must keep the failure the enriched parse() reports for $payload",
            )
        }
    }

    // ── The supertype adds nothing to the wire ───────────────────────────────────────────────

    /**
     * [QuestionDisplay.choiceCharsCount] and [QuestionDisplay.difficultyOrNull] are body `val`s
     * with getters, so they have no backing field and kotlinx does not encode them. Asserted as an
     * exact key set rather than an absence check, so that any new encoded key has to be
     * acknowledged here.
     */
    @Test
    fun `given every full shape when encoded then the keys are what they were`() {
        val json = Json
        assertEncodedKeys(
            json.encodeToString(QuestionContent.serializer(), singleChoice()),
            setOf("type", "id", "difficulty", "text", "imageUrl", "options", "correctOptionId"),
        )
        assertEncodedKeys(
            json.encodeToString(QuestionContent.serializer(), multipleChoice()),
            setOf("type", "id", "difficulty", "text", "imageUrl", "options", "correctOptionIds"),
        )
        assertEncodedKeys(
            json.encodeToString(QuestionContent.serializer(), ordering()),
            setOf("type", "id", "difficulty", "text", "imageUrl", "items"),
        )
        assertEncodedKeys(
            json.encodeToString(QuestionContent.serializer(), fillBlank()),
            setOf("type", "id", "difficulty", "text", "imageUrl", "blanks", "candidates"),
        )
        assertEncodedKeys(
            json.encodeToString(QuestionContent.serializer(), survey()),
            setOf("type", "id", "difficulty", "text", "imageUrl", "options"),
        )
    }

    @Test
    fun `given every redacted shape when encoded then the keys are what they were`() {
        val json = Json { encodeDefaults = true }
        val rows = listOf(RedactedQuestionContent.Row("r0", "Alpha"))
        val base = setOf("type", "id", "difficulty", "text", "imageUrl")

        assertEncodedKeys(
            json.encodeToString(RedactedQuestionContent.serializer(), redactedSingleChoice(options = rows)),
            base + "options",
        )
        assertEncodedKeys(
            json.encodeToString(
                RedactedQuestionContent.serializer(),
                RedactedQuestionContent.MultipleChoice(text = "Q?", imageUrl = null, options = rows),
            ),
            base + "options",
        )
        assertEncodedKeys(
            json.encodeToString(
                RedactedQuestionContent.serializer(),
                RedactedQuestionContent.Ordering(text = "Q?", imageUrl = null, items = rows),
            ),
            base + "items",
        )
        assertEncodedKeys(
            json.encodeToString(
                RedactedQuestionContent.serializer(),
                RedactedQuestionContent.FillBlank(
                    text = "Q?",
                    imageUrl = null,
                    blanks = listOf("rb-0"),
                    candidates = rows,
                ),
            ),
            base + setOf("blanks", "candidates", "protectedTextSegments"),
        )
    }

    /** A payload in, the same bytes out — the strongest form of "the wire format did not move". */
    @Test
    fun `given an ordinary payload when decoded and re-encoded then the bytes come back identical`() {
        val json = Json { ignoreUnknownKeys = true }
        for ((name, payload) in ordinaryPayloads()) {
            val reEncoded = json.encodeToString(
                QuestionContent.serializer(),
                json.decodeFromString(QuestionContent.serializer(), payload),
            )

            assertEquals(payload, reEncoded, "$name: the encoded bytes moved")
        }
    }

    private fun assertEncodedKeys(encoded: String, expected: Set<String>) {
        val keys = Json.parseToJsonElement(encoded).jsonObject.keys
        assertEquals(expected, keys, "encoded keys changed; encoded value was $encoded")
        assertFalse("choiceCharsCount" in keys, "choiceCharsCount reached the wire — it must stay a getter")
        assertFalse("charsCount" in keys, "charsCount reached the wire — it must stay a getter")
        assertFalse("difficultyOrNull" in keys, "difficultyOrNull reached the wire — it must stay a getter")
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────────────────

    private fun singleChoice() = QuestionContent.SingleChoice(
        id = "q1",
        difficulty = Difficulty.EASY,
        text = "Q?",
        imageUrl = null,
        options = listOf(
            QuestionContent.Option(OptionId("o1"), "Alpha"),
            QuestionContent.Option(OptionId("o2"), "Beta"),
        ),
        correctOptionId = OptionId("o1"),
    )

    private fun multipleChoice() = QuestionContent.MultipleChoice(
        id = "q2",
        difficulty = Difficulty.HARD,
        text = "Pick two",
        imageUrl = null,
        options = listOf(
            QuestionContent.Option(OptionId("o1"), "Alpha"),
            QuestionContent.Option(OptionId("o2"), "Beta"),
            QuestionContent.Option(OptionId("o3"), "Gamma"),
        ),
        correctOptionIds = setOf(OptionId("o1"), OptionId("o2")),
    )

    private fun ordering() = QuestionContent.Ordering(
        id = "q3",
        difficulty = Difficulty.EASY,
        text = "Order these",
        imageUrl = null,
        items = listOf(
            QuestionContent.OrderItem(OptionId("i1"), "First"),
            QuestionContent.OrderItem(OptionId("i2"), "Second"),
        ),
    )

    private fun fillBlank() = QuestionContent.FillBlank(
        id = "q4",
        difficulty = Difficulty.HARD,
        text = "A __ b",
        imageUrl = null,
        blanks = listOf(QuestionContent.Blank(BlankId("b1"), CandidateId("c1"))),
        candidates = listOf(
            QuestionContent.Candidate(CandidateId("c1"), "aaa"),
            QuestionContent.Candidate(CandidateId("c2"), "bbb"),
            QuestionContent.Candidate(CandidateId("c3"), "ccc"),
            QuestionContent.Candidate(CandidateId("c4"), "ddd"),
            QuestionContent.Candidate(CandidateId("c5"), "eee"),
        ),
    )

    private fun survey() = QuestionContent.Survey(
        id = "q5",
        difficulty = Difficulty.EASY,
        text = "Favourite?",
        imageUrl = null,
        options = listOf(
            QuestionContent.Option(OptionId("o1"), "Alpha"),
            QuestionContent.Option(OptionId("o2"), "Beta"),
        ),
    )

    private fun redactedSingleChoice(
        difficulty: String? = null,
        options: List<RedactedQuestionContent.Row> = listOf(
            RedactedQuestionContent.Row("r0", "Alpha"),
            RedactedQuestionContent.Row("r1", "Beta"),
        ),
    ) = RedactedQuestionContent.SingleChoice(
        id = "q1",
        difficulty = difficulty,
        text = "Q?",
        imageUrl = null,
        options = options,
    )

    private fun legacyPayload() =
        """{"type":"single-choice","options":["Alpha","Beta","Gamma"],"correctIndex":2}"""

    /** One payload per ordinary shape, written the way kotlinx encodes it. */
    private fun ordinaryPayloads(): List<Pair<String, String>> = listOf(
        "SingleChoice" to """{"type":"SingleChoice","id":"q1","difficulty":"EASY","text":"Q?",""" +
            """"imageUrl":null,"options":[{"id":"o1","text":"Alpha"},{"id":"o2","text":"Beta"}],""" +
            """"correctOptionId":"o1"}""",
        "MultipleChoice" to """{"type":"MultipleChoice","id":"q2","difficulty":"HARD","text":"Pick two",""" +
            """"imageUrl":null,"options":[{"id":"o1","text":"Alpha"},{"id":"o2","text":"Beta"},""" +
            """{"id":"o3","text":"Gamma"}],"correctOptionIds":["o1","o2"]}""",
        "Ordering" to """{"type":"Ordering","id":"q3","difficulty":"EASY","text":"Order these",""" +
            """"imageUrl":null,"items":[{"id":"i1","text":"First"},{"id":"i2","text":"Second"}]}""",
        "FillBlank" to """{"type":"FillBlank","id":"q4","difficulty":"HARD","text":"A __ b","imageUrl":null,""" +
            """"blanks":[{"id":"b1","correctCandidateId":"c1"}],"candidates":[{"id":"c1","text":"aaa"},""" +
            """{"id":"c2","text":"bbb"},{"id":"c3","text":"ccc"},{"id":"c4","text":"ddd"},""" +
            """{"id":"c5","text":"eee"}]}""",
        "Survey" to """{"type":"Survey","id":"q5","difficulty":"EASY","text":"Favourite?","imageUrl":null,""" +
            """"options":[{"id":"o1","text":"Alpha"},{"id":"o2","text":"Beta"}]}""",
    )

    /**
     * One payload per redacted shape, keyed by the discriminator it wears so that
     * `the redacted payloads cover every variant the hierarchy declares` can check the list against
     * the serializer. Third element is the Kotlin class the payload must decode into.
     */
    private fun redactedPayloads(): List<Triple<String, String, String>> = listOf(
        Triple(
            "SingleChoiceRedacted",
            """{"type":"SingleChoiceRedacted","id":"q1","difficulty":"EASY","text":"Q?","imageUrl":null,""" +
                """"options":[{"id":"ro-0","text":"Alpha"},{"id":"ro-1","text":"Beta"}]}""",
            "SingleChoice",
        ),
        Triple(
            "MultipleChoiceRedacted",
            """{"type":"MultipleChoiceRedacted","id":"q2","difficulty":"HARD","text":"Pick two",""" +
                """"imageUrl":"https://example.test/a.png","options":[{"id":"ro-0","text":"Alpha"},""" +
                """{"id":"ro-1","text":"Beta"},{"id":"ro-2","text":"Gamma"}]}""",
            "MultipleChoice",
        ),
        Triple(
            "OrderingRedacted",
            """{"type":"OrderingRedacted","difficulty":"EASY","text":"Order these","imageUrl":null,""" +
                """"items":[{"id":"ri-0","text":"Second"},{"id":"ri-1","text":"First"}]}""",
            "Ordering",
        ),
        Triple(
            "FillBlankRedacted",
            """{"type":"FillBlankRedacted","id":"q4","text":"A __ b","imageUrl":null,"blanks":["rb-0"],""" +
                """"candidates":[{"id":"rc-0","text":"aaa"},{"id":"rc-1","text":"bbb"}]}""",
            "FillBlank",
        ),
    )
}
