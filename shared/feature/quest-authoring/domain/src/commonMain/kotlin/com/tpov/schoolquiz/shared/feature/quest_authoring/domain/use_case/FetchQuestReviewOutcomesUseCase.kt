package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestReviewOutcomeSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestReviewOutcomeRepository

/**
 * Исходы модельной проверки для экрана «Мои квесты» (история 4.16, UX-6).
 *
 * Оба чтения и доведение — в одном сценарии, потому что они живут вместе: увидев исход без
 * статуса, экран просит сервер довести заявку и перечитывает. Отказ чтения — пустой результат,
 * а не ошибка списка: черновики остаются на экране.
 */
class FetchQuestReviewOutcomesUseCase(
    private val repository: QuestReviewOutcomeRepository,
) {
    suspend operator fun invoke(
        submissionIds: Set<String>,
        legacyDraftIds: Set<String>,
    ): List<QuestReviewOutcomeSnapshot> {
        val firstPass = repository.fetch(submissionIds, legacyDraftIds).getOrNull() ?: return emptyList()
        val readyIds = firstPass.filter { it.outcomeReady }.map { it.submissionId }.toSet()
        if (readyIds.isEmpty()) return firstPass
        readyIds.forEach { id -> repository.advance(id) }
        return repository.fetch(submissionIds, legacyDraftIds).getOrNull() ?: firstPass
    }
}
