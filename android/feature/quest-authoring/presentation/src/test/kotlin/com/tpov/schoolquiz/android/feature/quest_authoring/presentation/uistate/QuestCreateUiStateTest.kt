package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestCreateUiStateTest {

    @Test
    fun `question actions are available before structure titles are filled`() {
        val state = QuestCreateUiState(
            selectedCatalogId = CatalogId("catalog-1"),
            isWaitingForUser = false,
        )

        assertTrue(state.canOpenQuestions)
        assertFalse(state.canCreate)
    }

    @Test
    fun `question actions wait only for auth and busy states`() {
        assertFalse(
            QuestCreateUiState(
                selectedCatalogId = CatalogId("catalog-1"),
                isWaitingForUser = true,
            ).canOpenQuestions,
        )

        assertFalse(
            QuestCreateUiState(
                selectedCatalogId = CatalogId("catalog-1"),
                isWaitingForUser = false,
                isCreating = true,
            ).canOpenQuestions,
        )
    }
}
