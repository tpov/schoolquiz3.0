package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure JVM round-trip tests for [Quest.toQuestDisplayItem].
 *
 * Verifies all fields are mapped correctly including nullable ones
 * (averageRating, averageRatingCount, pictureUrl) flagged by code-reviewer.
 *
 * Phase: 04 (code-reviewer finding)
 */
class QuestToDisplayItemMapperTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun questFixture(
        id: String = "q-1",
        catalogId: String = "cat-1",
        title: String = "Quest A",
        pictureUrl: String? = null,
        averageRating: Float? = null,
        averageRatingCount: Int = 0,
    ) = Quest(
        id = QuestId(id),
        catalogId = CatalogId(catalogId),
        authorUid = "author-uid",
        title = title,
        picturePath = null,
        pictureUrl = pictureUrl,
        visibleOn = setOf("home"),
        averageRating = averageRating,
        averageRatingCount = averageRatingCount,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
    )

    // ── id / catalogId / title ────────────────────────────────────────────────

    @Test
    fun `toQuestDisplayItem maps id correctly`() {
        val quest = questFixture(id = "q-1")
        assertEquals(QuestId("q-1"), quest.toQuestDisplayItem().id)
    }

    @Test
    fun `toQuestDisplayItem maps catalogId correctly`() {
        val quest = questFixture(catalogId = "cat-42")
        assertEquals(CatalogId("cat-42"), quest.toQuestDisplayItem().catalogId)
    }

    @Test
    fun `toQuestDisplayItem maps title correctly`() {
        val quest = questFixture(title = "My Quest")
        assertEquals("My Quest", quest.toQuestDisplayItem().title)
    }

    // ── pictureUrl ────────────────────────────────────────────────────────────

    @Test
    fun `toQuestDisplayItem maps pictureUrl when present`() {
        val quest = questFixture(pictureUrl = "https://example.com/pic.jpg")
        assertEquals("https://example.com/pic.jpg", quest.toQuestDisplayItem().pictureUrl)
    }

    @Test
    fun `toQuestDisplayItem maps pictureUrl as null when absent`() {
        val quest = questFixture(pictureUrl = null)
        assertNull(quest.toQuestDisplayItem().pictureUrl)
    }

    // ── averageRating ─────────────────────────────────────────────────────────

    @Test
    fun `toQuestDisplayItem maps averageRating when present`() {
        val quest = questFixture(averageRating = 2.5f)
        assertEquals(2.5f, quest.toQuestDisplayItem().averageRating)
    }

    @Test
    fun `toQuestDisplayItem maps averageRating as null when absent`() {
        val quest = questFixture(averageRating = null)
        assertNull(quest.toQuestDisplayItem().averageRating)
    }

    @Test
    fun `toQuestDisplayItem maps averageRating boundary values`() {
        assertEquals(0.0f, questFixture(averageRating = 0.0f).toQuestDisplayItem().averageRating)
        assertEquals(3.0f, questFixture(averageRating = 3.0f).toQuestDisplayItem().averageRating)
    }

    // ── averageRatingCount ────────────────────────────────────────────────────

    @Test
    fun `toQuestDisplayItem maps averageRatingCount correctly`() {
        val quest = questFixture(averageRatingCount = 42)
        assertEquals(42, quest.toQuestDisplayItem().averageRatingCount)
    }

    @Test
    fun `toQuestDisplayItem maps averageRatingCount zero as zero`() {
        val quest = questFixture(averageRatingCount = 0)
        assertEquals(0, quest.toQuestDisplayItem().averageRatingCount)
    }

    // ── full round-trip ───────────────────────────────────────────────────────

    @Test
    fun `toQuestDisplayItem full round-trip preserves all fields`() {
        val quest = questFixture(
            id = "q-99",
            catalogId = "cat-7",
            title = "Round-trip Quest",
            pictureUrl = "https://cdn.example.com/img.png",
            averageRating = 1.5f,
            averageRatingCount = 100,
        )
        val item = quest.toQuestDisplayItem()
        assertEquals(QuestId("q-99"), item.id)
        assertEquals(CatalogId("cat-7"), item.catalogId)
        assertEquals("Round-trip Quest", item.title)
        assertEquals("https://cdn.example.com/img.png", item.pictureUrl)
        assertEquals(1.5f, item.averageRating)
        assertEquals(100, item.averageRatingCount)
    }
}
