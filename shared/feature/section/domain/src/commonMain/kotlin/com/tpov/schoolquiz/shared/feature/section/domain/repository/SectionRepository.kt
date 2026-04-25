package com.tpov.schoolquiz.shared.feature.section.domain.repository

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for reading and syncing [Section]s.
 *
 * Production implementation lives in the data layer (phase-01).
 * Test doubles: [FakeSectionRepository] in the test source set.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   AC#5 — SectionRepository must have observeByParent, refreshByParents, getById.
 */
interface SectionRepository {

    /**
     * Observes sections belonging to the given [questId], sorted by [Section.order] ASC.
     *
     * Spec: AC#5.
     */
    fun observeByQuest(questId: QuestId): Flow<List<Section>>

    /**
     * Returns a single section from the local cache, or null if absent.
     */
    suspend fun getById(id: SectionId): Section?

    /**
     * Pulls sections for the given [questIds] from remote and persists them locally.
     *
     * Implements cascading sync step 3: for quests whose contentsVersion grew,
     * fetch sections with `where('questId', 'in', batch).where('lastModifiedAt', '>', cursor)`.
     * Batched in groups of <= 30 (Firestore `in`-limit).
     *
     * Cursor must be updated after successful refresh to max(lastModifiedAt) of returned items.
     *
     * Spec: FR#14 cascading sync step 3, Business Rule #8.
     *
     * @param questIds set of quest ids whose sections to pull.
     * @param cursor   local sectionsCursor (max lastModifiedAt seen so far).
     * @return [Result.success] containing the set of processed [SectionId]s (passed to theme sync
     *   as parentIds); [Result.failure] on network/permission errors.
     */
    suspend fun refreshByParents(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>>

    /**
     * Returns the local [contentsVersion] for [id], or null if the section is absent.
     * Used by the cascade orchestrator to detect contentsVersion changes.
     */
    suspend fun getLocalContentsVersion(id: SectionId): Long?
}
