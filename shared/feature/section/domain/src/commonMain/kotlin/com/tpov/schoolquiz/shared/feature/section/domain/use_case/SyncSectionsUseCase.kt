package com.tpov.schoolquiz.shared.feature.section.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository

/**
 * Triggers remote sync for sections of the given quests.
 *
 * Used by the sync orchestrator (SyncWorker step 3): after the quests reported by the
 * `sync_changes` journal are synced, pull their sections.
 *
 * Thin orchestration wrapper — actual Firestore batching lives in the data layer.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   FR#14 sync step 3.
 *   Primary User Journey 5: admin adds question → section is refreshed.
 */
class SyncSectionsUseCase(
    private val sections: SectionRepository,
) {
    /**
     * @param questIds IDs of quests whose sections need to be refreshed.
     * @param cursor   local sectionsCursor (max lastModifiedAt seen so far). Default 0L.
     * @return [Result.success] containing the set of processed [SectionId]s, passed on as parent ids
     *   to the theme sync step;
     *   [Result.failure] on error.
     */
    suspend operator fun invoke(questIds: Set<QuestId>, cursor: Long = 0L): Result<Set<SectionId>> =
        sections.refreshByParents(questIds, cursor)
}
