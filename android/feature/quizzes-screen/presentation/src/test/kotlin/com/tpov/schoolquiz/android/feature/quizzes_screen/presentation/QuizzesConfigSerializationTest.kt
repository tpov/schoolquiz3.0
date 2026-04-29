package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation

import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * JVM unit tests for [QuizzesConfig] serialization round-trip.
 *
 * Spec: docs/features/quizzes-screen/0-spec.md — AC#21 (process death restoration)
 * Design: docs/features/quizzes-screen/04-testing.md §8
 * Phase: 03
 *
 * SER-01..11 — round-trip, stack serialization, Cyrillic, corrupted payloads.
 */
class QuizzesConfigSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun <T : QuizzesConfig> roundTrip(original: T): QuizzesConfig {
        val encoded = json.encodeToString(QuizzesConfig.serializer(), original)
        return json.decodeFromString(QuizzesConfig.serializer(), encoded)
    }

    private fun stackRoundTrip(stack: List<QuizzesConfig>): List<QuizzesConfig> {
        val serializer = ListSerializer(QuizzesConfig.serializer())
        val encoded = json.encodeToString(serializer, stack)
        return json.decodeFromString(serializer, encoded)
    }

    // ── SER-01 — QuizzesConfig.Idle ───────────────────────────────────────────

    /**
     * Spec: SER-01 — data object Idle round-trips without fields.
     * ADR-QS-02: QuizzesConfig is @Serializable sealed class; Idle has no fields.
     */
    @Test
    fun `QuizzesConfig Idle round-trip`() {
        val original = QuizzesConfig.Idle
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
        assertIs<QuizzesConfig.Idle>(decoded)
    }

    // ── SER-02 — QuizzesConfig.QuestList ─────────────────────────────────────

    /**
     * Spec: SER-02 — QuestList(catalogId, titles) preserves all fields.
     * titles[0] = catalogName (no separate catalogName field per §10).
     */
    @Test
    fun `QuizzesConfig QuestList round-trip`() {
        val original = QuizzesConfig.QuestList(
            catalogId = "cat-1",
            titles = listOf("Математика"),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
    }

    // ── SER-03 — QuizzesConfig.SectionList ───────────────────────────────────

    /**
     * Spec: SER-03 — SectionList(questId, titles) with 2-element titles list.
     */
    @Test
    fun `QuizzesConfig SectionList round-trip`() {
        val original = QuizzesConfig.SectionList(
            questId = "q-1",
            titles = listOf("Математика", "Квест 1"),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
    }

    // ── SER-04 — QuizzesConfig.ThemeList ─────────────────────────────────────

    /**
     * Spec: SER-04 — ThemeList(sectionId, titles) with 3-element titles list.
     */
    @Test
    fun `QuizzesConfig ThemeList round-trip`() {
        val original = QuizzesConfig.ThemeList(
            sectionId = "s-1",
            titles = listOf("Математика", "Квест 1", "Секция 1"),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
    }

    // ── SER-05 — QuizzesConfig.LessonList ────────────────────────────────────

    /**
     * Spec: SER-05 — LessonList(themeId, titles) with 4-element titles list.
     */
    @Test
    fun `QuizzesConfig LessonList round-trip`() {
        val original = QuizzesConfig.LessonList(
            themeId = "t-1",
            titles = listOf("Математика", "Квест 1", "Секция 1", "Тема 1"),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
    }

    // ── SER-06 — QuizzesConfig.LessonRunner ──────────────────────────────────

    /**
     * Spec: SER-06 — LessonRunner preserves lessonId, mode, and titles.
     * mode: Difficulty is @Serializable (Phase-01). lessonId is raw String (not LessonId).
     * Also covers: Ser-02 (tests.md §Ser-02) — LessonRunner EASY mode round-trip.
     */
    @Test
    fun `QuizzesConfig LessonRunner round-trip preserves lessonId and mode`() {
        val original = QuizzesConfig.LessonRunner(
            lessonId = "l-1",
            mode = com.tpov.schoolquiz.shared.core.question_schema.Difficulty.EASY,
            titles = listOf("Математика", "Квест 1", "Секция 1", "Тема 1", "Урок 1"),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
        val runner = assertIs<QuizzesConfig.LessonRunner>(decoded)
        assertEquals("l-1", runner.lessonId)
        assertEquals(com.tpov.schoolquiz.shared.core.question_schema.Difficulty.EASY, runner.mode)
    }

    // ── Ser-01 (Phase-06) — LessonRunner HARD round-trip ────────────────────────

    /**
     * Phase-06 Ser-01: LessonRunner(mode=HARD) preserves mode after round-trip.
     * Spec: docs/features/lesson-runner/plan/phase-06/tests.md §Ser-01
     */
    @Test
    fun `QuizzesConfig LessonRunner HARD mode round-trip`() {
        val original = QuizzesConfig.LessonRunner(
            lessonId = "l1",
            mode = com.tpov.schoolquiz.shared.core.question_schema.Difficulty.HARD,
            titles = listOf("Cat", "Quest", "Lesson"),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
        val runner = assertIs<QuizzesConfig.LessonRunner>(decoded)
        assertEquals(
            com.tpov.schoolquiz.shared.core.question_schema.Difficulty.HARD,
            runner.mode,
        )
    }

    // ── SER-07 — Stack round-trip ─────────────────────────────────────────────

    /**
     * Spec: SER-07 — full stack [Idle, QuestList, SectionList] serializes and
     * deserializes to structurally equal stack.
     * AC#21: StateKeeper uses ListSerializer(QuizzesConfig.serializer()).
     */
    @Test
    fun `stack Idle QuestList SectionList round-trip`() {
        val stack = listOf(
            QuizzesConfig.Idle,
            QuizzesConfig.QuestList("cat-1", listOf("Математика")),
            QuizzesConfig.SectionList("q-1", listOf("Математика", "Квест 1")),
        )
        val decoded = stackRoundTrip(stack)
        assertEquals(stack, decoded)
        assertEquals(3, decoded.size)
        assertIs<QuizzesConfig.Idle>(decoded[0])
        assertIs<QuizzesConfig.QuestList>(decoded[1])
        assertIs<QuizzesConfig.SectionList>(decoded[2])
    }

    // ── SER-08 — Cyrillic titles ──────────────────────────────────────────────

    /**
     * Spec: SER-08 — Cyrillic characters in titles survive JSON encoding.
     * Verifies no encoding corruption (e.g., incorrect charset or escaped chars).
     */
    @Test
    fun `titles with Cyrillic survive round-trip`() {
        val original = QuizzesConfig.SectionList(
            questId = "q-cyrillic",
            titles = listOf("Алгебра и начала анализа", "Производная функции"),
        )
        val decoded = roundTrip(original) as QuizzesConfig.SectionList
        assertEquals(original.titles, decoded.titles)
    }

    // ── SER-09 — Empty titles list ────────────────────────────────────────────

    /**
     * Spec: SER-09 — empty titles list survives round-trip without becoming null.
     */
    @Test
    fun `empty titles list survives round-trip`() {
        val original = QuizzesConfig.QuestList(
            catalogId = "cat-empty",
            titles = emptyList(),
        )
        val decoded = roundTrip(original) as QuizzesConfig.QuestList
        assertEquals(emptyList(), decoded.titles)
    }

    // ── SER-10 — Missing required field ──────────────────────────────────────

    /**
     * Spec: SER-10 — JSON with missing required field (catalogId) throws SerializationException.
     * The restore wrapper in DefaultQuizzesComponent catches this and returns [Idle].
     */
    @Test
    fun `missing required field throws SerializationException`() {
        val brokenJson = """{"type":"QuestList","titles":["Math"]}"""  // missing catalogId
        assertFailsWith<SerializationException> {
            json.decodeFromString(QuizzesConfig.serializer(), brokenJson)
        }
    }

    // ── SER-11 — Unknown discriminator ────────────────────────────────────────

    /**
     * Spec: SER-11 — JSON with unknown type discriminator throws SerializationException.
     * The restore wrapper in DefaultQuizzesComponent falls back to [Idle] list on any
     * SerializationException.
     */
    @Test
    fun `unknown discriminator throws SerializationException`() {
        val unknownJson = """{"type":"UnknownFutureScreen","id":"x-1"}"""
        assertFailsWith<SerializationException> {
            json.decodeFromString(QuizzesConfig.serializer(), unknownJson)
        }
    }

    // ── SER-10/11 — Restore wrapper behavior ─────────────────────────────────

    /**
     * Additional: restore wrapper pattern — SerializationException caught returns [Idle].
     * Simulates what DefaultQuizzesComponent.stateKeeper restore logic must do.
     * Spec: 04-testing.md §8.3 NOTE on runtime wrapper.
     */
    @Test
    fun `restore wrapper returns Idle list on corrupted stack JSON`() {
        val corruptedJson = """[{"type":"QuestList"},{"type":"UNKNOWN"}]"""
        val fallback = runCatching {
            json.decodeFromString(ListSerializer(QuizzesConfig.serializer()), corruptedJson)
        }.getOrElse { listOf(QuizzesConfig.Idle) }

        assertEquals(listOf(QuizzesConfig.Idle), fallback)
    }
}
