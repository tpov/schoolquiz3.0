package com.tpov.schoolquiz.android.feature.quest.presentation

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote
import kotlinx.coroutines.flow.StateFlow

/**
 * Presentation contract for the "Мои квесты" screen.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.1
 * ADR-CMP-51: Decompose Component pattern.
 */
interface MyQuestsComponent {
    val state: StateFlow<MyQuestsUiState>

    /** Состояние диалога подачи с карточки черновика (4.11); null — диалог закрыт. */
    val submitState: StateFlow<DraftSubmitState?>

    /** null = "Все категории" (no filter). */
    fun onCatalogSelected(id: CatalogId?)

    /** Navigate to create-quest screen (Destination.OpenQuestCreate). */
    fun onCreateQuestClick()

    /** Drill-down from MyQuests into SectionList for the tapped quest. */
    fun onQuestClick(quest: QuestDisplayItem)

    /** Open existing local draft in the authoring flow. */
    fun onDraftClick(draft: DraftQuestDisplayItem)

    /** Запросить цитату платы и открыть диалог подачи для карточки (D13). */
    fun onDraftSubmitClick(draft: DraftQuestDisplayItem)

    /** Закрыть диалог подачи без отправки. */
    fun onDraftSubmitDismiss()

    /** Отправить черновик по подтверждённой цитате — той же очередью, что и форма подачи. */
    fun onDraftSubmitConfirm()
}

/**
 * UI state snapshot for "Мои квесты" screen.
 *
 * isGuest=true → quests always empty, no repository call made.
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.1 MyQuestsUiState
 */
data class MyQuestsUiState(
    val quests: List<QuestDisplayItem> = emptyList(),
    val drafts: List<DraftQuestDisplayItem> = emptyList(),
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val selectedCatalogId: CatalogId? = null,
    val isGuest: Boolean = false,
    val isLoading: Boolean = false,
)

data class DraftQuestDisplayItem(
    val id: QuestDraftId,
    val catalogId: CatalogId,
    val title: String,
    val status: QuestDraftStatus,
    val questionCount: Int,
    val isActive: Boolean,
    /** Причина возврата, если заявка не прошла: показывается автору прямо в списке. */
    val rejectionReason: String? = null,
)

/**
 * Диалог подачи с карточки (история 4.11, D13): цитата названа до отправки, подтверждение без
 * цитаты невозможно. Цитата — показ, не власть: плату считает сервер на приёмке.
 */
data class DraftSubmitState(
    val draft: DraftQuestDisplayItem,
    val quote: ReviewFeeQuote? = null,
    val isQuoteLoading: Boolean = false,
    val isQuoteFailed: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSubmitFailed: Boolean = false,
)
