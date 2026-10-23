package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.PrivateQuizSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Форма «Створення квесту» в приватной категории (AD-29, история 8.3).
 *
 * Иерархия автору не показывается, карточка настроек — показывается, дефолты — доменные.
 * `hasValidStructureTitles` для приватной категории требует только названия квиза: плоский
 * HARD-набор иерархии не спрашивает.
 */
class QuestCreateUiStatePrivateCategoryTest {

    private val privateCatalog = CatalogDisplayItem(
        id = CatalogId(PrivateQuizSettings.PRIVATE_QUIZ_CATALOG_ID),
        name = "Приватные квизы",
        pictureUrl = null,
    )

    @Test
    fun privateCategory_recognizedByTheSeededTechnicalId() {
        val state = QuestCreateUiState(
            catalogs = listOf(privateCatalog),
            selectedCatalogId = privateCatalog.id,
            isWaitingForUser = false,
        )
        assertTrue(state.isPrivateCategory)
    }

    @Test
    fun ordinaryCategory_isNotPrivate() {
        val state = QuestCreateUiState(
            catalogs = listOf(CatalogDisplayItem(CatalogId("catalog-1"), "Криптография", null)),
            selectedCatalogId = CatalogId("catalog-1"),
            isWaitingForUser = false,
        )
        assertFalse(state.isPrivateCategory)
        assertFalse(state.canClosePrivateLink)
    }

    @Test
    fun defaults_areTheAd29Ones() {
        val state = QuestCreateUiState(
            catalogs = listOf(privateCatalog),
            selectedCatalogId = privateCatalog.id,
            isWaitingForUser = false,
        )
        assertEquals("дефолт AD-29: одна попытка", 1, state.privateAttemptLimit)
        assertFalse("дефолт AD-29: результат скрыт", state.privateShowResult)
        assertFalse("дефолт AD-29: ссылка без срока", state.privateLinkHasExpiry)
    }

    @Test
    fun structureTitles_relaxedForPrivate_onlyTheQuestTitleIsRequired() {
        val privateState =
            QuestCreateUiState(
                catalogs = listOf(privateCatalog),
                selectedCatalogId = privateCatalog.id,
                isWaitingForUser = false,
                newQuestTitle = "Мой приватный квиз",
            )
        // canCreate требует только названия квиза: раздел/тема/урок у плоского набора не спрашиваются.
        assertTrue("приватная категория создаёт квиз без иерархии", privateState.canCreate)

        val ordinaryState =
            QuestCreateUiState(
                catalogs = listOf(CatalogDisplayItem(CatalogId("catalog-1"), "Криптография", null)),
                selectedCatalogId = CatalogId("catalog-1"),
                isWaitingForUser = false,
                newQuestTitle = "Обычный квест",
            )
        assertFalse("обычной категории иерархия по-прежнему нужна", ordinaryState.canCreate)
    }

    @Test
    fun closeLink_availableOnlyWithAnActiveDraft() {
        val withoutDraft =
            QuestCreateUiState(
                catalogs = listOf(privateCatalog),
                selectedCatalogId = privateCatalog.id,
                isWaitingForUser = false,
            )
        assertFalse("повторное закрытие автору не предлагается", withoutDraft.canClosePrivateLink)

        val withDraft =
            withoutDraft.copy(activeDraftTitle = "Приватный квиз")
        assertTrue(withDraft.canClosePrivateLink)

        val queued = withDraft.copy(isLinkCloseQueued = true)
        assertFalse(queued.canClosePrivateLink)
    }
}
