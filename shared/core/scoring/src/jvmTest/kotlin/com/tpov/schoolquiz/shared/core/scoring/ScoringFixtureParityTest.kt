package com.tpov.schoolquiz.shared.core.scoring

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Kotlin half of the two-language parity harness.
 *
 * The server carries a second implementation of this arithmetic in JavaScript
 * (functions/assessment-scoring.js). Two implementations of a formula built on Int division are
 * two sets of rounding decisions, and the drift is silent — a digit off by one still looks like a
 * plausible score. So both sides read the same fixture file and neither owns the expectations.
 *
 * This test lives in jvmTest rather than commonTest because commonTest cannot read a file: there
 * is no okio or kotlinx-io in the version catalog. The fixtures sit in this module's jvmTest
 * resources, which puts them on the test classpath — no working-directory assumption — and makes
 * Gradle track them as an input to :shared:core:scoring:jvmTest.
 *
 * A missing fixture file is a failure, not a skip. A parity harness that quietly stops comparing
 * is worse than no harness, because the green tick still claims the two agree.
 *
 * Each case is decoded inside its own loop iteration rather than with the file. A fixture that
 * violates a QuestionContent init block is a mistake someone will make while adding a case, and
 * decoding the whole file up front turns that into a bare decoder message with no case name
 * attached, on all four tests at once. Decoded per case, it names the case and the rest still run.
 *
 * Run with: ./gradlew :shared:core:scoring:jvmTest --no-configuration-cache
 */
class ScoringFixtureParityTest {

    private val fixtures: ScoringFixtures = loadFixtures()

    @Test
    fun `the fixture file carries cases for all three scoring functions`() {
        assertTrue(
            fixtures.evaluateAnswer.isNotEmpty(),
            "Fixture array \"evaluateAnswer\" is empty — nothing would be compared",
        )
        assertTrue(
            fixtures.computePercentScore.isNotEmpty(),
            "Fixture array \"computePercentScore\" is empty — nothing would be compared",
        )
        assertTrue(
            fixtures.computeStars.isNotEmpty(),
            "Fixture array \"computeStars\" is empty — nothing would be compared",
        )
    }

    @Test
    fun `every evaluateAnswer fixture case matches the Kotlin scorer`() {
        val failures = mutableListOf<String>()
        for (raw in fixtures.evaluateAnswer) {
            val name = raw.caseName()
            val case = raw.decodeOrRecord(EvaluateAnswerCase.serializer(), name, failures) ?: continue
            val actual = evaluateAnswer(case.content, case.answer).raw
            if (actual != case.expected) {
                failures += "  \"$name\": expected ${case.expected}, got $actual"
            }
        }
        report("evaluateAnswer", fixtures.evaluateAnswer.size, failures)
    }

    @Test
    fun `every computePercentScore fixture case matches the Kotlin scorer`() {
        val failures = mutableListOf<String>()
        for (raw in fixtures.computePercentScore) {
            val name = raw.caseName()
            val case = raw.decodeOrRecord(PercentScoreCase.serializer(), name, failures) ?: continue
            val codeAnswer = try {
                CodeAnswer(case.codeAnswer)
            } catch (invariant: IllegalArgumentException) {
                // Expected only for a case flagged as unrepresentable in Kotlin. Any other
                // rejection is a finding about this fixture, so it is recorded with the case name
                // rather than thrown out of the loop, where it would hide every case after it.
                if (!case.kotlinRejectsCodeAnswer) {
                    failures += "  \"$name\": CodeAnswer(\"${case.codeAnswer}\") was rejected by " +
                        "its invariant — ${invariant.message}"
                }
                continue
            }
            if (case.kotlinRejectsCodeAnswer) {
                failures += "  \"$name\": CodeAnswer(\"${case.codeAnswer}\") was expected to be " +
                    "rejected by its invariant, but it was accepted"
                continue
            }
            val actual = computePercentScore(codeAnswer).raw
            if (actual != case.expected) {
                failures += "  \"$name\": codeAnswer \"${case.codeAnswer}\" " +
                    "expected ${case.expected}, got $actual"
            }
        }
        report("computePercentScore", fixtures.computePercentScore.size, failures)
    }

