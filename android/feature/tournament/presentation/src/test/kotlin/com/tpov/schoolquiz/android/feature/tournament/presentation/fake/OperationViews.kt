package com.tpov.schoolquiz.android.feature.tournament.presentation.fake

import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestion
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionResult
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionView

/** Ответ операции теста: только названные члены, остальные честно отсутствуют. */
fun operationView(
    status: String? = null,
    event: TournamentEventView? = null,
    session: TournamentSessionView? = null,
    sessions: List<TournamentSessionView>? = null,
    serverNowMs: Long? = null,
    currentQuestion: TournamentPublicQuestion? = null,
    result: TournamentSessionResult? = null,
): TournamentOperationView =
    TournamentOperationView(
        schemaVersion = null,
        operationId = null,
        status = status,
        serverNowMs = serverNowMs,
        deadlineReached = null,
        questionDeadlineReached = null,
        deliveredNowMs = null,
        window = null,
        event = event,
        session = session,
        sessions = sessions,
        meeting = null,
        currentQuestion = currentQuestion,
        lastClosed = null,
        readyAck = null,
        result = result,
    )
