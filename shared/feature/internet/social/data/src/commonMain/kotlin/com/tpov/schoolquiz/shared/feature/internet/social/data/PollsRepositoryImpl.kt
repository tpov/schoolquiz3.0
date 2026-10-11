package com.tpov.schoolquiz.shared.feature.internet.social.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.internet.social.data.outbox.PollOutboxWriters
import com.tpov.schoolquiz.shared.feature.internet.social.data.remote.PollsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.repository.PollsRepository
import kotlinx.coroutines.CancellationException

/**
 * Владелец опросов на клиенте (UX-O7, история 8.2): чтение — серверная вью через транспорт,
 * запись — общая очередь (AD-6). Локальной половины нет ни у одной из операций (AD-25).
 */
class PollsRepositoryImpl(
    private val remote: PollsRemoteDataSource,
    private val outbox: OutboxStore,
) : PollsRepository {
    override suspend fun listSeries(pageSize: Int): Result<List<PollSeriesSummary>> =
        try {
            Result.success(remote.listSeries(pageSize))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun questionState(
        seriesId: String,
        questionIndex: Int,
    ): Result<PollQuestionState> =
        try {
            Result.success(remote.questionState(seriesId, questionIndex))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun queueVote(submission: PollVoteSubmission): Result<Unit> =
        try {
            outbox.enqueue(PollOutboxWriters.voteRecord(submission))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun queuePublish(submission: PollPublishSubmission): Result<Unit> =
        try {
            outbox.enqueue(PollOutboxWriters.publishRecord(submission))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }
}
