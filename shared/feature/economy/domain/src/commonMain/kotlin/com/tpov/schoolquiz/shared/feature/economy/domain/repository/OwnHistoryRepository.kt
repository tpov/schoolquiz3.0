package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.model.OwnHistoryPage

/**
 * Порт собственной истории операций (AD-30/UX-20, история 8.6) — read-only проекция.
 *
 * Чтение не создаёт финансового эффекта и не пишет ни в одну таблицу; владелец — текущая
 * сессия, и смена аккаунта отсекает поздний ответ (реализация обязана проверять uid при
 * ответе). Курсор выдаётся сервером и значим только под тем владельцем, которому выдан.
 */
interface OwnHistoryRepository {
    suspend fun historyPage(
        ownerUid: String,
        cursor: String?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Result<OwnHistoryPage>

    /** «Начисления» одного авторского урока — та же проекция, отбор по строке книги. */
    suspend fun lessonEarningsPage(
        ownerUid: String,
        lessonId: String,
        cursor: String?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Result<OwnHistoryPage>

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 20
    }
}
