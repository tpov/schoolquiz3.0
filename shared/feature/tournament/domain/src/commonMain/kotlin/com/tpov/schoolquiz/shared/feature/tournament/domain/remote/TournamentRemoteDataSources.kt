package com.tpov.schoolquiz.shared.feature.tournament.domain.remote

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

/**
 * Порты чтения события: одно событие и ограниченная страница просмотра.
 *
 * Сессии мутаций здесь нет: ANSWER/ADVANCE/READY_ACK едут из очереди (outbox) — тем же
 * единственным приёмником, что и остальные отложенные действия. Прямые вызовы — только чтения,
 * подготовка банка, операции организатора/участника и запись скачивания (транспортный образец
 * Arena: явные вызовы, повторов здесь нет, durable-половина живёт у вызывающего).
 */
interface TournamentEventRemoteDataSource {
    /** Чтение одного события с его прогрессом и (в BRACKET/FINISHED) матчами. */
    suspend fun readEvent(request: TournamentEventReadRequest): Result<TournamentEventRead>

    /** Ограниченная страница событий для просмотра; курсор — [TournamentEventListRequest.afterEventId]. */
    suspend fun listEvents(request: TournamentEventListRequest): Result<TournamentEventPage>
}

/** Порт чтения встречи пары: предложения, подтверждения, готовность, курсоры страниц. */
interface TournamentMeetingRemoteDataSource {
    suspend fun readMeeting(request: TournamentMeetingReadRequest): Result<TournamentMeetingPage>
}

/** Порт чтения сессии: те же серверные часы и сохранённый прогресс, никогда их перезапуск. */
interface TournamentSessionReadRemoteDataSource {
    suspend fun readSession(request: TournamentSessionReadRequest): Result<TournamentSessionRead>
}

/**
 * Порт прямых операций: подготовка банка, операции организатора/участника (CREATE,
 * START_QUALIFICATION, PROPOSE, CONFIRM, READY) и серверно-засвидетельствованная запись
 * скачивания. Идемпотентность у операций — по присланному operationId.
 */
interface TournamentOperationRemoteDataSource {
    suspend fun prepare(request: TournamentPrepareRequest): Result<TournamentPrepareReply>

    suspend fun operate(request: TournamentOperateRequest): Result<TournamentOperationView>

    suspend fun recordDownload(request: TournamentDownloadRequest): Result<TournamentDownloadRecord>
}
