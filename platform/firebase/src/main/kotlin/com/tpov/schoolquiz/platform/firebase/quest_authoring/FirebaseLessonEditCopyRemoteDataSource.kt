package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.LessonEditCopyWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonEditCopyRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт редактируемой копии урока (UX-23, история 4.23).
 *
 * Имя коллбла — ровно регистрация в `functions/index.js` (сторож —
 * [LessonEditCopyCallableNamesTest]). Владелец выводится сервером из Auth, поэтому ключа
 * владельца в полезной нагрузке нет; отказ транспорта едет как [SyncFailure], отказ строгого
 * декодера — тоже отказ чтения.
 */
class FirebaseLessonEditCopyRemoteDataSource(
    private val functions: FirebaseFunctions,
) : LessonEditCopyRemoteDataSource {
    override suspend fun fetchLessonEditCopy(lessonId: String): LessonEditCopy =
        try {
            val reply =
                functions
                    .getHttpsCallable(LessonEditCopyCallableNames.BUILD_EDIT_COPY)
                    .withAppTimeout()
                    .call(mapOf(LESSON_ID to lessonId))
                    .await()
                    .data
            LessonEditCopyWireParser.lessonEditCopy(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }
}

/** Имена callable-поверхности копии — ровно регистрации в `functions/index.js`. */
internal object LessonEditCopyCallableNames {
    const val BUILD_EDIT_COPY = "buildLessonEditCopy"
}

private const val LESSON_ID = "lessonId"
