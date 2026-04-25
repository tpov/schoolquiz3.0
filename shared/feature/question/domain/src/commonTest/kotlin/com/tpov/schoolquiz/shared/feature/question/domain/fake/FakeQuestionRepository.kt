package com.tpov.schoolquiz.shared.feature.question.domain.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [QuestionRepository] for use case testing.
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
 *   Domain Test Scenarios 44, 49.
 */
class FakeQuestionRepository(
    initial: List<Question> = emptyList(),
) : QuestionRepository {

    private val cache = MutableStateFlow<Map<QuestionId, Question>>(
        initial.associateBy { it.id },
    )

    private var pendingRemote: List<Question>? = null
    private var nextRefreshFailure: Throwable? = null

    /** The cursor value advanced by the last [refreshByParents] call. */
    var lastCursor: Long = 0L
        private set

    override fun observeByLesson(lessonId: LessonId): Flow<List<Question>> =
        cache.map { map ->
            map.values.filter { it.lessonId == lessonId }.sortedBy { it.order }
        }

    override suspend fun getById(id: QuestionId): Question? = cache.value[id]

    override suspend fun refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit> {
        lastCursor = cursor
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        val remote = pendingRemote
        if (remote != null) {
            pendingRemote = null
            cache.update { current ->
                val mutable = current.toMutableMap()
                var newMaxLastMod = cursor
                for (incoming in remote) {
                    if (incoming.lessonId !in lessonIds) continue
                    // Cursor guard
                    if (incoming.lastModifiedAt <= cursor) continue
                    newMaxLastMod = maxOf(newMaxLastMod, incoming.lastModifiedAt)
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
        return Result.success(Unit)
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    fun seed(questions: List<Question>) { cache.value = questions.associateBy { it.id } }
    fun simulateRemoteQuestions(questions: List<Question>) { pendingRemote = questions }
    fun setNextRefreshFailure(error: Throwable) { nextRefreshFailure = error }
    fun snapshot(): List<Question> = cache.value.values.sortedBy { it.id.value }
}
