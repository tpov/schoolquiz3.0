package com.tpov.schoolquiz.shared.feature.quest.domain.use_case

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes quests authored by the current user, optionally filtered to a single catalog.
 *
 * Thin orchestration wrapper over [QuestRepository.observeMyQuests]. ViewModels
 * depend on this use case rather than the repository directly to keep the
 * composition root flexible.
 *
 * @param quests the [QuestRepository] to observe from.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   Scope > Domain layer — ObserveMyQuestsUseCase.
 *   Domain Test Scenario 32: authorUid="test-uid-42", 3 quests in repo → 2 emitted.
 *   Primary User Journey 7: "Мои квесты" catalog spinner filter.
 */
class ObserveMyQuestsUseCase(
    private val quests: QuestRepository,
) {
    /**
     * @param authorUid Firebase Auth UID of the current user (authorUid filter).
     * @param catalogId optional catalog filter; null means "all categories".
     * @return [Flow] emitting the filtered quest list from the local cache.
     */
    operator fun invoke(
        authorUid: String,
        catalogId: CatalogId? = null,
    ): Flow<List<Quest>> = quests.observeMyQuests(authorUid = authorUid, catalogId = catalogId)
}
