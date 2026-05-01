package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase-01 tests: Group A (parser round-trip) + Group B (Difficulty @Serializable).
 *
 * JSON wire format matches kotlinx.serialization default field names
 * from QuestionContent domain model: "text", "imageUrl", "id", "difficulty".
 */
class QuestionContentParserTest {

    private val parser = KotlinxSerializationQuestionContentParser()

    // ── Group A: Parser round-trip ────────────────────────────────────────────

    @Test
    fun `given singleChoice json when parse then returns success with options and correctOptionId`() {
        // Spec scenario A-01: parser_roundtrip_singleChoice
        val json = """{"type":"SingleChoice","id":"q1","difficulty":"EASY","text":"Q?","imageUrl":null,"options":[{"id":"1","text":"A"},{"id":"2","text":"B"}],"correctOptionId":"1"}"""

        val result = parser.parse(json)

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
        val content = assertIs<QuestionContent.SingleChoice>(result.getOrThrow())
        assertEquals(2, content.options.size)
        assertEquals(OptionId("1"), content.correctOptionId)
    }

    @Test
    fun `given multipleChoice json when parse then returns success with correctOptionIds`() {
        // Spec scenario A-02: parser_roundtrip_multipleChoice
        val json = """{"type":"MultipleChoice","id":"q1","difficulty":"EASY","text":"Q?","imageUrl":null,"options":[{"id":"1","text":"A"},{"id":"2","text":"B"},{"id":"3","text":"C"}],"correctOptionIds":["1","2"]}"""

        val result = parser.parse(json)

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
        val content = assertIs<QuestionContent.MultipleChoice>(result.getOrThrow())
        assertEquals(setOf(OptionId("1"), OptionId("2")), content.correctOptionIds)
    }

    @Test
    fun `given ordering json when parse then returns success with items order preserved`() {
        // Spec scenario A-03: parser_roundtrip_ordering
        val json = """{"type":"Ordering","id":"q1","difficulty":"EASY","text":"Order these","imageUrl":null,"items":[{"id":"A","text":"First"},{"id":"B","text":"Second"},{"id":"C","text":"Third"}]}"""

        val result = parser.parse(json)

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
        val content = assertIs<QuestionContent.Ordering>(result.getOrThrow())
        assertEquals(3, content.items.size)
        assertEquals(OptionId("A"), content.items[0].id)
        assertEquals(OptionId("B"), content.items[1].id)
        assertEquals(OptionId("C"), content.items[2].id)
    }

    @Test
    fun `given fillBlank json when parse then returns success with blank and candidate counts`() {
        // Spec scenario A-04: parser_roundtrip_fillBlank
        val json = """{"type":"FillBlank","id":"q1","difficulty":"EASY","text":"Fill ___","imageUrl":null,"blanks":[{"id":"b1","correctCandidateId":"c1"}],"candidates":[{"id":"c1","text":"w1"},{"id":"c2","text":"w2"},{"id":"c3","text":"w3"},{"id":"c4","text":"w4"},{"id":"c5","text":"w5"}]}"""

        val result = parser.parse(json)

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
        val content = assertIs<QuestionContent.FillBlank>(result.getOrThrow())
        assertEquals(1, content.blanks.size)
        assertEquals(5, content.candidates.size)
    }

    @Test
    fun `given fillBlank protected text segments when parse then preserves segments`() {
        val json = """{"type":"FillBlank","id":"q1","difficulty":"EASY","text":"Fill ___","imageUrl":null,"blanks":[{"id":"b1","correctCandidateId":"c1"}],"candidates":[{"id":"c1","text":"Kotlin"},{"id":"c2","text":"Java"},{"id":"c3","text":"Swift"},{"id":"c4","text":"Go"},{"id":"c5","text":"Scala"}],"protectedTextSegments":["Kotlin"]}"""

        val result = parser.parse(json)

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
        val content = assertIs<QuestionContent.FillBlank>(result.getOrThrow())
        assertEquals(listOf("Kotlin"), content.protectedTextSegments)
    }

    @Test
    fun `given unknown type json when parse then returns failure without crash`() {
        // Spec scenario A-05: parser_unknownType_returnsFailure
        val json = """{"type":"VideoQuestion","foo":"bar"}"""

        val result = parser.parse(json)

        assertTrue(result.isFailure, "Expected failure for unknown question type")
    }

    @Test
    fun `given empty string when parse then returns failure`() {
        // Spec scenario A-06: parser_emptyString_returnsFailure
        val result = parser.parse("")

        assertTrue(result.isFailure, "Expected failure for empty payload")
    }

    @Test
    fun `given malformed json string when parse then returns failure`() {
        // Spec scenario A-07: parser_malformedJson_returnsFailure
        val result = parser.parse("not-json")

        assertTrue(result.isFailure, "Expected failure for malformed JSON")
    }

