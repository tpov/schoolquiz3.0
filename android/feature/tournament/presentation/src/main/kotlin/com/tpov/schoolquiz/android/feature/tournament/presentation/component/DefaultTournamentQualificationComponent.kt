package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxRepository
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventListRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Участие в квалификации поверх 5.9-слоя. Ответ едет только из очереди: намерение замораживается
 * в момент ответа, доставка и квитанция читаются оттуда же; READY_ACK ставится после доставки,
 * в окно, которое назвал сервер. Окно ведут серверные часы ([com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentWindowView.expiresAtMs]),
 * никакое клиентское время решением не является. Игра вопроса — общая машина
 * [TournamentSessionPlayComponent] (истории 5.10 и 5.11): квалификация и матч играют ею одной.
 */
class DefaultTournamentQualificationComponent(
    context: ComponentContext,
    repository: TournamentCompetitionRepository,
    outbox: TournamentSessionOutboxRepository,
    sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    nowMs: () -> Long = System::currentTimeMillis,
) : TournamentQualificationComponent,
    TournamentSessionPlayComponent(context, repository, outbox, sessions, dispatcher, nowMs) {
    private val mutable = MutableStateFlow(QualificationUiState(session = sessions.current()))
    override val state: StateFlow<QualificationUiState> = mutable.asStateFlow()

    override val host: TournamentPlayHost =
        object : TournamentPlayHost {
            override fun play(): PlayUiState? = mutable.value.play

            override fun reducePlay(transform: (PlayUiState?) -> PlayUiState?) {
                mutable.update { it.copy(play = transform(it.play)) }
            }

            override fun applyNotice(notice: TournamentNotice) {
                mutable.update { it.copy(notice = notice) }
            }

            override fun applyServerNow(serverNowMs: Long?) {
                mutable.update { it.copy(serverNowMs = serverNowMs ?: it.serverNowMs) }
            }

            override fun onSessionMerged(
                label: Int,
                sessionId: String,
                status: String?,
            ) {
                mutable.update { it.copy(rounds = mergeRound(it.rounds, label, sessionId, status)) }
            }

            override fun currentGeneration(): Long = eventGeneration

            override fun busyNow(): Boolean = mutable.value.busy

            override fun beginWork(session: OutboxSession) {
                mutable.update { it.copy(busy = true, session = session) }
            }

            override fun endWork() {
                mutable.update { it.copy(busy = false) }
            }
        }

    private var eventsCursor: String? = null

    /**
     * Личность попытки раунда: рождается на первом старте и переживает повторные попытки —
     * в пределах одного события. Смена события (выбор, повторный выбор, закрытие) стирает её:
     * START нового события не должен доигрывать личность операции чужого события.
     */
    private val attemptIds = mutableMapOf<Int, String>()
    private val operationIds = mutableMapOf<Int, String>()

    /**
     * Поколение открытого события: любой выбор или закрытие делает недействительными ответы,
     * летевшие по старому событию, — чтение сессии применяется, только если событие не менялось.
     */
    private var eventGeneration = 0L

    override fun onRefreshEventsClick() {
        eventsCursor = null
        loadEvents(append = false)
    }

    override fun onLoadMoreEventsClick() {
        if (eventsCursor == null) return
        loadEvents(append = true)
    }

    override fun onPickEvent(eventId: String) {
        if (mutable.value.busy) return
        val session = requireSession() ?: return
        // Смена события стирает личности попыток и ответы, летевшие по прежнему событию.
        attemptIds.clear()
        operationIds.clear()
        dropPendingAnswer()
        eventGeneration++
        act(session) {
            val read = repository.readEvent(TournamentEventReadRequest(session.ownerUid, eventId)).getOrThrow()
            mutable.update {
                it.copy(
                    event = read.event,
                    serverNowMs = read.serverNowMs,
                    rounds = defaultRounds(read.event.roundCount()),
                    play = null,
                    notice = TournamentNotice.Ready,
                )
            }
        }
    }

    override fun onCloseEvent() {
        stopSessionObservation()
        attemptIds.clear()
        operationIds.clear()
        dropPendingAnswer()
        eventGeneration++
        mutable.update {
            it.copy(
                event = null,
                serverNowMs = null,
                rounds = emptyList(),
                play = null,
                notice = TournamentNotice.NotLoaded,
            )
        }
    }

    override fun onLinkTokenChange(value: String) {
        mutable.update { it.copy(linkToken = value.trim()) }
    }

    override fun onStartRoundClick(roundNumber: Int) {
        val state = mutable.value
        val event = state.event ?: return
        if (state.busy || roundNumber !in 1..event.roundCount()) return
        val session = requireSession() ?: return
        val attemptId = attemptIds.getOrPut(roundNumber) { "attempt_" + ids().replace("-", "") }
        val operationId = operationIds.getOrPut(roundNumber) { ids() }
        act(session) {
            mutable.update { it.copy(notice = TournamentNotice.Ready) }
            val view =
                repository
                    .operate(
                        TournamentOperateRequest.StartQualification(
                            ownerUid = session.ownerUid,
                            operationId = operationId,
                            eventId = event.eventId,
                            roundNumber = roundNumber,
                            attemptId = attemptId,
                            linkToken = state.linkToken.takeIf { it.isNotEmpty() },
                        ),
                    ).getOrThrow()
            applyRounds(view.sessions, fallbackSession = view.session, pickedRound = roundNumber)
            val started = view.session ?: return@act
            readSessionInto(session, started.sessionId, roundNumber)
        }
    }

    private fun loadEvents(append: Boolean) {
        if (mutable.value.busy) return
        val session = requireSession() ?: return
        act(session) {
            val page =
                repository
                    .listEvents(
                        TournamentEventListRequest(
                            ownerUid = session.ownerUid,
                            afterEventId = if (append) eventsCursor else null,
                        ),
                    ).getOrThrow()
            eventsCursor = page.nextEventId
            mutable.update { current ->
                val rows = if (append) current.events + page.events else page.events
                current.copy(
                    events = rows.associateBy { it.eventId }.values.toList(),
                    canLoadMore = page.hasMore,
                    hasLoaded = true,
                    notice = TournamentNotice.Ready,
                )
            }
        }
    }

    private fun applyRounds(
        sessionsView: List<TournamentSessionView>?,
        fallbackSession: TournamentSessionView?,
        pickedRound: Int,
    ) {
        mutable.update { current ->
            var rounds = current.rounds
            sessionsView.orEmpty().forEach { row ->
                rounds = mergeRound(rounds, row.roundNumber ?: pickedRound, row.sessionId, row.status)
            }
            fallbackSession?.let { row ->
                rounds = mergeRound(rounds, row.roundNumber ?: pickedRound, row.sessionId, row.status)
            }
            current.copy(rounds = rounds)
        }
    }

    private fun defaultRounds(roundCount: Int): List<RoundProgress> =
        (1..roundCount).map {
            RoundProgress(
                it,
                null,
                null,
            )
        }

    private fun mergeRound(
        rounds: List<RoundProgress>,
        roundNumber: Int,
        sessionId: String?,
        status: String?,
    ): List<RoundProgress> {
        val index = rounds.indexOfFirst { it.roundNumber == roundNumber }
        val row =
            RoundProgress(
                roundNumber,
                sessionId ?: rounds.getOrNull(index)?.sessionId,
                status ?: rounds.getOrNull(index)?.status,
            )
        return if (index >= 0) rounds.toMutableList().also { it[index] = row } else rounds + row
    }

    private fun com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView.roundCount(): Int =
        qualification.roundCount
}
