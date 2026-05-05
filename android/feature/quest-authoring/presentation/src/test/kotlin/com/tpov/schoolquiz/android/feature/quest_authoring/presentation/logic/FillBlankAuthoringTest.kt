package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FillBlankAuthoringTest {

    @Test
    fun `given answers in text when build runtime text then replaces occurrences with blank markers`() {
        val result = buildFillBlankRuntimeText(
            text = "JetBrains создал Kotlin и Compose",
            answers =
                listOf(
                    FillBlankAnswerSpec("Kotlin"),
                    FillBlankAnswerSpec("Compose"),
                ),
        )

        assertEquals("JetBrains создал ___ и ___", result)
    }

    @Test
    fun `given protected answer when build visual segments then marks blank as protected`() {
        val result = buildFillBlankVisualSegments(
            text = "JetBrains создал Kotlin",
            answers = listOf(FillBlankAnswerSpec(text = "Kotlin", isProtected = true)),
        )

        assertEquals(
            listOf(
                FillBlankVisualSegment(text = "JetBrains создал ", isBlank = false, isProtected = false),
                FillBlankVisualSegment(text = "Kotlin", isBlank = true, isProtected = true),
            ),
            result,
        )
    }

    @Test
    fun `given missing answer when build runtime text then returns null`() {
        val result = buildFillBlankRuntimeText(
            text = "JetBrains создал Kotlin",
            answers = listOf(FillBlankAnswerSpec("Swift")),
        )

        assertNull(result)
    }

    @Test
    fun `given answer rows out of text order when order answers then returns text order`() {
        val result =
            orderFillBlankAnswersByText(
                text = "JetBrains создал Kotlin и Compose",
                answers =
                    listOf(
                        FillBlankAnswerSpec("Compose"),
                        FillBlankAnswerSpec("Kotlin"),
                    ),
            )

        assertEquals(
            listOf(
                FillBlankAnswerSpec("Kotlin"),
                FillBlankAnswerSpec("Compose"),
            ),
            result,
        )
    }

    @Test
    fun `given star blank markup when build runtime text then replaces with marker`() {
        val result = buildFillBlankRuntimeText(
            text = "JetBrains создал **Kotlin**",
            answers = emptyList(),
        )

        assertEquals("JetBrains создал ___", result)
    }

    @Test
    fun `given triple star markup when extract answers then marks blank protected`() {
        val result = extractFillBlankAnswers("JetBrains создал ***Kotlin***")

        assertEquals(listOf(FillBlankAnswerSpec(text = "Kotlin", isProtected = true)), result)
    }

    @Test
    fun `given single double and triple star markup when build visual segments then exposes all marker colors`() {
        val result = buildFillBlankVisualSegments(
            text = "*JetBrains* создал **Kotlin** и ***Compose***",
            answers = emptyList(),
        )

        assertEquals(
            listOf(
                FillBlankVisualSegment(text = "JetBrains", isBlank = false, isProtected = true),
                FillBlankVisualSegment(text = " создал ", isBlank = false, isProtected = false),
                FillBlankVisualSegment(text = "Kotlin", isBlank = true, isProtected = false),
                FillBlankVisualSegment(text = " и ", isBlank = false, isProtected = false),
                FillBlankVisualSegment(text = "Compose", isBlank = true, isProtected = true),
            ),
            result,
        )
    }

    @Test
    fun `given empty text when upsert first answer marker then appends blank marker`() {
        val result =
            upsertFillBlankAnswerMarker(
                text = "",
                answerIndex = 0,
                answer = FillBlankAnswerSpec(text = ""),
            )

        assertEquals("**blank**", result)
    }

    @Test
    fun `given blank marker when answer changes then marker receives answer text`() {
        val result =
            upsertFillBlankAnswerMarker(
                text = "JetBrains создал **blank**",
                answerIndex = 0,
                answer = FillBlankAnswerSpec(text = "Kotlin"),
            )

        assertEquals("JetBrains создал **Kotlin**", result)
    }

    @Test
    fun `given answer marker when protected flag changes then marker becomes triple star`() {
        val result =
            upsertFillBlankAnswerMarker(
                text = "JetBrains создал **Kotlin**",
                answerIndex = 0,
                answer = FillBlankAnswerSpec(text = "Kotlin", isProtected = true),
            )

        assertEquals("JetBrains создал ***Kotlin***", result)
    }

    @Test
    fun `given answer markers when remove second answer then removes second marker`() {
        val result =
            removeFillBlankAnswerMarker(
                text = "JetBrains создал **Kotlin** и **Compose**",
                answerIndex = 1,
            )

        assertEquals("JetBrains создал **Kotlin** и", result)
    }
}
