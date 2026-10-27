package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Приехавший приватный квест кладётся с серверной версией — или без всякой (AD-12).
 *
 * Отсутствие версии здесь обязано доехать до черновика как отсутствие. Подстановка местного числа
 * (прежде — `localRevision` в источнике данных) превращала бы «сверять не с чем» в «сверено с
 * собой», а это и есть ложное расхождение.
 */
class PrivateQuestSnapshotMappingTest {

    @Test
    fun `given a snapshot with a server version then the draft keeps it`() {
        val entity = snapshot(serverRevision = 5L).toEntityBundle().draft

        assertEquals(5L, entity.serverRevision)
        assertEquals(5L, entity.localRevision)
    }

    @Test
    fun `given a snapshot without a server version then the draft has none`() {
        val entity = snapshot(serverRevision = null).toEntityBundle().draft

        assertNull(entity.serverRevision, "неназначенная версия остаётся неназначенной")
        assertEquals(1L, entity.localRevision, "счётчик правок начинается с единицы, а не с нуля")
    }

    private fun snapshot(serverRevision: Long?): PrivateQuestSnapshot =
        PrivateQuestSnapshot(
            serverRevision = serverRevision,
            changedAtMs = 2_000L,
            request =
                QuestArenaSubmissionRequest(
                    submissionId = "submission-1",
                    draftId = "draft-1",
                    ownerUid = "owner-1",
                    expectedVersion = serverRevision,
                    requestedAtMs = 2_000L,
                    draft =
                        ArenaDraftDto(
                            id = "draft-1",
                            catalogId = "math",
                            title = "Черновик",
                            description = null,
                            defaultLanguage = "ru",
                            defaultDifficulty = "EASY",
                            publicQuestId = null,
                            createdAtMs = 1_000L,
                            updatedAtMs = 2_000L,
                        ),
                    sections = emptyList(),
                    themes = emptyList(),
                    lessons = emptyList(),
                    questions = emptyList(),
                ),
        )
}
