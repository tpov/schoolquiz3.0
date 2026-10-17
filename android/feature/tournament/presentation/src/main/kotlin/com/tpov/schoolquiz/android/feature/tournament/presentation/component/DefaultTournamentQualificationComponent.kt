package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_COMPLETED
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_QUESTION_TIMED_OUT
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentAnswerPayload
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentQuestionTypes
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.WINDOW_OPEN
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.failureNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionIntent
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxRepository
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionReceiptOutcome
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventListRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionPayload
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionReadRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Участие в квалификации поверх 5.9-слоя. Ответ едет только из очереди: намерение замораживается
 * в момент ответа, доставка и квитанция читаются оттуда же; READY_ACK ставится после доставки
 * ответа, в окно, которое назвал сервер. Окно ведут серверные часы ([com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentWindowView.expiresAtMs]),
 * никакое клиентское время решением не является.
 */
class DefaultTournamentQualificationComponent(
    context: ComponentContext,
    private val repository: TournamentCompetitionRepository,
    private val outbox: TournamentSessionOutboxRepository,
    private val sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val nowMs: () -> Long = System::currentTimeMillis,
) : TournamentQualificationComponent,
    ComponentContext by context {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutable = MutableStateFlow(QualificationUiState(session = sessions.current()))
    override val state: StateFlow<QualificationUiState> = mutable.asStateFlow()

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

    /** Ответ, чья квитанция ещё не применена: страхует опрос — поздняя доставка доедет по нему. */
    private var pendingAnswer: PendingAnswer? = null

    /** Наблюдатель пометки «не отправлено» на активной сессии (AD-14). */
    private var sessionObserver: Job? = null

    /** Ответ и его маршрут применения: одно применение на квитанцию, кто бы ни приехал первым. */
    private class PendingAnswer(
        val session: OutboxSession,
        val sessionId: String,
        val roundNumber: Int,
        val intent: TournamentSessionIntent,
    )

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

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
        pendingAnswer = null
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
        sessionObserver?.cancel()
        sessionObserver = null
        attemptIds.clear()
        operationIds.clear()
        pendingAnswer = null
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

    override fun onToggleChoice(optionId: String) {
        mutable.update { current ->
            val play = current.play ?: return@update current
            val payload = play.question?.publicPayload ?: return@update current
            val selected =
                if (payload.type == TournamentQuestionTypes.MULTIPLE_CHOICE) {
                    if (optionId in play.selectedIds) play.selectedIds - optionId else play.selectedIds + optionId
                } else {
                    listOf(optionId)
                }
            current.copy(play = play.copy(selectedIds = selected))
        }
    }

    /** Дописывание порядка: повторный тап по выбранной строке убирает её — полный порядок
     * восстанавливаем перестановкой, а не сбросом всего ответа. */
    override fun onOrderTap(optionId: String) {
        mutable.update { current ->
            val play = current.play ?: return@update current
            val selected =
                if (optionId in play.selectedIds) {
                    play.selectedIds - optionId
                } else {
                    play.selectedIds + optionId
                }
            current.copy(play = play.copy(selectedIds = selected))
        }
    }

    override fun onBlankChange(
        index: Int,
        value: String,
    ) {
        mutable.update { current ->
            val play = current.play ?: return@update current
            val blanks = play.blanks.toMutableList()
            while (blanks.size <= index) blanks.add("")
            blanks[index] = value
            current.copy(play = play.copy(blanks = blanks))
        }
    }

    override fun onSubmitAnswerClick() {
        val state = mutable.value
        val play = state.play ?: return
        val question = play.question ?: return
        if (state.busy || play.awaitingDelivery || play.settled || play.timedOut) return
        val session = requireSession() ?: return
        val answerPayload = buildAnswerPayload(question.publicPayload, play) ?: return
        val intent =
            TournamentSessionIntent.Answer(
                sessionId = play.sessionId,
                questionIndex = play.questionIndex,
                questionId = question.questionId ?: return,
                answerPayload = answerPayload,
            )
        val roundNumber = play.roundNumber
        val pending =
            PendingAnswer(
                session = session,
                sessionId = play.sessionId,
                roundNumber = roundNumber,
                intent = intent,
            )
        pendingAnswer = pending
        act(session) {
            mutable.update { it.copy(notice = TournamentNotice.Ready, play = it.play?.copy(awaitingDelivery = true)) }
            outbox
                .enqueue(intent)
                .getOrElse { failure -> throw (failure as? Exception ?: IllegalStateException(failure.toString())) }
            observePending(play.sessionId, session)
            val outcome = awaitReceipt(intent, session.ownerUid)
            applyAnswerOutcome(pending, outcome)
        }
    }

    override fun onRecheckClick() {
        val state = mutable.value
        val play = state.play ?: return
        if (state.busy) return
        val session = requireSession() ?: return
        act(session) {
            readSessionInto(session, play.sessionId, play.roundNumber)
        }
    }

    /** Ответ уехал или получил квитанцию: следующие состояния — только серверные факты.
     * Применение одно: квитанция из опроса и поздняя квитанция не могут задвоить READY_ACK. */
    private suspend fun applyAnswerOutcome(
        pending: PendingAnswer,
        outcome: TournamentSessionReceiptOutcome?,
    ) {
        if (outcome == null) {
            // Сервер ещё не ответил, строка лежит в очереди: честное «отправляется» (AD-14).
            // Личность ответа остаётся страховать опрос: поздняя доставка доедет по ней.
            mutable.update { it.copy(notice = TournamentNotice.Pending) }
            return
        }
        // Применение одно: квитанция из опроса и поздняя квитанция не могут задвоить READY_ACK.
        if (pendingAnswer != pending) return
        pendingAnswer = null
        val session = pending.session
        when (outcome) {
            is TournamentSessionReceiptOutcome.Acknowledged -> {
                val view = outcome.receipt.view
                mutable.update { it.copy(play = it.play?.copy(lastStatus = view.status, awaitingDelivery = false)) }
                val window = view.window
                if (window != null && window.state == WINDOW_OPEN) {
                    val ack =
                        TournamentSessionIntent.ReadyAck(
                            sessionId = pending.sessionId,
                            questionIndex = window.nextQuestionIndex,
                            windowId = window.windowId,
                        )
                    outbox.enqueue(ack).getOrElse {
                            failure ->
                        throw (failure as? Exception ?: IllegalStateException(failure.toString()))
                    }
                    val ackOutcome = awaitReceipt(ack, session.ownerUid)
                    if (ackOutcome is TournamentSessionReceiptOutcome.Acknowledged) {
                        mutable.update { it.copy(play = it.play?.copy(readyAckWindowId = ack.windowId)) }
                    }
                }
                // Опоздавший ответ честен дважды: пока сервер не подвёл вопрос, метка «время вышло»
                // держится; подвёл — экран показывает то, где сервер, без выдуманного счёта.
                val timedOutQuestion = pending.intent.questionIndex.takeIf { view.status == SESSION_QUESTION_TIMED_OUT }
                readSessionInto(session, pending.sessionId, pending.roundNumber, preserveTimedOutFor = timedOutQuestion)
            }
            TournamentSessionReceiptOutcome.Invalid ->
                // Квитанция не признана: «неизвестно» не становится фактом — перечитывание у игрока.
                mutable.update {
                    it.copy(
                        notice = TournamentNotice.Retry,
                        play = it.play?.copy(awaitingDelivery = false),
                    )
                }
        }
    }

    private suspend fun readSessionInto(
        session: OutboxSession,
        sessionId: String,
        roundNumber: Int,
        preserveTimedOutFor: Int? = null,
    ) {
        val generation = eventGeneration
        val view = repository.readSession(TournamentSessionReadRequest(session.ownerUid, sessionId)).getOrThrow()
        val current = view.session
        if (current == null) {
            mutable.update { it.copy(notice = TournamentNotice.Retry) }
            return
        }
        // Событие закрыли или выбрали другое, пока чтение летело: чужой ответ не применяется.
        if (generation != eventGeneration || sessions.current() != session) return
        observePending(sessionId, session)
        val window = view.window ?: current.window
        // Край окна вернул тот же вопрос: собранный ответ игрока не стирается.
        val previous = mutable.value.play?.takeIf { it.sessionId == sessionId }
        val sameQuestion =
            previous != null &&
                previous.question?.questionId != null &&
                previous.question?.questionId == view.currentQuestion?.questionId
        mutable.update { state ->
            state.copy(
                serverNowMs = view.serverNowMs ?: state.serverNowMs,
                rounds = mergeRound(state.rounds, roundNumber, sessionId, current.status),
                play =
                    PlayUiState(
                        sessionId = sessionId,
                        roundNumber = current.roundNumber ?: roundNumber,
                        question = view.currentQuestion,
                        questionIndex = current.questionIndex,
                        window = window,
                        result = view.result,
                        lastStatus =
                            when {
                                view.result != null -> SESSION_COMPLETED
                                // Сервер ещё не подвёл опоздавший вопрос: честная метка остаётся.
                                preserveTimedOutFor != null && current.questionIndex == preserveTimedOutFor ->
                                    SESSION_QUESTION_TIMED_OUT
                                else -> null
                            },
                        readyAckWindowId =
                            state.play
                                ?.takeIf { it.sessionId == sessionId }
                                ?.readyAckWindowId,
                        selectedIds = if (sameQuestion) previous.selectedIds else emptyList(),
                        blanks =
                            if (sameQuestion) {
                                previous.blanks
                            } else {
                                List(view.currentQuestion?.publicPayload?.blanks?.size ?: 0) { "" }
                            },
                    ),
                notice = TournamentNotice.Ready,
            )
        }
        // Окно открыто: следующий вопрос приезжает его краем — перечитываем по серверному expiresAtMs.
        if (window?.state == WINDOW_OPEN) {
            val wait = (window.expiresAtMs - nowMs()).coerceAtLeast(0L)
            scope.launch {
                delay(wait)
                if (scope.isActive && mutable.value.play?.sessionId == sessionId) {
                    runCatching { readSessionInto(session, sessionId, roundNumber) }
                }
            }
        }
    }

    /** Квитанция лежит в хранилище приёмника; пока её нет — строка очереди ещё едет. */
    private suspend fun awaitReceipt(
        intent: TournamentSessionIntent,
        ownerUid: String,
    ): TournamentSessionReceiptOutcome? {
        repeat(RECEIPT_POLLS) {
            outbox.receipt(intent, ownerUid)?.let { return it }
            delay(RECEIPT_POLL_MS)
        }
        return null
    }

    private fun observePending(
        sessionId: String,
        session: OutboxSession,
    ) {
        sessionObserver?.cancel()
        sessionObserver =
            scope.launch {
                outbox.observeSession(session.ownerUid, sessionId).collect { sync ->
                    if (sessions.current() != session) {
                        mutable.update { it.copy(notice = TournamentNotice.AccountChanged) }
                        // Смена аккаунта кончает наблюдение: его нельзя оставить собирать дальше.
                        coroutineContext.cancel()
                        return@collect
                    }
                    val awaiting = sync is OutboxEntitySync.Queued
                    mutable.update { current ->
                        current.copy(play = current.play?.copy(awaitingDelivery = awaiting))
                    }
                    // Строка уехала, а опрос квитанции бюджетом не дождался: поздняя доставка
                    // проходит тот же путь применения, что и квитанция из опроса.
                    val pending = pendingAnswer
                    if (!awaiting && pending != null && pending.sessionId == sessionId) {
                        scope.launch {
                            val outcome = outbox.receipt(pending.intent, session.ownerUid)
                            if (outcome != null) {
                                applyAnswerOutcome(pending, outcome)
                            }
                        }
                    }
                }
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
        sessionsView: List<com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionView>?,
        fallbackSession: com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionView?,
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

    /** Ответ из собранного состояния: тип вопроса ведёт, идентификаторы строк — только публичные. */
    private fun buildAnswerPayload(
        payload: TournamentPublicQuestionPayload,
        play: PlayUiState,
    ) = when (payload.type) {
        TournamentQuestionTypes.SINGLE_CHOICE ->
            play.selectedIds.firstOrNull()?.let(
                TournamentAnswerPayload::singleChoice,
            )
        TournamentQuestionTypes.MULTIPLE_CHOICE ->
            play.selectedIds.takeIf {
                it.isNotEmpty()
            }?.let(TournamentAnswerPayload::multipleChoice)
        TournamentQuestionTypes.ORDERING ->
            play.selectedIds
                .takeIf { it.size == payload.items.orEmpty().size && it.isNotEmpty() }
                ?.let(TournamentAnswerPayload::ordering)
        TournamentQuestionTypes.FILL_BLANK ->
            play.blanks
                .takeIf { rows -> rows.size == payload.blanks.orEmpty().size && rows.all { it.isNotBlank() } }
                ?.let(TournamentAnswerPayload::fillBlank)
        else -> null
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

    private companion object {
        const val RECEIPT_POLLS = 100
        const val RECEIPT_POLL_MS = 100L
    }
}
