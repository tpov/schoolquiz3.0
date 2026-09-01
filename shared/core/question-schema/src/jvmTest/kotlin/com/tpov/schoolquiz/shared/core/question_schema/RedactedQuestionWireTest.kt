package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Kotlin half of the two-language harness pinning [RedactedQuestionContent] to
 * `functions/question-redaction.js`.
 *
 * The server decides what a published question looks like once its answer has been taken off, and
 * this module declares the same shape a second time in Kotlin. Two declarations of one wire format
 * drift silently — a renamed field or a dropped default still decodes into something plausible —
 * so neither side owns the expectations. Both read
 * `shared/core/question-schema/src/jvmTest/resources/redacted-question-fixtures.json`: JavaScript
 * asserts the emitter still produces each `publicPayload`, this file asserts Kotlin still reads
 * and writes it.
 *
 * jvmTest rather than commonTest because commonTest cannot read a file — there is no okio or
 * kotlinx-io in the version catalog. The fixtures sit in this module's jvmTest resources, which
 * puts them on the test classpath with no working-directory assumption and makes Gradle track them
 * as an input to `:shared:core:question-schema:jvmTest`. This mirrors
 * `ScoringFixtureParityTest`, which pins the scorer the same way.
 *
 * A missing fixture file is a failure, not a skip. A parity harness that quietly stops comparing
 * is worse than none, because the green tick still says the two agree.
 *
 * Run with: ./gradlew :shared:core:question-schema:allTests --no-configuration-cache
 */
class RedactedQuestionWireTest {

    private val fixtures: RedactedFixtures = loadFixtures()

    private val parser = KotlinxSerializationQuestionContentParser()

    @Test
    fun `the fixture file carries a case for every redacted shape the type declares`() {
        val declared = declaredVariants()
        val covered = fixtures.redacted.map { it.expectedType }.toSet()

        assertEquals(
            emptySet(),
            declared - covered,
            "RedactedQuestionContent variants with no fixture case — a shape nobody pinned is a " +
                "shape the emitter is free to get wrong. Declared: $declared, covered: $covered",
        )
        assertEquals(
            emptySet(),
            covered - declared,
            "Fixture cases naming a type this hierarchy does not declare. Declared: $declared, " +
                "covered: $covered",
        )
        assertTrue(
            fixtures.parseMustSucceed.isNotEmpty(),
            "Fixture array \"parseMustSucceed\" is empty — the regression half would test nothing",
        )
        assertTrue(
            fixtures.forbiddenKeys.isNotEmpty(),
            "Fixture array \"forbiddenKeys\" is empty — the leak check would check nothing",
        )
    }

