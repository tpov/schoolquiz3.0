package com.tpov.schoolquiz.platform.firebase.economy

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.feature.economy.data.remote.OwnHistoryRemoteDataSource
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт собственной истории операций (AD-30/UX-20, история 8.6).
 *
 * Имена коллблов — ровно регистрации в `functions/index.js` (сторож —
 * [OwnHistoryCallableNamesTest]). Владелец выводится сервером из Auth: uid в теле не ездит.
 */
class FirebaseOwnHistoryRemoteDataSource(
    private val functions: FirebaseFunctions,
) : OwnHistoryRemoteDataSource {

    override suspend fun historyPage(
        cursor: String?,
        pageSize: Int,
    ): Any? =
        call(CALLABLE_HISTORY, payload(cursor, pageSize))

    override suspend fun lessonEarningsPage(
        lessonId: String,
        cursor: String?,
        pageSize: Int,
    ): Any? =
        call(
            CALLABLE_LESSON_EARNINGS,
            payload(cursor, pageSize) + mapOf(LESSON_ID to lessonId),
        )

    private fun payload(
        cursor: String?,
        pageSize: Int,
    ): Map<String, Any?> =
        buildMap {
            put(PAGE_SIZE, pageSize)
            cursor?.let { put(CURSOR, it) }
        }

    private suspend fun call(
        name: String,
        payload: Map<String, Any?>,
    ): Any? =
        try {
            functions
                .getHttpsCallable(name)
                .withAppTimeout()
                .call(payload)
                .await()
                .data
        } catch (e: CancellationException) {
            throw e
        }

}

/** Имена callable-поверхности — ровно регистрации в `functions/index.js` (сторож — тест имён). */
internal object OwnHistoryCallableNames {
    const val HISTORY: String = "listMyOperationHistory"
    const val LESSON_EARNINGS: String = "listMyLessonEarnings"
}

private const val CALLABLE_HISTORY: String = OwnHistoryCallableNames.HISTORY
private const val CALLABLE_LESSON_EARNINGS: String = OwnHistoryCallableNames.LESSON_EARNINGS

private const val PAGE_SIZE = "pageSize"
private const val CURSOR = "cursor"
private const val LESSON_ID = "lessonId"
