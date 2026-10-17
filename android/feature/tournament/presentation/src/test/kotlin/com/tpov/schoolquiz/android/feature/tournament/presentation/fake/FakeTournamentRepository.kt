package com.tpov.schoolquiz.android.feature.tournament.presentation.fake

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
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
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository

/** Прямой фейк 5.9-репозитория: готовые ответы и журнал вызовов для маршрутов 5.10 и 5.11. */
class FakeTournamentRepository : TournamentCompetitionRepository {
    val prepareCalls = mutableListOf<TournamentPrepareRequest>()
    val operateCalls = mutableListOf<TournamentOperateRequest>()
    val readEventCalls = mutableListOf<TournamentEventReadRequest>()
    val listEventsCalls = mutableListOf<TournamentEventListRequest>()
    val readSessionCalls = mutableListOf<TournamentSessionReadRequest>()
    val readMeetingCalls = mutableListOf<TournamentMeetingReadRequest>()
    val recordDownloadCalls = mutableListOf<TournamentDownloadRequest>()

    var prepareResult: Result<TournamentPrepareReply> =
        Result.failure(IllegalStateException("prepare is not configured"))
    var operateResult: Result<TournamentOperationView> =
        Result.failure(IllegalStateException("operate is not configured"))
    var readEventResult: Result<TournamentEventRead> =
        Result.failure(IllegalStateException("readEvent is not configured"))
    var listEventsResult: Result<TournamentEventPage> =
        Result.failure(IllegalStateException("listEvents is not configured"))
    var readSessionResult: Result<TournamentSessionRead> =
        Result.failure(IllegalStateException("readSession is not configured"))
    var readMeetingResult: Result<TournamentMeetingPage> =
        Result.failure(IllegalStateException("readMeeting is not configured"))
    var recordDownloadResult: Result<TournamentDownloadRecord> =
        Result.failure(IllegalStateException("recordDownload is not configured"))

    override suspend fun readEvent(request: TournamentEventReadRequest): Result<TournamentEventRead> {
        readEventCalls += request
        return readEventResult
    }

    override suspend fun listEvents(request: TournamentEventListRequest): Result<TournamentEventPage> {
        listEventsCalls += request
        return listEventsResult
    }

    override suspend fun readMeeting(request: TournamentMeetingReadRequest): Result<TournamentMeetingPage> {
        readMeetingCalls += request
        return readMeetingResult
    }

    override suspend fun readSession(request: TournamentSessionReadRequest): Result<TournamentSessionRead> {
        readSessionCalls += request
        return readSessionResult
    }

    override suspend fun prepare(request: TournamentPrepareRequest): Result<TournamentPrepareReply> {
        prepareCalls += request
        return prepareResult
    }

    override suspend fun operate(request: TournamentOperateRequest): Result<TournamentOperationView> {
        operateCalls += request
        return operateResult
    }

    override suspend fun recordDownload(request: TournamentDownloadRequest): Result<TournamentDownloadRecord> {
        recordDownloadCalls += request
        return recordDownloadResult
    }
}

/** Отказ сервера поимённо — той же обёрткой, что и настоящая транспортная граница. */
fun refused(reason: String): SyncFailure = SyncFailure(SyncError.Refused(reason))