    @Test
    fun `every emitted payload decodes into the matching redacted type`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.redacted) {
            val decoded = case.decodeOrRecord(failures) ?: continue
            val expected = kotlinTypeOf(case.expectedType)
            if (expected == null) {
                failures += "  \"${case.name}\": expectedType \"${case.expectedType}\" is not a " +
                    "RedactedQuestionContent variant"
                continue
            }
            if (decoded::class != expected) {
                failures += "  \"${case.name}\": expected ${expected.simpleName}, " +
                    "got ${decoded::class.simpleName}"
            }
        }
        report("decode", fixtures.redacted.size, failures)
    }

    @Test
    fun `every emitted payload re-encodes to the bytes it arrived as`() {
        val claimed = fixtures.redacted.filter { it.reEncodesExactly }
        assertTrue(
            claimed.isNotEmpty(),
            "No fixture case claims byte-identical re-encoding — nothing would be compared",
        )
        val failures = mutableListOf<String>()
        for (case in claimed) {
            val decoded = case.decodeOrRecord(failures) ?: continue
            val reEncoded = json.encodeToString(RedactedQuestionContent.serializer(), decoded)
            if (reEncoded != case.publicPayload) {
                failures += "  \"${case.name}\":\n    emitted: ${case.publicPayload}\n" +
                    "    Kotlin:  $reEncoded"
            }
        }
        report("re-encode", claimed.size, failures)
    }

    /**
     * The weaker claim, made for every case including the three the encoder normalises.
     *
     * Those three — no `protectedTextSegments`, no `id`, no `difficulty` — are exactly the shapes
     * the real seed corpus produces, so skipping them would leave the encoder untested on the only
     * data that actually exists. Encoding writes the omitted key back out explicitly, which is a
     * different byte string but must still mean the same thing.
     */
    @Test
    fun `every emitted payload survives a decode-encode-decode round trip`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.redacted) {
            val decoded = case.decodeOrRecord(failures) ?: continue
            val reEncoded = json.encodeToString(RedactedQuestionContent.serializer(), decoded)
            val reDecoded = try {
                json.decodeFromString(RedactedQuestionContent.serializer(), reEncoded)
            } catch (rejected: IllegalArgumentException) {
                failures += "  \"${case.name}\": Kotlin cannot read back what it wrote — " +
                    "${rejected.message}"
                continue
            }
            if (reDecoded != decoded) {
                failures += "  \"${case.name}\": round trip changed the value\n" +
                    "    before: $decoded\n    after:  $reDecoded"
            }
        }
        report("semantic round trip", fixtures.redacted.size, failures)
    }

    /**
     * A stale `reEncodesExactly: false` would silently downgrade a case that byte-identity does in
     * fact cover, so the flag has to earn itself: a case that claims normalisation must actually be
     * normalised.
     */
    @Test
    fun `a case claiming the encoder normalises it really is not byte-identical`() {
        val normalised = fixtures.redacted.filterNot { it.reEncodesExactly }
        assertTrue(
            normalised.isNotEmpty(),
            "No case exercises the normalising path, so the shapes the seed corpus actually " +
                "produces — no id, no difficulty, no protectedTextSegments — are untested",
        )
        val failures = mutableListOf<String>()
        for (case in normalised) {
            val decoded = case.decodeOrRecord(failures) ?: continue
            val reEncoded = json.encodeToString(RedactedQuestionContent.serializer(), decoded)
            if (reEncoded == case.publicPayload) {
                failures += "  \"${case.name}\": re-encodes byte-identically, so " +
                    "reEncodesExactly should be true"
            }
        }
        report("stale normalisation flag", normalised.size, failures)
    }

    /**
     * The one failure this whole type exists to prevent.
     *
     * Matched as *keys*, walked recursively, and never as substrings of the payload text: `order`
     * and `info` are ordinary English words that turn up in question prose, and a text scan cannot
     * tell a key from a value in any case. The list lives in the fixture so that this suite and the
     * JavaScript one cannot disagree about it.
     */
    @Test
    fun `no emitted payload carries an answer or an info key`() {
        val forbidden = fixtures.forbiddenKeys.toSet()
        val failures = mutableListOf<String>()
        for (case in fixtures.redacted) {
            val tree = try {
                json.parseToJsonElement(case.publicPayload)
            } catch (malformed: IllegalArgumentException) {
                failures += "  \"${case.name}\": publicPayload is not JSON — ${malformed.message}"
                continue
            }
            val leaked = forbiddenKeysIn(tree, forbidden)
            if (leaked.isNotEmpty()) {
                failures += "  \"${case.name}\": public half carries the key(s) $leaked"
            }
        }
        report("answer leak", fixtures.redacted.size, failures)
    }

    /**
     * A redacted payload must never become a [QuestionContent].
     *
     * `DefaultQuestCreateComponent` and `DefaultReviewQueueComponent` re-encode what they parse, so
     * a redacted value that arrived there as a QuestionContent would overwrite the stored question
     * with its own public half. Both overloads are checked: the enriched one is the path those
     * components actually take.
     */
    @Test
    fun `both parse overloads refuse every emitted payload`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.redacted) {
            val plain = parser.parse(case.publicPayload)
            if (plain.isSuccess) {
                failures += "  \"${case.name}\": parse() returned ${plain.getOrNull()}"
            }
            val enriched = parser.parse(case.publicPayload, "fallback-id", "Fallback?", Difficulty.EASY)
            if (enriched.isSuccess) {
                failures += "  \"${case.name}\": the enriched parse() returned ${enriched.getOrNull()}"
            }
        }
        report("parse refusal", fixtures.redacted.size, failures)
    }

    /**
     * The message has to say what actually happened.
     *
     * Before this slice the enriched overload discarded the accurate exception and fell through to
     * the legacy branch, which reported every redacted payload as `Unsupported legacy question
     * type: SingleChoiceRedacted` — naming a contract the payload has nothing to do with, and
     * sending whoever reads the log to the wrong file.
     */
    @Test
    fun `the enriched overload names the unregistered discriminator instead of blaming the legacy format`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.redacted) {
            val message = parser
                .parse(case.publicPayload, "fallback-id", "Fallback?", Difficulty.EASY)
                .exceptionOrNull()
                ?.message
                .orEmpty()
            if (!message.contains(case.expectedType)) {
                failures += "  \"${case.name}\": message does not name ${case.expectedType} — $message"
            }
            if (message.contains("legacy", ignoreCase = true)) {
                failures += "  \"${case.name}\": message blames the legacy format — $message"
            }
        }
        report("failure message", fixtures.redacted.size, failures)
    }

    /** The regression half: nothing this slice touched may change how a real payload is read. */
    @Test
    fun `every ordinary and legacy payload still parses exactly as before`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.parseMustSucceed) {
            val result = parser.parse(case.payload, "fallback-id", "Fallback?", Difficulty.EASY)
            val content = result.getOrNull()
            if (content == null) {
                failures += "  \"${case.name}\": ${result.exceptionOrNull()?.message}"
                continue
            }
            val actual = content::class.simpleName
            if (actual != case.expectedType) {
                failures += "  \"${case.name}\": expected ${case.expectedType}, got $actual"
            }
            // A legacy payload only ever parsed through the enriched overload; the plain one is
            // expected to refuse it, and asserting that keeps the two overloads from converging.
            val plain = parser.parse(case.payload)
            if (case.legacy && plain.isSuccess) {
                failures += "  \"${case.name}\": the plain parse() accepted a legacy payload"
            }
            if (!case.legacy && plain.isFailure) {
                failures += "  \"${case.name}\": the plain parse() refused an ordinary payload — " +
                    "${plain.exceptionOrNull()?.message}"
            }
        }
        report("regression", fixtures.parseMustSucceed.size, failures)
    }

    /**
     * The seed corpus keeps a question's id on the wrapper document, never inside `payload`
     * (`buildLesson` in `scripts/seed-bulk/data/courses/<course>/_helpers.js`), and the emitter
     * copies `id` across only when the source has one. A required `id` would therefore refuse the
     * public half of every seeded question — the whole corpus — so it has to decode to null.
     */
    @Test
    fun `a payload whose source carried no id decodes with a null id`() {
        val failures = mutableListOf<String>()
        val idless = mutableListOf<RedactedCase>()
        for (case in fixtures.redacted) {
            val decoded = case.decodeOrRecord(failures) ?: continue
            if (decoded.id == null) idless += case
        }
        report("id decode", fixtures.redacted.size, failures)

        assertTrue(
            idless.isNotEmpty(),
            "No fixture case has an id-less public half, so the shape the entire seed corpus " +
                "produces is untested. Cases present: ${fixtures.redacted.map { it.name }}",
        )
        for (case in idless) {
            assertTrue(
                !case.publicPayload.contains("\"id\":null"),
                "\"${case.name}\": the emitter omits an absent id, it does not write null",
            )
        }
    }

    /**
     * `difficulty` is a string, carried exactly as it arrived.
     *
     * `resolveDifficulty` copies a payload's own value verbatim on purpose — substituting anything
     * would move a question between the easy and hard pools and change its reward, its unlock price
     * and its client timer. So the emitter publishes `""` as `""` and `"MEDIUM"` as `"MEDIUM"`, and
     * omits the key when there was nothing to publish. A [Difficulty] field would refuse all three.
     */
    @Test
    fun `difficulty is carried verbatim and only EASY or HARD map to the enum`() {
        val failures = mutableListOf<String>()
        val seen = mutableSetOf<String?>()
        for (case in fixtures.redacted) {
            val decoded = case.decodeOrRecord(failures) ?: continue
            val raw = (json.parseToJsonElement(case.publicPayload).jsonObject["difficulty"]
                as? JsonPrimitive)?.contentOrNull
            seen += raw
            if (decoded.difficulty != raw) {
                failures += "  \"${case.name}\": wire said ${raw?.let { "\"$it\"" }}, " +
                    "decoded to ${decoded.difficulty?.let { "\"$it\"" }}"
            }
            val expectedEnum = when (raw) {
                "EASY" -> Difficulty.EASY
                "HARD" -> Difficulty.HARD
                else -> null
            }
            if (decoded.difficultyOrNull != expectedEnum) {
                failures += "  \"${case.name}\": difficultyOrNull for ${raw?.let { "\"$it\"" }} " +
                    "was ${decoded.difficultyOrNull}, expected $expectedEnum"
            }
        }
        report("difficulty", fixtures.redacted.size, failures)

        for (shape in listOf(null, "", "MEDIUM")) {
            assertTrue(
                shape in seen,
                "No fixture case has difficulty ${shape?.let { "\"$it\"" } ?: "absent"}, which " +
                    "question-redaction.js is written to emit. Shapes present: $seen",
            )
        }
    }

    /**
     * `info` is not a field on [RedactedQuestionContent] — the emitter builds the public half from
     * an allow-list and never copies it. A payload that somehow carried one must still decode
     * rather than throw, which is what the production parser's `ignoreUnknownKeys` buys; the value
     * is simply not kept.
     */
    @Test
    fun `a redacted payload carrying an info field still decodes and does not keep it`() {
        val case = fixtures.redacted.firstOrNull { it.expectedType == "SingleChoiceRedacted" }
            ?: fail(
                "No SingleChoiceRedacted case in the fixture. Cases present: " +
                    "${fixtures.redacted.map { it.expectedType }}",
            )
        val withInfo = case.publicPayload.replaceFirst(
            "\"text\":",
            "\"info\":\"why the answer is what it is\",\"text\":",
        )
        assertTrue(withInfo.contains("\"info\""), "the test payload was not modified")

        val lenient = Json { ignoreUnknownKeys = true }
        val decoded = lenient.decodeFromString(RedactedQuestionContent.serializer(), withInfo)

        assertEquals(
            json.decodeFromString(RedactedQuestionContent.serializer(), case.publicPayload),
            decoded,
            "info must not survive into the decoded value",
        )
    }

    /**
     * The display entry point reads what the emitter actually writes.
     *
     * The commonTest suite checks [QuestionContentParser.parseForDisplay] against payloads written
     * by hand; this checks it against the real corpus — including the three shapes only the emitter
     * produces (no `id`, no `difficulty`, no `protectedTextSegments`) and the Cyrillic-and-emoji
     * case. A hand-written payload cannot go stale against the server, and that is exactly why it
     * cannot stand in for this one.
     */
    @Test
    fun `the display entry point reads every emitted payload the old ones refuse`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.redacted) {
            val result = parser.parseForDisplay(case.publicPayload, "fallback-id", "Fallback?", Difficulty.EASY)
            val display = result.getOrNull()
            if (display == null) {
                failures += "  \"${case.name}\": ${result.exceptionOrNull()?.message}"
                continue
            }
            val expected = kotlinTypeOf(case.expectedType)
            if (display::class != expected) {
                failures += "  \"${case.name}\": expected ${expected?.simpleName}, got ${display::class.simpleName}"
            }
        }
        report("display parse", fixtures.redacted.size, failures)
    }

    /**
     * The fallbacks belong to the legacy branch and to nothing else.
     *
     * A redacted payload is not legacy, so the id and difficulty passed alongside it must not
     * colour what comes back — the emitter's verbatim `""`, `"MEDIUM"` or absent difficulty has to
     * survive, since substituting one would move a question between the easy and hard pools and
     * change its reward, its unlock price and its timer.
     */
    @Test
    fun `the display entry point applies no fallback to an emitted payload`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.redacted) {
            val decoded = case.decodeOrRecord(failures) ?: continue
            val viaEntryPoint = parser
                .parseForDisplay(case.publicPayload, "fallback-id", "Fallback?", Difficulty.HARD)
                .getOrNull()
            if (viaEntryPoint != decoded) {
                failures += "  \"${case.name}\": the entry point changed the value\n" +
                    "    decoder:     $decoded\n    entry point: $viaEntryPoint"
            }
        }
        report("fallback leak", fixtures.redacted.size, failures)
    }

    /**
     * What a question is worth, proven across the two languages rather than within one.
     *
     * `expectedCharsCount` is written into the fixture from the server's own `questionCharsCount`
     * (`functions/lesson-reward.js`), and `question-redaction-wire.test.js` asserts the server
     * still agrees with it. This asserts Kotlin does too. Until this existed, every "the two must
     * agree to the character" claim in this module was Kotlin checked against Kotlin, which is the
     * one comparison that cannot catch the client and the server drifting apart — and they had
     * drifted, over an empty-string `imageUrl`.
     */
    @Test
    fun `every emitted payload is worth what the server says it is worth`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.redacted) {
            val decoded = case.decodeOrRecord(failures) ?: continue
            if (decoded.charsCount != case.expectedCharsCount) {
                failures += "  \"${case.name}\": the fixture says ${case.expectedCharsCount}, " +
                    "charsCount says ${decoded.charsCount}"
            }
        }
        report("chars count", fixtures.redacted.size, failures)
    }

    @Test
    fun `every ordinary payload is worth what the server says it is worth`() {
        val claimed = fixtures.parseMustSucceed.filter { it.expectedCharsCount != null }
        assertTrue(claimed.isNotEmpty(), "No ordinary case carries an expectedCharsCount")
        val failures = mutableListOf<String>()
        for (case in claimed) {
            val result = parser.parse(case.payload, "fallback-id", "Fallback?", Difficulty.EASY)
            val content = result.getOrNull()
            if (content == null) {
                failures += "  \"${case.name}\": ${result.exceptionOrNull()?.message}"
                continue
            }
            if (content.charsCount != case.expectedCharsCount) {
                failures += "  \"${case.name}\": the fixture says ${case.expectedCharsCount}, " +
                    "charsCount says ${content.charsCount}"
            }
        }
        report("chars count", claimed.size, failures)
    }

    /**
     * A case with no `expectedCharsCount` is a case nothing prices, so the omission has to be
     * earned. The legacy payload is the one shape whose count is not a property of the payload
     * alone — its `options` are bare strings with no `text`, so the server reads it as 0 while the
     * parser synthesises texts from the fallbacks it is handed. The JavaScript suite asserts the
     * same thing from its side.
     */
    @Test
    fun `only the legacy cases may omit an expected chars count`() {
        val all = fixtures.redacted.map { it.name to it.expectedCharsCount } +
            fixtures.parseMustSucceed.map { it.name to it.expectedCharsCount }
        val missing = all.filter { it.second == null }.map { it.first }.sorted()
        val legacy = fixtures.parseMustSucceed.filter { it.legacy }.map { it.name }.sorted()

        assertTrue(legacy.isNotEmpty(), "No legacy case in the fixture — the exemption is untested")
        assertEquals(
            legacy,
            missing,
            "The cases with no expectedCharsCount must be exactly the legacy ones",
        )
    }

    /**
     * The disjointness the whole dispatch rests on, checked in the direction nothing checked
     * before.
     *
     * `both parse overloads refuse every emitted payload` pins one side: no redacted payload
     * becomes a [QuestionContent]. This pins the other: no ordinary or legacy payload becomes a
     * [RedactedQuestionContent]. Without it, a redacted variant given a colliding `@SerialName`
     * would route an ordinary question into the redacted hierarchy and lose its answer, and every
     * existing test would stay green.
     */
    @Test
    fun `the redacted decoder refuses every ordinary and legacy payload`() {
        val failures = mutableListOf<String>()
        for (case in fixtures.parseMustSucceed) {
            val decoded = try {
                json.decodeFromString(RedactedQuestionContent.serializer(), case.payload)
            } catch (refused: IllegalArgumentException) {
                continue
            }
            failures += "  \"${case.name}\": decoded into a redacted ${decoded::class.simpleName}"
        }
        report("redacted refusal", fixtures.parseMustSucceed.size, failures)
    }

    /** Reports every mismatch at once: a changed field drifts families of cases, not one. */
    private fun report(stage: String, total: Int, failures: List<String>) {
        assertTrue(
            failures.isEmpty(),
            "$stage: ${failures.size} of $total cases failed against $FIXTURE_RESOURCE\n" +
                failures.joinToString("\n"),
        )
    }

    /**
     * Decoded per case rather than once for the file, so a fixture the decoder rejects names the
     * case that broke and leaves the rest of the run intact.
     */
    private fun RedactedCase.decodeOrRecord(failures: MutableList<String>): RedactedQuestionContent? =
        try {
            json.decodeFromString(RedactedQuestionContent.serializer(), publicPayload)
        } catch (rejected: IllegalArgumentException) {
            // SerializationException extends IllegalArgumentException, so this covers both an
            // unknown key — the decoder is deliberately strict, since a field the emitter writes
            // and Kotlin does not declare is the drift this file exists to catch — and a value
            // outside a declared type.
            failures += "  \"$name\": ${rejected.message}"
            null
        }

    /** Every *key* in the tree whose name is forbidden. Keys only, at any depth. */
    private fun forbiddenKeysIn(element: JsonElement, forbidden: Set<String>): List<String> =
        when (element) {
            is JsonObject -> element.keys.filter { it in forbidden } +
                element.values.flatMap { forbiddenKeysIn(it, forbidden) }
            is JsonArray -> element.flatMap { forbiddenKeysIn(it, forbidden) }
            else -> emptyList()
        }

    /**
     * The variants the sealed hierarchy actually declares, by their `@SerialName`.
     *
     * Derived rather than listed: a hand-kept copy of the four names is a fifth variant waiting to
     * be added without a fixture and tested by nothing. A sealed serializer's descriptor is
     * `["type": String, "value": <contextual>]`, and the contextual element's element names are the
     * subclasses' serial names.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun declaredVariants(): Set<String> {
        val variants = RedactedQuestionContent.serializer().descriptor.getElementDescriptor(1)
        return (0 until variants.elementsCount).map { variants.getElementName(it) }.toSet()
    }

    private fun loadFixtures(): RedactedFixtures {
        val stream = RedactedQuestionWireTest::class.java.getResourceAsStream(FIXTURE_RESOURCE)
            ?: fail(
                "Redacted question fixtures missing from the test classpath at $FIXTURE_RESOURCE. " +
                    "They are the only thing pinning RedactedQuestionContent to " +
                    "functions/question-redaction.js; without them there is nothing to compare.",
            )
        val payload = stream.use { it.readBytes().decodeToString() }
        return try {
            envelope.decodeFromString(RedactedFixtures.serializer(), payload)
        } catch (malformed: IllegalArgumentException) {
            fail("$FIXTURE_RESOURCE could not be read as a fixture file: ${malformed.message}")
        }
    }

    private fun kotlinTypeOf(discriminator: String) = when (discriminator) {
        "SingleChoiceRedacted" -> RedactedQuestionContent.SingleChoice::class
        "MultipleChoiceRedacted" -> RedactedQuestionContent.MultipleChoice::class
        "OrderingRedacted" -> RedactedQuestionContent.Ordering::class
        "FillBlankRedacted" -> RedactedQuestionContent.FillBlank::class
        else -> null
    }

    private companion object {
        const val FIXTURE_RESOURCE = "/redacted-question-fixtures.json"

        /**
         * Strict on purpose. A key the emitter writes that Kotlin does not declare is precisely the
         * drift this harness exists to catch, so it must fail rather than be ignored — the opposite
         * of the production parser, which reads payloads from an older corpus.
         *
         * `encodeDefaults` is on because the emitter writes `protectedTextSegments` whenever the
         * source carried the field, empty list included; without it an emitted `[]` would silently
         * disappear on the way back out.
         */
        val json = Json { encodeDefaults = true }

        /** The envelope carries the emitter's inputs too; those are for the JavaScript half. */
        val envelope = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class RedactedFixtures(
    /** Human-facing notes for whoever edits the JSON; not used by either suite. */
    @SerialName("_doc") val doc: String = "",
    /** Key names no public half may carry. Shared with the JavaScript suite. */
    val forbiddenKeys: List<String>,
    val redacted: List<RedactedCase>,
    val parseMustSucceed: List<ParseCase>,
)

