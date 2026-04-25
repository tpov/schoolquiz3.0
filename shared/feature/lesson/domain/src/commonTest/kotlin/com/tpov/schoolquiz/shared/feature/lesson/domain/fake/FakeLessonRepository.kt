package com.tpov.schoolquiz.shared.feature.lesson.domain.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [LessonRepository] for use case testing.
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
 *   Domain Test Scenario 43.
 */
class FakeLessonRepository(
    initial: List<Lesson> = emptyList(),
) : LessonRepository {

    private val cache = MutableStateFlow<Map<LessonId, Lesson>>(
        initial.associateBy { it.id },
    )

    private var pendingRemote: List<Lesson>? = null
    private var nextRefreshFailure: Throwable? = null

    /** The cursor value advanced by the last [refreshByParents] call. */
    var lastCursor: Long = 0L
        private set

    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> =
        cache.map { map ->
            map.values.filter { it.themeId == themeId }.sortedBy { it.order }
        }

    override suspend fun getById(id: LessonId): Lesson? = cache.value[id]

    override suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>> {
        lastCursor = cursor
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        val remote = pendingRemote
        val processedIds = mutableSetOf<LessonId>()
        if (remote != null) {
            pendingRemote = null
            cache.update { current ->
                val mutable = current.toMutableMap()
                var newMaxLastMod = cursor
                for (incoming in remote) {
                    if (incoming.themeId !in themeIds) continue
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

    override suspend fun getLocalContentsVersion(id: LessonId): Long? = cache.value[id]?.contentsVersion

    // ── Test helpers ──────────────────────────────────────────────────────────

    fun seed(lessons: List<Lesson>) { cache.value = lessons.associateBy { it.id } }
    fun simulateRemoteLessons(lessons: List<Lesson>) { pendingRemote = lessons }
    fun setNextRefreshFailure(error: Throwable) { nextRefreshFailure = error }
    fun snapshot(): List<Lesson> = cache.value.values.sortedBy { it.id.value }
}
