package com.tpov.schoolquiz.shared.feature.theme.domain.fake

import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [ThemeRepository] for use case testing.
 *
 * The [refreshByParents] implementation simulates cursor-based delta sync:
 *  - Items with lastModifiedAt <= cursor → skip.
 *  - Items with `archived=true` and higher version → local delete.
 *  - Items with incoming.version <= local.version → skip (version guard).
 *  - Otherwise → upsert.
 *
 * [lastCursor] tracks the cursor advanced after each [refreshByParents] call.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — AC#6.
 *   Domain Test Scenario 42.
 */
class FakeThemeRepository(
    initial: List<Theme> = emptyList(),
) : ThemeRepository {

    private val cache = MutableStateFlow<Map<ThemeId, Theme>>(
        initial.associateBy { it.id },
    )

    private var pendingRemote: List<Theme>? = null
    private var nextRefreshFailure: Throwable? = null

    /** The cursor value advanced by the last [refreshByParents] call. */
    var lastCursor: Long = 0L
        private set

    override fun observeBySection(sectionId: SectionId): Flow<List<Theme>> =
        cache.map { map ->
            map.values.filter { it.sectionId == sectionId }.sortedBy { it.order }
        }

    override suspend fun getById(id: ThemeId): Theme? = cache.value[id]

    override suspend fun refreshByParents(sectionIds: Set<SectionId>, cursor: Long): Result<Set<ThemeId>> {
        lastCursor = cursor
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        val remote = pendingRemote
        val processedIds = mutableSetOf<ThemeId>()
        if (remote != null) {
            pendingRemote = null
            cache.update { current ->
                val mutable = current.toMutableMap()
                var newMaxLastMod = cursor
                for (incoming in remote) {
                    if (incoming.sectionId !in sectionIds) continue
                    // Cursor guard
                    if (incoming.lastModifiedAt <= cursor) continue
                    newMaxLastMod = maxOf(newMaxLastMod, incoming.lastModifiedAt)
                    processedIds.add(incoming.id)
                    val existing = mutable[incoming.id]
                    when {
                        incoming.archived && (existing == null || incoming.version > existing.version) -> {
                            mutable.remove(incoming.id)
                        }
                        !incoming.archived -> {
                            if (existing == null || incoming.version > existing.version) {
                                mutable[incoming.id] = incoming
                            }
                        }
                    }
                }
                lastCursor = newMaxLastMod
                mutable
            }
        }
        return Result.success(processedIds)
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    fun seed(themes: List<Theme>) { cache.value = themes.associateBy { it.id } }
    fun simulateRemoteThemes(themes: List<Theme>) { pendingRemote = themes }
    fun setNextRefreshFailure(error: Throwable) { nextRefreshFailure = error }
    fun snapshot(): List<Theme> = cache.value.values.sortedBy { it.id.value }
}
