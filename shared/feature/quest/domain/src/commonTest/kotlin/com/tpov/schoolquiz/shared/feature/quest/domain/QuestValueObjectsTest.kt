package com.tpov.schoolquiz.shared.feature.quest.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Domain Test Scenarios for [QuestId] and [Quest] value-object construction.
 *
 * Covers scenarios from docs/features/home-and-my-quests/0-spec.md
 * § "Domain Test Scenarios":
 *   3-4   : QuestId blank / valid
 *   10-17 : Quest invariants (authorUid, title, picturePath, visibleOn, averageRating,
 *            averageRatingCount, version, lastModifiedAt)
 */
class QuestValueObjectsTest {

    // ── Scenario 3 : QuestId blank throws ─────────────────────────────────────
    @Test
    fun `scenario 3 QuestId with blank value throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            QuestId("")
        }
    }

    // ── Scenario 4 : QuestId valid constructs ─────────────────────────────────
    @Test
    fun `scenario 4 QuestId with non-blank value constructs without exception`() {
        val id = QuestId("q1")
        assertEquals("q1", id.value)
    }

    @Test
    fun `QuestId whitespace-only value throws`() {
        assertFailsWith<IllegalArgumentException> {
            QuestId("   ")
        }
    }

    // ── Scenario 10 : authorUid blank throws ──────────────────────────────────
    @Test
    fun `scenario 10 Quest with blank authorUid throws IllegalArgumentException`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validQuest(authorUid = "")
        }
        assertEquals(true, error.message?.contains("authorUid"))
    }

    @Test
    fun `Quest with whitespace-only authorUid throws`() {
        assertFailsWith<IllegalArgumentException> {
            validQuest(authorUid = "   ")
        }
    }

    // ── Scenario 11 : title blank throws ──────────────────────────────────────
    @Test
    fun `scenario 11 Quest with blank title throws IllegalArgumentException`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validQuest(title = "")
        }
        assertEquals(true, error.message?.contains("title"))
    }

    @Test
    fun `Quest with whitespace-only title throws`() {
        assertFailsWith<IllegalArgumentException> {
            validQuest(title = "   ")
        }
    }

    // ── Scenario 12 : visibleOn with unknown shelf throws ─────────────────────
    @Test
    fun `scenario 12 Quest with unknown shelf in visibleOn throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validQuest(visibleOn = setOf("invalid"))
        }
        assertEquals(true, error.message?.contains("visibleOn"))
    }

    @Test
    fun `Quest with mix of valid and invalid shelves throws`() {
        assertFailsWith<IllegalArgumentException> {
            validQuest(visibleOn = setOf("home", "badShelf"))
        }
    }

    // ── Scenario 13 : empty visibleOn is allowed at construction ──────────────
    @Test
    fun `scenario 13 Quest with empty visibleOn constructs without exception`() {
        val q = validQuest(visibleOn = emptySet())
        assertEquals(emptySet(), q.visibleOn)
    }

    // ── Scenario 14 : averageRating below 0 throws ────────────────────────────
    @Test
    fun `scenario 14 Quest with averageRating -0_1 throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validQuest(averageRating = -0.1f)
        }
        assertEquals(true, error.message?.contains("averageRating"))
    }

    // ── Scenario 15 : averageRating above 3.0 throws ─────────────────────────
    @Test
    fun `scenario 15 Quest with averageRating 3_5 throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validQuest(averageRating = 3.5f)
        }
        assertEquals(true, error.message?.contains("averageRating"))
    }

    // ── Scenario 16 : averageRatingCount negative throws ─────────────────────
    @Test
    fun `scenario 16 Quest with averageRatingCount -1 throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validQuest(averageRatingCount = -1)
        }
        assertEquals(true, error.message?.contains("averageRatingCount"))
    }

    // ── Scenario 17 : valid quest constructs — null and 2.7 rating both ok ────
    @Test
    fun `scenario 17a valid Quest with averageRating null constructs`() {
        val q = validQuest(averageRating = null)
        assertEquals(null, q.averageRating)
    }

    @Test
    fun `scenario 17b valid Quest with averageRating 2_7 constructs`() {
        val q = validQuest(averageRating = 2.7f)
        assertNotNull(q.averageRating)
    }

    @Test
    fun `Quest with version 0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            validQuest(version = 0L)
        }
    }

    @Test
    fun `Quest with negative lastModifiedAt throws`() {
        assertFailsWith<IllegalArgumentException> {
            validQuest(lastModifiedAt = -1L)
        }
    }

    @Test
    fun `Quest with lastModifiedAt zero constructs`() {
        val q = validQuest(lastModifiedAt = 0L)
        assertEquals(0L, q.lastModifiedAt)
    }

    @Test
    fun `Quest picturePath with https URL throws`() {
        assertFailsWith<IllegalArgumentException> {
            validQuest(picturePath = "https://example.com/img.jpg")
        }
    }

    @Test
    fun `Quest picturePath with gs URI throws`() {
        assertFailsWith<IllegalArgumentException> {
            validQuest(picturePath = "gs://bucket/img.jpg")
        }
    }

    @Test
    fun `Quest picturePath with blank string throws`() {
        assertFailsWith<IllegalArgumentException> {
            validQuest(picturePath = "")
        }
    }

    @Test
    fun `Quest picturePath with relative path constructs`() {
        val q = validQuest(picturePath = "quest-pictures/q1.jpg")
        assertEquals("quest-pictures/q1.jpg", q.picturePath)
    }

    @Test
    fun `Quest picturePath null constructs`() {
        val q = validQuest(picturePath = null)
        assertEquals(null, q.picturePath)
    }

    @Test
    fun `Quest with all valid fields constructs`() {
        val q = validQuest(
            visibleOn = setOf("home", "arena"),
            averageRating = 1.5f,
            averageRatingCount = 5,
            version = 3L,
            lastModifiedAt = 1714000000000L,
        )
        assertEquals(setOf("home", "arena"), q.visibleOn)
        assertEquals(1.5f, q.averageRating)
        assertEquals(5, q.averageRatingCount)
        assertEquals(3L, q.version)
        assertEquals(1714000000000L, q.lastModifiedAt)
    }

    @Test
    fun `Quest boundary rating 0_0 is accepted`() {
        val q = validQuest(averageRating = 0.0f)
        assertEquals(0.0f, q.averageRating)
    }

    @Test
    fun `Quest boundary rating 3_0 is accepted`() {
        val q = validQuest(averageRating = 3.0f)
        assertEquals(3.0f, q.averageRating)
    }

    @Test
    fun `Quest all valid shelves constructs`() {
        val q = validQuest(
            visibleOn = setOf("home", "arena", "tournament", "tournamentFinal", "archive"),
        )
        assertEquals(5, q.visibleOn.size)
    }

    @Test
    fun `Quest archived false by default`() {
        val q = validQuest()
        assertEquals(false, q.archived)
    }

    @Test
    fun `Quest archived true constructs`() {
        val q = validQuest(archived = true)
        assertEquals(true, q.archived)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun validQuest(
        id: QuestId = QuestId("q1"),
        catalogId: CatalogId = CatalogId("surveys"),
        authorUid: String = "test-uid-42",
        title: String = "My Quest",
        picturePath: String? = null,
        visibleOn: Set<String> = setOf("home"),
        averageRating: Float? = null,
        averageRatingCount: Int = 0,
        version: Long = 1L,
        lastModifiedAt: Long = 0L,
        archived: Boolean = false,
    ) = Quest(
        id = id,
        catalogId = catalogId,
        authorUid = authorUid,
        title = title,
        picturePath = picturePath,
        visibleOn = visibleOn,
        averageRating = averageRating,
        averageRatingCount = averageRatingCount,
        version = version,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
