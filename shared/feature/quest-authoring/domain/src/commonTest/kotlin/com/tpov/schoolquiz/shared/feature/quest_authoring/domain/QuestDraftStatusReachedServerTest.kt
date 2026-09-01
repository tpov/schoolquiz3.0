package com.tpov.schoolquiz.shared.feature.quest_authoring.domain

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QUEST_DRAFT_STATUSES_REACHED_SERVER
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.hasReachedServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which statuses mean "the server has it" decides what a database rebuild destroys, so the set is
 * pinned here rather than left to be re-derived. A status added to the enum later lands in the
 * losing half by default, and the last test says so out loud instead of letting it pass unnoticed.
 */
class QuestDraftStatusReachedServerTest {

    @Test
    fun `only synced, review sent and published count as reaching the server`() {
        assertEquals(
            setOf(
                QuestDraftStatus.SYNCED,
                QuestDraftStatus.REVIEW_SENT,
                QuestDraftStatus.PUBLISHED,
            ),
            QUEST_DRAFT_STATUSES_REACHED_SERVER,
        )
    }

    @Test
    fun `the local-only statuses are outside the set`() {
        val localOnly = listOf(
            QuestDraftStatus.DRAFT,
            QuestDraftStatus.SAVED,
            QuestDraftStatus.SYNC_PENDING,
            QuestDraftStatus.CONFLICT,
            // Still sitting in a local queue at this point: nothing has left the device yet.
            QuestDraftStatus.REVIEW_QUEUED,
        )
        localOnly.forEach { status ->
            assertFalse(status.hasReachedServer, "$status must not count as delivered")
        }
    }

    @Test
    fun `every status is classified, and the two halves cover the enum exactly`() {
        val (delivered, local) = QuestDraftStatus.entries.partition { it.hasReachedServer }
        assertEquals(3, delivered.size, "delivered statuses: $delivered")
        assertEquals(5, local.size, "local-only statuses: $local")
        assertTrue(QuestDraftStatus.entries.size == delivered.size + local.size)
    }
}
