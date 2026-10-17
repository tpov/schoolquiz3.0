package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxRepository
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Игра матча на общей машине [TournamentSessionPlayComponent] (истории 5.10 и 5.11): компонент
 * задаёт только контекст матча — свою сессию и её поколение; сборка ответа, очередь 5.9,
 * квитанция READY_ACK и перечитывание по краю окна — общие с квалификацией.
 */
class DefaultTournamentMatchPlayComponent(
    context: ComponentContext,
    repository: TournamentCompetitionRepository,
    outbox: TournamentSessionOutboxRepository,
    sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    nowMs: () -> Long = System::currentTimeMillis,
) : TournamentMatchPlayComponent,
    TournamentSessionPlayComponent(context, repository, outbox, sessions, dispatcher, nowMs) {
    private val mutable = MutableStateFlow(MatchPlayUiState(session = sessions.current()))
    override val state: StateFlow<MatchPlayUiState> = mutable.asStateFlow()

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
            ) = Unit

            override fun currentGeneration(): Long = matchGeneration

            override fun busyNow(): Boolean = mutable.value.busy

            override fun beginWork(session: OutboxSession) {
                mutable.update { it.copy(busy = true, session = session) }
            }

            override fun endWork() {
                mutable.update { it.copy(busy = false) }
            }
        }

    /**
     * Поколение открытого матча: закрытие или открытие другого матча делает недействительными
     * ответы, летевшие по прежнему, — чтение сессии применяется, только если матч не менялся.
     */
    private var matchGeneration = 0L

    override fun onOpenMatchSession(
        eventId: String,
        matchId: String,
        sessionId: String,
    ): Boolean {
        if (mutable.value.busy) return false
        val session = requireSession() ?: return false
        // Открытие матча — новый контекст: ответы прежнего матча применены не будут, и его
        // наблюдение гасится сразу — неудавшееся открытие не оставляет чужого наблюдателя.
        matchGeneration++
        dropPendingAnswer()
        stopSessionObservation()
        mutable.update {
            it.copy(
                eventId = eventId,
                matchId = matchId,
                sessionId = sessionId,
                play = null,
                notice = TournamentNotice.NotLoaded,
            )
        }
        act(session) {
            readSessionInto(session, sessionId, label = 0)
        }
        return true
    }

    override fun onCloseMatchSession() {
        stopSessionObservation()
        matchGeneration++
        dropPendingAnswer()
        mutable.update {
            it.copy(
                eventId = null,
                matchId = null,
                sessionId = null,
                play = null,
                serverNowMs = null,
                notice = TournamentNotice.NotLoaded,
            )
        }
    }
}
