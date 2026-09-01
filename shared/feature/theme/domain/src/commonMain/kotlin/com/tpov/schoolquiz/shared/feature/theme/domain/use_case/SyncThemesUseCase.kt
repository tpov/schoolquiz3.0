package com.tpov.schoolquiz.shared.feature.theme.domain.use_case

import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository

/**
 * Triggers remote sync for themes of the given sections.
 *
 * Sync step 4: after the sections named by the `sync_changes` journal are synced,
 * pull their themes.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — FR#14 sync step 4.
 */
class SyncThemesUseCase(
    private val themes: ThemeRepository,
) {
    /**
     * @param sectionIds IDs of sections whose themes need to be refreshed.
     * @param cursor     local themesCursor (max lastModifiedAt seen so far). Default 0L.
     * @return [Result.success] containing the set of processed [ThemeId]s, passed to lesson sync
     *   as parentIds; [Result.failure] on error.
     */
    suspend operator fun invoke(sectionIds: Set<SectionId>, cursor: Long = 0L): Result<Set<ThemeId>> =
        themes.refreshByParents(sectionIds, cursor)
}
