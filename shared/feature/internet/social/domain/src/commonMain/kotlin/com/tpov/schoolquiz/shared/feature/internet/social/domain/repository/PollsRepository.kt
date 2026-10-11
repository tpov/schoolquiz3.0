package com.tpov.schoolquiz.shared.feature.internet.social.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteSubmission

/**
 * Владелец опросов на клиенте (AD-1: Polls владеет сериями, голосами и разрешённой статистикой).
 *
 * Чтение — серверные view-коллблы (§5), запись — общая очередь (AD-6/AD-7). Локальной половины
 * у операций нет: ни одна таблица фичи не меняется, разрешённые проекции перечитываются из вью.
 */
interface PollsRepository {
    suspend fun listSeries(pageSize: Int = 20): Result<List<PollSeriesSummary>>

    suspend fun questionState(
        seriesId: String,
        questionIndex: Int,
    ): Result<PollQuestionState>

    suspend fun queueVote(submission: PollVoteSubmission): Result<Unit>

    suspend fun queuePublish(submission: PollPublishSubmission): Result<Unit>
}
