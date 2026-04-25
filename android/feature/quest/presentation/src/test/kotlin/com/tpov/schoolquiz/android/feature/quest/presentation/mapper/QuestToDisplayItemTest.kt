package com.tpov.schoolquiz.android.feature.quest.presentation.mapper

import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildQuest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [Quest.toDisplayItem] extension mapper.
 *
 * Verifies that domain Quest fields are correctly mapped to QuestDisplayItem
 * for UI presentation (rating, picture URL, title).
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 * API: docs/features/home-and-my-quests/06-api-contract.md §7
 * Plan: docs/features/home-and-my-quests/plan/phase-05/tests.md §3
 *
 * AC#26 — averageRating passes through (star fills computed in UI layer).
 * AC#27 — null averageRating → null in display item (outline stars in UI).
 * AC#28 — pictureUrl from Quest.pictureUrl (remote URL preferred over picturePath).
 */
class QuestToDisplayItemTest {

    // ── AC#26 — quest with rating maps correctly ──────────────────────────────

    @Test
    fun `when quest has rating then display item preserves rating and count`() {
        val quest = buildQuest(
            id = "q1",
            title = "Test Quest",
            picturePath = null,
            pictureUrl = null,
            averageRating = 2.7f,
            averageRatingCount = 5,
        )

        val item = quest.toDisplayItem()

        assertEquals(QuestId("q1"), item.id)
        assertEquals("Test Quest", item.title)
        assertEquals(2.7f, item.averageRating)
        assertEquals(5, item.averageRatingCount)
        assertNull(item.pictureUrl)
    }

    // ── AC#27 — null rating → null in display item ────────────────────────────

    @Test
    fun `when quest has no rating then display item averageRating is null`() {
        val quest = buildQuest(averageRating = null, averageRatingCount = 0)

        val item = quest.toDisplayItem()

        assertNull(item.averageRating, "null averageRating must map to null (outline stars in UI)")
        assertEquals(0, item.averageRatingCount)
    }

    // ── AC#28 — pictureUrl (remote HTTPS) preferred over picturePath ──────────

    @Test
    fun `when quest has pictureUrl then display item uses remote url`() {
        val quest = buildQuest(
            picturePath = "quests/q1/pic.png",
            pictureUrl = "https://example.com/pic.png",
        )

        val item = quest.toDisplayItem()

        assertEquals("https://example.com/pic.png", item.pictureUrl,
            "remote pictureUrl must be used for Coil loading")
    }

    // ── picturePath=null and no url → placeholder ─────────────────────────────

    @Test
    fun `when quest has no picture then display item pictureUrl is null`() {
        val quest = buildQuest(picturePath = null, pictureUrl = null)

        val item = quest.toDisplayItem()

        assertNull(item.pictureUrl, "null pictureUrl → placeholder icon in UI")
    }

    // ── zero rating value (not null) preserves distinction ────────────────────

    @Test
    fun `when quest averageRating is zero then display item reflects zero not null`() {
        val quest = buildQuest(averageRating = 0.0f, averageRatingCount = 3)

        val item = quest.toDisplayItem()

        assertEquals(0.0f, item.averageRating, "0.0f rating must be distinct from null (no-ratings)")
        assertEquals(3, item.averageRatingCount)
    }
}