    @Test
    fun `given singleChoice json with unknown extra field when parse then succeeds`() {
        // Spec scenario A-08: parser_ignoresUnknownKeys — verifies ignoreUnknownKeys = true
        val json = """{"type":"SingleChoice","id":"q1","difficulty":"EASY","text":"Q?","imageUrl":null,"options":[{"id":"1","text":"A"},{"id":"2","text":"B"}],"correctOptionId":"1","extraField":"ignored"}"""

        val result = parser.parse(json)

        assertTrue(result.isSuccess, "Parser must ignore unknown keys; got: ${result.exceptionOrNull()}")
    }

    @Test
    fun `given singleChoice json with info when parse then info is preserved`() {
        val json = """{"type":"SingleChoice","id":"q1","difficulty":"EASY","text":"Q?","imageUrl":null,"options":[{"id":"1","text":"A"},{"id":"2","text":"B"}],"correctOptionId":"1","info":"Почему ответ именно A"}"""

        val result = parser.parse(json)

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
        val content = assertIs<QuestionContent.SingleChoice>(result.getOrThrow())
        assertEquals("Почему ответ именно A", content.info)
    }

    // ── Group B: Difficulty @Serializable round-trip ──────────────────────────

    @Test
    fun `given Difficulty EASY when json roundtrip then decoded value equals EASY`() {
        // Spec scenario B-01: difficulty_easy_serializable_roundtrip
        val encoded = Json.encodeToString(Difficulty.serializer(), Difficulty.EASY)
        val decoded = Json.decodeFromString(Difficulty.serializer(), encoded)

        assertEquals(Difficulty.EASY, decoded)
    }

    @Test
    fun `given Difficulty HARD when json roundtrip then decoded value equals HARD`() {
        // Spec scenario B-02: difficulty_hard_serializable_roundtrip
        val encoded = Json.encodeToString(Difficulty.serializer(), Difficulty.HARD)
        val decoded = Json.decodeFromString(Difficulty.serializer(), encoded)

        assertEquals(Difficulty.HARD, decoded)
    }

    // ── Group C: Legacy format fallback ───────────────────────────────────────

    @Test
    fun `parse_legacy_singleChoice_returns_synthesized_options`() {
        val payload = """{"type":"single-choice","options":["A","B","C"],"correctIndex":0}"""

        val result = parser.parse(payload, "q1", "Question?", Difficulty.EASY)

        assertTrue(result.isSuccess, "Expected success for legacy single-choice: ${result.exceptionOrNull()}")
        val content = assertIs<QuestionContent.SingleChoice>(result.getOrThrow())
        assertEquals(3, content.options.size)
        assertEquals(OptionId("opt-0"), content.options[0].id)
        assertEquals("A", content.options[0].text)
        assertEquals(OptionId("opt-1"), content.options[1].id)
        assertEquals(OptionId("opt-2"), content.options[2].id)
    }

    @Test
    fun `parse_legacy_singleChoice_correctIndex_2_maps_to_opt-2`() {
        val payload = """{"type":"single-choice","options":["val — изменяемая","var — неизменяемая","val — read-only, var — mutable","Никакой"],"correctIndex":2}"""

        val result = parser.parse(payload, "q42", "Чем отличаются val и var?", Difficulty.EASY)

        assertTrue(result.isSuccess, "Expected success: ${result.exceptionOrNull()}")
        val content = assertIs<QuestionContent.SingleChoice>(result.getOrThrow())
        assertEquals(OptionId("opt-2"), content.correctOptionId)
        assertEquals(4, content.options.size)
    }

    @Test
    fun `parse_legacy_uses_fallbackId_when_missing`() {
        val payload = """{"type":"single-choice","options":["X","Y"],"correctIndex":1}"""

        val result = parser.parse(payload, "my-fallback-id", "Q?", Difficulty.EASY)

        val content = assertIs<QuestionContent.SingleChoice>(result.getOrThrow())
        assertEquals("my-fallback-id", content.id)
    }

    @Test
    fun `parse_legacy_uses_fallbackText_when_missing`() {
        val payload = """{"type":"single-choice","options":["X","Y"],"correctIndex":0}"""

        val result = parser.parse(payload, "id1", "My fallback text", Difficulty.EASY)

        val content = assertIs<QuestionContent.SingleChoice>(result.getOrThrow())
        assertEquals("My fallback text", content.text)
    }

    @Test
    fun `parse_legacy_uses_fallbackDifficulty_when_missing`() {
        val payload = """{"type":"single-choice","options":["X","Y"],"correctIndex":0}"""

        val result = parser.parse(payload, "id1", "Q?", Difficulty.HARD)

        val content = assertIs<QuestionContent.SingleChoice>(result.getOrThrow())
        assertEquals(Difficulty.HARD, content.difficulty)
    }
}