@Serializable
private data class RedactedCase(
    val name: String,
    /** The wire discriminator, e.g. `"OrderingRedacted"`. */
    val expectedType: String,
    /**
     * What the question is worth to the timer, written from the server's own `questionCharsCount`.
     * Never absent on a redacted case; nullable only because one shape in the sibling array is.
     */
    val expectedCharsCount: Int? = null,
    /** Exactly what `redact` wrote, as a JSON string. */
    val publicPayload: String,
    /**
     * False where the emitter omits a key whose Kotlin field has a default, so encoding writes it
     * back out explicitly. Such a case claims only the semantic round trip, but it still has to
     * decode, still has to carry no answer, and still has to genuinely differ — a stale flag is
     * caught by its own test.
     */
    val reEncodesExactly: Boolean = true,
)

@Serializable
private data class ParseCase(
    val name: String,
    val payload: String,
    /** The [QuestionContent] subclass this must still produce. */
    val expectedType: String,
    /**
     * What the payload is worth, from the server's `questionCharsCount`. Absent exactly on the
     * legacy cases, whose count depends on the fallbacks the parser is handed rather than on the
     * payload — asserted by its own test, so the absence cannot spread.
     */
    val expectedCharsCount: Int? = null,
    /** True for a pre-ADR-0003 payload, which only the enriched overload can read. */
    val legacy: Boolean = false,
)
