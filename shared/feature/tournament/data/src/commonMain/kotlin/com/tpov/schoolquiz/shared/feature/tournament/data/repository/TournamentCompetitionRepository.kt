package com.tpov.schoolquiz.shared.feature.tournament.data.repository

import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRecord
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventListRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareReply
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentEventRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentMeetingRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentOperationRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentSessionReadRemoteDataSource

/**
 * Прямые (вне очереди) обращения соревнования в одном фасаде: чтения события/встречи/сессии,
 * подготовка банка, операции организатора и участника, запись скачивания.
 *
 * Мутации сессии здесь намеренно нет: ANSWER/ADVANCE/READY_ACK едут из очереди через
 * [com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxRepository].
 * Решения об ошибках здесь тоже нет — Result с типизированными ветвями уходит наверх как есть.
 */
interface TournamentCompetitionRepository {
    suspend fun readEvent(request: TournamentEventReadRequest): Result<TournamentEventRead>

    suspend fun listEvents(request: TournamentEventListRequest): Result<TournamentEventPage>

    suspend fun readMeeting(request: TournamentMeetingReadRequest): Result<TournamentMeetingPage>

    suspend fun readSession(request: TournamentSessionReadRequest): Result<TournamentSessionRead>

    suspend fun prepare(request: TournamentPrepareRequest): Result<TournamentPrepareReply>

    suspend fun operate(request: TournamentOperateRequest): Result<TournamentOperationView>

    suspend fun recordDownload(request: TournamentDownloadRequest): Result<TournamentDownloadRecord>
}

/** Прямая делегация портам: строгий разбор и транспортные решения живут ниже и правее. */
class TournamentCompetitionRepositoryImpl(
    private val events: TournamentEventRemoteDataSource,
    private val meetings: TournamentMeetingRemoteDataSource,
    private val sessionReads: TournamentSessionReadRemoteDataSource,
    private val operations: TournamentOperationRemoteDataSource,
) : TournamentCompetitionRepository {
    override suspend fun readEvent(request: TournamentEventReadRequest): Result<TournamentEventRead> =
        events.readEvent(request)

    override suspend fun listEvents(request: TournamentEventListRequest): Result<TournamentEventPage> =
        events.listEvents(
            request,
        )

    override suspend fun readMeeting(request: TournamentMeetingReadRequest): Result<TournamentMeetingPage> =
        meetings.readMeeting(
            request,
        )

    override suspend fun readSession(request: TournamentSessionReadRequest): Result<TournamentSessionRead> =
        sessionReads.readSession(
            request,
        )

    override suspend fun prepare(request: TournamentPrepareRequest): Result<TournamentPrepareReply> =
        operations.prepare(
            request,
        )

    override suspend fun operate(request: TournamentOperateRequest): Result<TournamentOperationView> =
        operations.operate(
            request,
        )

    override suspend fun recordDownload(request: TournamentDownloadRequest): Result<TournamentDownloadRecord> =
        operations.recordDownload(
            request,
        )
}
