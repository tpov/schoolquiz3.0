package com.tpov.schoolquiz.android.feature.tournament.presentation.fake

import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentAdmissionLink
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingView
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
    meeting: TournamentMeetingView? = null,
    serverNowMs: Long? = null,
    currentQuestion: TournamentPublicQuestion? = null,
    result: TournamentSessionResult? = null,
    admissionLink: TournamentAdmissionLink? = null,
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
        meeting = meeting,
        currentQuestion = currentQuestion,
        lastClosed = null,
        readyAck = null,
        result = result,
        admissionLink = admissionLink,
    )
