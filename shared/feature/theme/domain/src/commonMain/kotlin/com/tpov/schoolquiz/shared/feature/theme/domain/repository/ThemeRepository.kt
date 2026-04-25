package com.tpov.schoolquiz.shared.feature.theme.domain.repository

import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for reading and syncing [Theme]s.
 *
 * Production implementation lives in the data layer (phase-01).
 * Test doubles: [FakeThemeRepository] in the test source set.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — AC#5.
 */
interface ThemeRepository {

    /**
     * Observes themes belonging to [sectionId], sorted by [Theme.order] ASC.
     */
    fun observeBySection(sectionId: SectionId): Flow<List<Theme>>

    /**
     * Returns a single theme from the local cache, or null if absent.
     */
    suspend fun getById(id: ThemeId): Theme?

    /**
     * Pulls themes for the given [sectionIds] from remote and persists locally.
     *
     * Implements cascading sync step 4: fetch with
     * `where('sectionId', 'in', batch).where('lastModifiedAt', '>', cursor)`.
     * Cursor must be updated after successful refresh to max(lastModifiedAt).
     *
     * Spec: FR#14, Business Rule #8.
     *
     * @param sectionIds set of section ids whose themes to pull.
     * @param cursor     local themesCursor (max lastModifiedAt seen so far).
     * @return [Result.success] containing the set of processed [ThemeId]s (passed to lesson sync
     *   as parentIds); [Result.failure] on network/permission errors.
     */
    suspend fun refreshByParents(sectionIds: Set<SectionId>, cursor: Long): Result<Set<ThemeId>>

    /**
     * Returns the local [contentsVersion] for [id], or null if the theme is absent.
     * Used by the cascade orchestrator to detect contentsVersion changes.
     */
    suspend fun getLocalContentsVersion(id: ThemeId): Long?
}
