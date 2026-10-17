package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventListRequest
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
 * Сетка чемпионата над чтениями соревнования (история 5.11): страница событий и чтение одного
 * события — прогресс сетки, матчи, итоговые места и эскроу приезжают только с сервера; состояние
 * и решения об ошибках клиент не выдумывает ([TournamentNotice] — один словарь всех маршрутов).
 */
class DefaultTournamentBracketComponent(
    context: ComponentContext,
    private val repository: TournamentCompetitionRepository,
    private val sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : TournamentBracketComponent,
    ComponentContext by context {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutable = MutableStateFlow(BracketUiState(session = sessions.current()))
    override val state: StateFlow<BracketUiState> = mutable.asStateFlow()

    private var eventsCursor: String? = null

    /**
     * Поколение открытой сетки: закрытие делает недействительным чтение, летевшее к нему, —
     * закрытая сетка не открывается заново опоздавшим чтением.
     */
    private var detailGeneration = 0L

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    override fun onRefreshClick() {
        // Курсор сбрасывает сама загрузка после проверки занятости: сброс «до» молча терял бы
        // курсор, когда занятость не пустила загрузку.
        loadEvents(append = false)
    }

    override fun onLoadMoreClick() {
        if (eventsCursor == null) return
        loadEvents(append = true)
    }

    override fun onOpenEvent(eventId: String) {
        if (mutable.value.busy) return
        val session = requireSession() ?: return
        // Поколение снимается до запуска работы: закрытие между запуском и чтением обязано
        // опоздавшее чтение отвергнуть, а не «принять новое поколение».
        val generation = detailGeneration
        act(session) {
            val read = repository.readEvent(TournamentEventReadRequest(session.ownerUid, eventId)).getOrThrow()
            // Сетку закрыли, пока чтение летело: опоздавшее чтение её заново не открывает.
            if (generation != detailGeneration) return@act
            mutable.update {
                it.copy(
                    detail = read,
                    notice = TournamentNotice.Ready,
                )
            }
        }
    }

    override fun onCloseEvent() {
        detailGeneration++
        mutable.update {
            it.copy(
                detail = null,
                notice = TournamentNotice.NotLoaded,
            )
        }
    }

    private fun loadEvents(append: Boolean) {
        if (mutable.value.busy) return
        if (!append) eventsCursor = null
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
                                com.tpov.schoolquiz.android.feature.tournament.presentation.model.failureNotice(
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
