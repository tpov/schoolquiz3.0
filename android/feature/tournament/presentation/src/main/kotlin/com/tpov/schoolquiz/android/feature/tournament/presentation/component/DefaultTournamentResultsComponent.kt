package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.failureNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventReadRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Итоги чемпионата над чтением события (история 5.11): места, слоты призов и эскроу — серверные
 * факты; «осталось» в завершённом событии показывает сервер, клиент его не вычисляет.
 */
class DefaultTournamentResultsComponent(
    context: ComponentContext,
    private val repository: TournamentCompetitionRepository,
    private val sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : TournamentResultsComponent,
    ComponentContext by context {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutable = MutableStateFlow(ResultsUiState(session = sessions.current()))
    override val state: StateFlow<ResultsUiState> = mutable.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    override fun onOpenEvent(eventId: String): Boolean {
        if (mutable.value.busy) return false
        val session = requireSession() ?: return false
        act(session) {
            val read = repository.readEvent(TournamentEventReadRequest(session.ownerUid, eventId)).getOrThrow()
            mutable.update {
                it.copy(
                    eventId = eventId,
                    detail = read,
                    notice = TournamentNotice.Ready,
                )
            }
        }
        return true
    }

    override fun onClose() {
        mutable.update {
            it.copy(
                eventId = null,
                detail = null,
                notice = TournamentNotice.NotLoaded,
            )
        }
    }

    private fun requireSession(): OutboxSession? {
        val session = sessions.current()
        if (session == null) mutable.update { it.copy(notice = TournamentNotice.AccountUnavailable) }
        return session
    }

    private fun act(
        session: OutboxSession,
        work: suspend () -> Unit,
    ) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, session = session) }
        scope.launch {
            try {
                work()
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                mutable.update {
                    it.copy(
                        notice =
                            if (sessions.current() == session) {
                                failureNotice(
                                    failure,
                                )
                            } else {
                                TournamentNotice.AccountChanged
                            },
                    )
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}
