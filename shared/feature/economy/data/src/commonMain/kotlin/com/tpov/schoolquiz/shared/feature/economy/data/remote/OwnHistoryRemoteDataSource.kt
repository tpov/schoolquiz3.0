package com.tpov.schoolquiz.shared.feature.economy.data.remote

/**
 * Транспорт собственной истории операций (AD-30/UX-20, история 8.6).
 *
 * Имена коллблов — ровно регистрации в `functions/index.js` (`listMyOperationHistory`,
 * `listMyLessonEarnings`, сторож — тест имён в platform/firebase). Владелец выводится сервером
 * из авторизации: `ownerUid` в теле не передаётся и не читается.
 */
interface OwnHistoryRemoteDataSource {
    suspend fun historyPage(
        cursor: String?,
        pageSize: Int,
    ): Any?

    suspend fun lessonEarningsPage(
        lessonId: String,
        cursor: String?,
        pageSize: Int,
    ): Any?
}
