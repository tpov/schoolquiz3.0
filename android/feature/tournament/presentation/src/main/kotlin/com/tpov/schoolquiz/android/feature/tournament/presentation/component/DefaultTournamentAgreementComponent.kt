package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.failureNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingSlot
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperationView
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
import java.util.UUID

/**
 * Согласование слота поверх прямых операций и чтения встречи (истории 5.7 и 5.11). Экран живёт
 * чтением: любое намерение — PROPOSE, CONFIRM, READY — после ответа сопровождается перечитыванием
 * встречи, поэтому восстановление при повторном входе и после отказа совпадает с тем, что видит
 * второй участник. Границы (предложение до C, подтверждение до C и не позже слота, готовность в
 * своём часу) считает сервер; клиент показывает их из проекции и честно называет отказ.
 *
 * Личность операции рождается на первой попытке и переживает повтор той же попытки: один и тот же
 * PROPOSE/CONFIRM под тем же operationId идемпотентен у сервера, а новый слот — новая личность.
 */
class DefaultTournamentAgreementComponent(
    context: ComponentContext,
    private val repository: TournamentCompetitionRepository,
    private val sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : TournamentAgreementComponent,
    ComponentContext by context {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutable = MutableStateFlow(AgreementUiState(session = sessions.current()))
    override val state: StateFlow<AgreementUiState> = mutable.asStateFlow()

    /**
     * Поколение открытого матча: закрытие/смена делает недействительными ответы, летевшие по
     * прежнему матчу, — чтение применяется, только если матч не менялся.
     */
    private var meetingGeneration = 0L

    /** Личности операций: ключ вида (намерение, матч, слот) — повтор не выдумывает новую. */
    private val operationIds = mutableMapOf<String, String>()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    override fun onOpenMatch(
        eventId: String,
        matchId: String,
    ): Boolean {
        if (mutable.value.busy) return false
        val session = requireSession() ?: return false
        meetingGeneration++
        operationIds.clear()
        mutable.update {
            it.copy(
                eventId = eventId,
                matchId = matchId,
                meeting = null,
                slotInput = "",
                // Прежний матч мог успеть завести свою сессию: новому матчу она не достаётся.
                playSessionId = null,
                notice = TournamentNotice.NotLoaded,
            )
        }
        act(session) {
            readMeetingInto(session, eventId, matchId, append = false, preserveNotice = false)
        }
        return true
    }

    override fun onCloseMatch() {
        meetingGeneration++
        operationIds.clear()
        mutable.update {
            it.copy(
                eventId = null,
                matchId = null,
                meeting = null,
                slotInput = "",
                playSessionId = null,
                notice = TournamentNotice.NotLoaded,
            )
        }
    }

    override fun onLoadMoreClick() {
        val state = mutable.value
        val eventId = state.eventId ?: return
        val matchId = state.matchId ?: return
        val page = state.meeting ?: return
        // Дочитка едет только по живому курсору: hasMore без курсора — конец страницы,
        // а не приглашение к перечитыванию первой страницы по кругу.
        val canPageOffers = page.hasMoreOffers && page.nextOfferId != null
        val canPageConfirmations = page.hasMoreConfirmations && page.nextConfirmationId != null
        if (state.busy || (!canPageOffers && !canPageConfirmations)) return
        val session = requireSession() ?: return
        act(session) {
            readMeetingInto(
                session,
                eventId,
                matchId,
                afterOfferId = page.nextOfferId.takeIf { page.hasMoreOffers },
                afterConfirmationId = page.nextConfirmationId.takeIf { page.hasMoreConfirmations },
                append = true,
                preserveNotice = false,
            )
        }
    }

    override fun onSlotStartsAtChange(value: String) {
        mutable.update { it.copy(slotInput = value.trim()) }
    }

    override fun onProposeClick() {
        val state = mutable.value
        val eventId = state.eventId ?: return
        val matchId = state.matchId ?: return
        if (state.busy) return
        // Слот из ввода: пустой или нечисловой ввод серверу не едет — ввод ещё не намерение.
        val slot = state.slotInput.toLongOrNull() ?: return
        val session = requireSession() ?: return
        val operationId = operationIds.getOrPut("PROPOSE/$matchId/$slot") { ids() }
        act(session) {
            mutable.update { it.copy(notice = TournamentNotice.Ready) }
            val outcome =
                runCatching {
                    repository
                        .operate(
                            TournamentOperateRequest.Propose(
                                ownerUid = session.ownerUid,
                                operationId = operationId,
                                eventId = eventId,
                                matchId = matchId,
                                slotStartsAtMs = slot,
                            ),
                        ).getOrThrow()
                }
            afterOperation(session, eventId, matchId, outcome)
        }
    }

    override fun onConfirmClick(slotStartsAtMs: Long) {
        val state = mutable.value
        val eventId = state.eventId ?: return
        val matchId = state.matchId ?: return
        if (state.busy) return
        // Подтверждение едет к слоту своей строки: черновой ввод не подменяет намерение строки —
        // свободный ввод кормит только явное намерение предложения (PROPOSE).
        val slot = slotStartsAtMs
        val session = requireSession() ?: return
        val operationId = operationIds.getOrPut("CONFIRM/$matchId/$slot") { ids() }
        act(session) {
            mutable.update { it.copy(notice = TournamentNotice.Ready) }
            val outcome =
                runCatching {
                    repository
                        .operate(
                            TournamentOperateRequest.Confirm(
                                ownerUid = session.ownerUid,
                                operationId = operationId,
                                eventId = eventId,
                                matchId = matchId,
                                slotStartsAtMs = slot,
                            ),
                        ).getOrThrow()
                }
            afterOperation(session, eventId, matchId, outcome)
        }
    }

    override fun onReadyClick() {
        val state = mutable.value
        val eventId = state.eventId ?: return
        val matchId = state.matchId ?: return
        if (state.busy) return
        val session = requireSession() ?: return
        val operationId = operationIds.getOrPut("READY/$matchId") { ids() }
        act(session) {
            mutable.update { it.copy(notice = TournamentNotice.Ready) }
            // Готовность принимает или отвергает сервер по часам согласованного слота: до слота,
            // в час слота или после него — честный исход сервера, а не локальная догадка.
            val outcome =
                runCatching {
                    repository
                        .operate(
                            TournamentOperateRequest.Ready(
                                ownerUid = session.ownerUid,
                                operationId = operationId,
                                eventId = eventId,
                                matchId = matchId,
                            ),
                        ).getOrThrow()
                }
            afterOperation(session, eventId, matchId, outcome)
        }
    }

    /**
     * Любой исход операции завершается перечитыванием встречи: согласие, отказ и границы —
     * серверные факты. Отказ пишется здесь ровно один раз (single-writer): `act`'ов catch
     * остаётся страховкой только для исключений мимо исхода операции. После отказа перечитывание
     * — сервис, а не источник правды об исходе: его собственная неудача сохранённый отказ
     * не затирает.
     */
    private suspend fun afterOperation(
        session: OutboxSession,
        eventId: String,
        matchId: String,
        outcome: Result<TournamentOperationView>,
    ) {
        val failure = outcome.exceptionOrNull()
        if (failure != null) {
            mutable.update { it.copy(notice = failureNotice(failure, agreementSurface = true)) }
            runCatching { readMeetingInto(session, eventId, matchId, append = false, preserveNotice = true) }
            return
        }
        capturePlaySession(outcome.getOrNull())
        readMeetingInto(session, eventId, matchId, append = false, preserveNotice = false)
    }

    private suspend fun readMeetingInto(
        session: OutboxSession,
        eventId: String,
        matchId: String,
        afterOfferId: String? = null,
        afterConfirmationId: String? = null,
        append: Boolean,
        preserveNotice: Boolean,
    ) {
        val generation = meetingGeneration
        val page =
            repository
                .readMeeting(
                    TournamentMeetingReadRequest(
                        ownerUid = session.ownerUid,
                        eventId = eventId,
                        matchId = matchId,
                        afterOfferId = afterOfferId,
                        afterConfirmationId = afterConfirmationId,
                    ),
                ).getOrThrow()
        // Матч закрыли или сменили, пока чтение летело: чужая страница не применяется.
        if (generation != meetingGeneration || sessions.current() != session) return
        mutable.update { current ->
            val page = page.honestPageable()
            current.copy(
                meeting =
                    if (append) {
                        current.meeting.appendedWith(page)
                    } else {
                        page
                    },
                notice =
                    if (preserveNotice) {
                        current.notice
                    } else {
                        TournamentNotice.Ready
                    },
            )
        }
    }

    /** hasMore без курсора не зовёт дальше: это конец страницы, а не приглашение к перечитыванию
     * первой страницы по кругу. Проецируем честно — и состояние, и кнопка дочитки говорят то же. */
    private fun TournamentMeetingPage.honestPageable(): TournamentMeetingPage =
        copy(
            hasMoreOffers = hasMoreOffers && nextOfferId != null,
            hasMoreConfirmations = hasMoreConfirmations && nextConfirmationId != null,
        )

    /** Своя сессия матча приезжает в ответе операции, когда сервер её уже создал; имени чужой
     * сессии в ответе нет — сервер выводит только сессию самого вызывающего. */
    private fun capturePlaySession(view: TournamentOperationView?) {
        val sessionId = view?.sessions?.firstOrNull()?.sessionId ?: return
        mutable.update { it.copy(playSessionId = sessionId) }
    }

    private fun TournamentMeetingPage?.appendedWith(page: TournamentMeetingPage): TournamentMeetingPage =
        if (this == null) {
            page
        } else {
            copy(
                offers = offers.mergeById(page.offers),
                hasMoreOffers = page.hasMoreOffers,
                nextOfferId = page.nextOfferId,
                confirmations = confirmations.mergeById(page.confirmations),
                hasMoreConfirmations = page.hasMoreConfirmations,
                nextConfirmationId = page.nextConfirmationId,
                ready = page.ready,
                meeting = page.meeting,
                serverNowMs = page.serverNowMs,
            )
        }

    private fun List<TournamentMeetingSlot>.mergeById(rows: List<TournamentMeetingSlot>): List<TournamentMeetingSlot> =
        (this + rows).associateBy { it.id }.values.toList()

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
                // Вся работа здесь — операции и чтения поверхности согласования: FAILED_PRECONDITION
                // сервера на ней означает границу встречи.
                mutable.update {
                    it.copy(
                        notice =
                            if (sessions.current() == session) {
                                failureNotice(
                                    failure,
                                    agreementSurface = true,
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
