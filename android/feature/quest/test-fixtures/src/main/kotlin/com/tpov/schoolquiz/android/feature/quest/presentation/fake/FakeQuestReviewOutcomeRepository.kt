package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestReviewOutcomeSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestReviewOutcomeRepository

/**
 * Фейк исходов модельной проверки (4.16) для компонентовых тестов: отдаёт снимки по черновикам и
 * считает вызовы доведения — тесты проверяют и показ, и «доведи статус».
 */
class FakeQuestReviewOutcomeRepository(
    private val snapshots: List<QuestReviewOutcomeSnapshot> = emptyList(),
    private val error: Exception? = null,
) : QuestReviewOutcomeRepository {
    val fetchCalls = mutableListOf<Pair<Set<String>, Set<String>>>()
    val advancedIds = mutableListOf<String>()

    override suspend fun fetch(
        submissionIds: Set<String>,
        legacyDraftIds: Set<String>,
    ): Result<List<QuestReviewOutcomeSnapshot>> {
        fetchCalls.add(submissionIds to legacyDraftIds)
        error?.let { return Result.failure(it) }
        val requested = legacyDraftIds + submissionIds
        return Result.success(snapshots.filter { it.draftId in requested || it.submissionId in requested })
    }

    override suspend fun advance(submissionId: String): Result<Unit> {
        advancedIds.add(submissionId)
        return Result.success(Unit)
    }
}