    @Test
    fun `every computeStars fixture case matches the Kotlin scorer`() {
        val failures = mutableListOf<String>()
        for (raw in fixtures.computeStars) {
            val name = raw.caseName()
            val case = raw.decodeOrRecord(StarsCase.serializer(), name, failures) ?: continue
            val percentScore = try {
                PercentScore(case.percentScore)
            } catch (invariant: IllegalArgumentException) {
                failures += "  \"$name\": PercentScore(${case.percentScore}) was rejected by its " +
                    "invariant — ${invariant.message}"
                continue
            }
            val actual = computeStars(percentScore, case.mode).rawTenths
            if (actual != case.expected) {
                failures += "  \"$name\": ${case.percentScore} percent on ${case.mode} " +
                    "expected ${case.expected}, got $actual"
            }
        }
        report("computeStars", fixtures.computeStars.size, failures)
    }

    /** Reports every mismatch at once: a changed constant drifts families of cases, not one. */
    private fun report(function: String, total: Int, failures: List<String>) {
        assertTrue(
            failures.isEmpty(),
            "$function: ${failures.size} of $total cases failed against $FIXTURE_RESOURCE\n" +
                failures.joinToString("\n"),
        )
    }

    private fun JsonObject.caseName(): String =
        (this["name"] as? JsonPrimitive)?.contentOrNull ?: "<unnamed case>"

    private fun <T> JsonObject.decodeOrRecord(
        deserializer: DeserializationStrategy<T>,
        name: String,
        failures: MutableList<String>,
    ): T? = try {
        json.decodeFromJsonElement(deserializer, this)
    } catch (malformed: SerializationException) {
        failures += "  \"$name\": could not be decoded — ${malformed.message}"
        null
    } catch (invariant: IllegalArgumentException) {
        failures += "  \"$name\": rejected by a QuestionContent or UserAnswer invariant — " +
            "${invariant.message}"
        null
    }

    private fun loadFixtures(): ScoringFixtures {
        val stream = ScoringFixtureParityTest::class.java.getResourceAsStream(FIXTURE_RESOURCE)
            ?: fail(
                "Scoring fixtures missing from the test classpath at $FIXTURE_RESOURCE. They are " +
                    "the only thing pinning this scorer to functions/assessment-scoring.js; " +
                    "without them there is nothing to compare.",
            )
        val payload = stream.use { it.readBytes().decodeToString() }
        return json.decodeFromString(ScoringFixtures.serializer(), payload)
    }

    private companion object {
        const val FIXTURE_RESOURCE = "/scoring-fixtures.json"

        // Deliberately strict: an unknown key is a typo in a hand-written fixture, and a typo that
        // decodes to a default is a case that silently stops testing what it was written for.
        val json = Json
    }
}

/**
 * The fixture file's shape. Cases stay as raw JSON objects here and are decoded one at a time, so
 * that a case rejected by an init block can be named. Adding a case needs no change to this file
 * or to functions/assessment-scoring.test.js.
 */
@Serializable
private data class ScoringFixtures(
    /** Human-facing notes for whoever edits the JSON; not used by either suite. */
    @SerialName("_doc") val doc: String = "",
    val evaluateAnswer: List<JsonObject>,
    val computePercentScore: List<JsonObject>,
    val computeStars: List<JsonObject>,
)

@Serializable
private data class EvaluateAnswerCase(
    val name: String,
    val content: QuestionContent,
    val answer: UserAnswer,
    /** The score digit, 1..9. */
    val expected: Int,
)

@Serializable
private data class PercentScoreCase(
    val name: String,
    val codeAnswer: String,
    /** The percent, 0..100 — what the JavaScript scorer must return for [codeAnswer]. */
    val expected: Int,
    /**
     * True when the CodeAnswer value class rejects [codeAnswer] outright, so Kotlin can never
     * reach the formula for it. Those cases assert the rejection instead of [expected].
     */
    val kotlinRejectsCodeAnswer: Boolean = false,
)

@Serializable
private data class StarsCase(
    val name: String,
    val percentScore: Int,
    val mode: Difficulty,
    /** Star tenths, 0..30. */
    val expected: Int,
)
