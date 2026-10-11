package com.tpov.schoolquiz.shared.feature.internet.social.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Строгий декодер `poll-views-v1` (UX-O7 §5): «скрыто» (null) и «нулевой счёт» — разные
 * состояния; чужая версия конверта и кривые поля — отказ чтения, а не нули на экране (AD-8).
 */
class PollWireParserTest {
    private val summary =
        mapOf(
            "seriesId" to "poll-77",
            "title" to "Demo poll",
            "revisionId" to 2L,
            "questionCount" to 3,
            "answeredCount" to 1,
            "own" to false,
        )

    @Test
    fun `series list decodes with own progress`() {
        val page =
            mapOf(
                "viewVersion" to "poll-views-v1",
                "entries" to listOf(summary),
            )

        val entries = PollWireParser.seriesList(page)

        assertEquals(1, entries.size)
        assertEquals("poll-77", entries[0].seriesId)
        assertEquals(2L, entries[0].revisionId)
        assertEquals(3, entries[0].questionCount)
        assertEquals(1, entries[0].answeredCount)
        assertEquals(false, entries[0].own)
    }

    @Test
    fun `foreign or missing view version refuses`() {
        val foreign = mapOf("viewVersion" to "poll-views-v2", "entries" to listOf<Any?>())
        assertFailsWith<IllegalStateException> { PollWireParser.seriesList(foreign) }
        assertFailsWith<IllegalStateException> { PollWireParser.seriesList(mapOf<String, Any?>()) }
    }

    @Test
    fun `question state keeps hidden and zero distinct`() {
        val hidden =
            mapOf<String, Any?>(
                "viewVersion" to "poll-views-v1",
                "seriesId" to "poll-77",
                "title" to "Demo poll",
                "own" to false,
                "revisionId" to 2L,
                "questionCount" to 2,
                "questionIndex" to 0,
                "question" to
                    mapOf(
                        "questionIndex" to 0,
                        "text" to "Favorite color?",
                        "mode" to "SINGLE",
                        "options" to
                            listOf(
                                mapOf("optionId" to "opt-1", "text" to "Red"),
                                mapOf("optionId" to "opt-2", "text" to "Green"),
                            ),
                    ),
                "ownVote" to null,
                "distribution" to null,
            )

        val state = PollWireParser.questionState(hidden)
        assertNull(state.ownVote)
        assertNull(state.distribution, "скрыто — не нулевой счёт: распределения нет вовсе")
        assertEquals(0, state.question.questionIndex)
        assertEquals("SINGLE", state.question.mode.wire)
        assertEquals(2, state.question.options.size)

        val zero =
            hidden + mapOf<String, Any?>(
                "ownVote" to
                    mapOf(
                        "selectedOptionIds" to listOf("opt-1"),
                        "acceptedAtMs" to 1_760_000_000_000,
                    ),
                "distribution" to
                    mapOf(
                        "optionCounts" to mapOf("opt-1" to 0, "opt-2" to 0),
                        "respondentCount" to 1,
                    ),
            )

        val confirmed = PollWireParser.questionState(zero)
        assertEquals("opt-1", confirmed.ownVote?.selectedOptionIds?.single())
        assertTrue(confirmed.distribution!!.optionCounts.values.all { it == 0 },
            "нулевой счёт — честный ноль принятого голоса, не скрытие")
        assertEquals(1, confirmed.distribution!!.respondentCount)
    }

    @Test
    fun `broken shapes refuse instead of defaulting`() {
        val options =
            listOf(
                mapOf("optionId" to "opt-1", "text" to "Red"),
                mapOf("optionId" to "opt-2", "text" to "Green"),
            )
        val question =
            mapOf(
                "questionIndex" to 0,
                "text" to "Favorite color?",
                "mode" to "SINGLE",
                "options" to options,
            )
        val base =
            mapOf<String, Any?>(
                "viewVersion" to "poll-views-v1",
                "seriesId" to "poll-77",
                "title" to "Demo poll",
                "own" to false,
                "revisionId" to 2L,
                "questionCount" to 2,
                "questionIndex" to 0,
                "question" to question,
            )

        val badMode =
            base + mapOf<String, Any?>("question" to question + mapOf("mode" to "WEIRD"))
        assertFailsWith<IllegalArgumentException> { PollWireParser.questionState(badMode) }

        val missingCounts =
            base + mapOf<String, Any?>(
                "ownVote" to
                    mapOf("selectedOptionIds" to listOf("opt-1"), "acceptedAtMs" to 1L),
                "distribution" to mapOf("respondentCount" to 1),
            )
        assertFailsWith<IllegalStateException> { PollWireParser.questionState(missingCounts) }
    }
}
