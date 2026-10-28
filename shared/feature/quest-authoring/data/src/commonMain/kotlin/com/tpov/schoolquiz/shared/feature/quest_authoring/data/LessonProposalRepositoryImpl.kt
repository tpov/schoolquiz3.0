package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.ProposalOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonEditCopyRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonProposalQuoteRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import kotlinx.coroutines.CancellationException

/**
 * Предложения автору (AD-33, истории 4.22–4.24): подачи — общая очередь, цитата и копия —
 * читающие callable.
 *
 * Локальной половины нет ни у одной из операций (AD-25): приёмник 4.21 создаёт запись из тела,
 * очередь ничего в таблицах фичи не меняет; тариф — серверная правда, кэш цитаты только врал бы.
 */
class LessonProposalRepositoryImpl(
    private val outbox: OutboxStore,
    private val quoteRemote: LessonProposalQuoteRemoteDataSource,
    private val editCopyRemote: LessonEditCopyRemoteDataSource,
) : LessonProposalRepository {
    override suspend fun queueErrorReportProposal(proposal: ErrorReportProposal): Result<Unit> =
        try {
            outbox.enqueue(ProposalOutboxWriter.recordErrorReport(proposal))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun queueNewLessonProposal(proposal: NewLessonProposal): Result<Unit> =
        try {
            outbox.enqueue(ProposalOutboxWriter.recordNewLesson(proposal))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun queueCopyEditProposal(proposal: CopyEditProposal): Result<Unit> =
        try {
            outbox.enqueue(ProposalOutboxWriter.recordCopyEdit(proposal))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun fetchLessonEditCopy(lessonId: String): Result<LessonEditCopy> =
        try {
            Result.success(editCopyRemote.fetchLessonEditCopy(lessonId))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun quoteProposalFee(
        kind: String,
        changedQuestions: Int,
    ): Result<LessonProposalFeeQuote> =
        try {
            Result.success(quoteRemote.quoteProposalFee(kind, changedQuestions))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }
}
