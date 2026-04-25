package com.tpov.schoolquiz.shared.feature.quest.domain.use_case

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository

/**
 * Triggers a remote sync for quests visible to the current user.
 *
 * Orchestrates the Step 2 of the cascading sync algorithm:
 *   1. Calls [QuestRepository.refreshFromRemote] with the user's [currentUserUid],
 *      [availableShelves], [catalogIdsToSync], and [cursor].
 *   2. Returns [Result.success] if the refresh completed (even if no changes were
 *      found on remote).
 *   3. Returns [Result.failure] on network / permission errors — callers
 *      (typically [SyncWorker]) handle retry logic via WorkManager backoff.
 *
 * This use case is a thin orchestration wrapper; the actual merge/dedupe/upsert
 * logic lives in the data layer implementation of [QuestRepository].
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   Scope > Domain layer — SyncQuestsUseCase.
 *   FR#14 cascading sync step 2.
 *   Domain Test Scenario 34: SyncQuestsUseCase with fake dependencies,
 *     remote has changes → local reflects changes.
 */
class SyncQuestsUseCase(
    private val quests: QuestRepository,
) {
    /**
     * @param currentUserUid   Firebase Auth UID of the current user, or null if guest.
     *   When null, Query A (own quests by authorUid) is skipped; Query B (public) runs.
     * @param availableShelves Set of shelf names the current user has access to.
     *   Baseline MVP: `{"home", "arena"}`.
     * @param catalogIdsToSync Catalog ids whose contentsVersion changed in Step 1.
     * @param cursor           Local questsCursor (max lastModifiedAt seen so far). Default 0L.
     * @return [Result.success] containing the set of processed [QuestId]s for cascade trigger;
     *   [Result.failure] on network/permission errors.
     */
    suspend operator fun invoke(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId> = emptySet(),
        cursor: Long = 0L,
    ): Result<Set<QuestId>> = quests.refreshFromRemote(
        currentUserUid = currentUserUid,
        availableShelves = availableShelves,
        catalogIdsToSync = catalogIdsToSync,
        cursor = cursor,
    )
}
