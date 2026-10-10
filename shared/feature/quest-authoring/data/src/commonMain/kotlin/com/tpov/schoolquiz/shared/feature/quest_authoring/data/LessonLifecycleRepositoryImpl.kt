package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.LessonLifecycleWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonLifecycleRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecyclePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltyPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonLifecycleRepository

/**
 * Проецирует view-ответы на домен; отказ сервера — [SyncFailure] поверх исходной ошибки.
 * Локальной половины нет и не надо: истина живёт на сервере (AD-25), кэш проекции только врёт бы.
 */
class LessonLifecycleRepositoryImpl(
    private val remote: LessonLifecycleRemoteDataSource,
) : LessonLifecycleRepository {    override suspend fun listOwned(
        ownerUid: String,
        afterLessonId: String?,
        pageSize: Int,
    ): Result<LessonLifecyclePage> =
        read { remote.listOwned(afterLessonId, pageSize) }

    override suspend fun listCurator(
        ownerUid: String,
        afterLessonId: String?,
        pageSize: Int,
    ): Result<LessonLifecyclePage> =
        read { remote.listCurator(afterLessonId, pageSize) }

    override suspend fun listHistory(
        ownerUid: String,
        lessonId: String,
        cursor: String?,
        pageSize: Int,
    ): Result<LessonHistoryPage> =
        read { remote.listHistory(lessonId, cursor, pageSize) }

    override suspend fun listRoyalty(
        ownerUid: String,
        cursor: String?,
        pageSize: Int,
    ): Result<LessonRoyaltyPage> =
        read { remote.listRoyalty(cursor, pageSize) }

    override suspend fun transition(
        ownerUid: String,
        command: LessonTransitionCommand,
        mutationId: String,
    ): Result<LessonTransitionDecision> =
        read { remote.transition(command, mutationId) }

    private suspend fun <T> read(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: SyncFailure) {
            // Транспорт уже назвал свою ветвь — перепревязывать нельзя.
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Result.failure(SyncFailure(SyncError.Unknown(error), error))
        } catch (error: IllegalStateException) {
            Result.failure(SyncFailure(SyncError.Unknown(error), error))
        }
}
