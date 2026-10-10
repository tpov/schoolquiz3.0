package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.LessonErrorReportOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.InboxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonErrorReportSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.InboxRepository
import kotlinx.coroutines.CancellationException

/**
 * Общий inbox (AD-20): чтение — серверная вью через транспорт, подача — общая очередь (AD-6).
 * Локальной половины нет ни у одной из операций: отчёт и лист не меняют таблиц фичи.
 */
class InboxRepositoryImpl(
    private val remote: InboxRemoteDataSource,
    private val outbox: OutboxStore,
) : InboxRepository {
    override suspend fun listInboxItems(
        pageSize: Int,
        after: InboxAnchor?,
    ): Result<InboxPage> =
        try {
            Result.success(remote.listInboxItems(pageSize, after))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun queueErrorReport(submission: LessonErrorReportSubmission): Result<Unit> =
        try {
            outbox.enqueue(LessonErrorReportOutboxWriter.record(submission))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }
}
