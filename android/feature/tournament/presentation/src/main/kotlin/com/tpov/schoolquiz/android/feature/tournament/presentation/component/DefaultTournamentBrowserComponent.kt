package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.failureNotice
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

/** Просмотр событий поверх 5.9-слоя: пейджинг курсором listEvents и честные состояния страницы
 * (испорченная/отказавшая страница — повтор, не краш). */
class DefaultTournamentBrowserComponent(
    context: ComponentContext,
    private val repository: TournamentCompetitionRepository,
    private val sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : TournamentBrowserComponent,
    ComponentContext by context {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutable = MutableStateFlow(BrowserUiState(session = sessions.current()))
    override val state: StateFlow<BrowserUiState> = mutable.asStateFlow()

    private var cursor: String? = null

    /** Поколение чтения детали: закрытие или новое открытие делают старый ответ недействительным. */
    private var readGeneration = 0L

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    override fun onRefreshClick() {
        cursor = null
        loadPage(append = false)
    }

    override fun onLoadMoreClick() {
        if (cursor == null) return
        loadPage(append = true)
    }

    override fun onOpenEvent(eventId: String) {
        if (mutable.value.busy) return
        val session =
            sessions.current() ?: run {
                mutable.update { it.copy(notice = TournamentNotice.AccountUnavailable) }
                return
            }
        val generation = ++readGeneration
        mutable.update { it.copy(busy = true, session = session, notice = TournamentNotice.Ready) }
        scope.launch {
            try {
                val read = repository.readEvent(TournamentEventReadRequest(session.ownerUid, eventId)).getOrThrow()
                // Деталь применяется, только если её не закрыли и не открывали другую в полёте.
                if (generation == readGeneration) {
                    mutable.update { it.copy(detail = read, notice = TournamentNotice.Ready) }
                }
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                if (generation == readGeneration) {
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
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCloseEvent() {
        readGeneration++
        mutable.update { it.copy(detail = null) }
    }

    private fun loadPage(append: Boolean) {
        if (mutable.value.busy) return
        val session =
            sessions.current() ?: run {
                mutable.update { it.copy(notice = TournamentNotice.AccountUnavailable, session = null) }
                return
            }
        mutable.update { it.copy(busy = true, session = session) }
        scope.launch {
            try {
                val page =
                    repository
                        .listEvents(
                            TournamentEventListRequest(
                                ownerUid = session.ownerUid,
                                afterEventId = if (append) cursor else null,
                            ),
                        ).getOrThrow()
                cursor = page.nextEventId
                mutable.update { current ->
                    val rows =
                        if (append) {
                            current.events + page.events
                        } else {
                            page.events
                        }
                    current.copy(
                        events = rows.associateBy { it.eventId }.values.toList(),
                        canLoadMore = page.hasMore,
                        hasLoaded = true,
                        notice = TournamentNotice.Ready,
                    )
                }
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                // Страница не поехала — то, что было, остаётся; повтор того же запроса предписан.
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
