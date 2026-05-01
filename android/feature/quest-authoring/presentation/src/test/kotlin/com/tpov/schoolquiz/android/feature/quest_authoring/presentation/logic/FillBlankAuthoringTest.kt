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
}
