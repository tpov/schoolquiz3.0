package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_COMPLETED
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_QUESTION_TIMED_OUT
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentAnswerPayload
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentQuestionTypes
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.WINDOW_DELIVERED
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.WINDOW_OPEN
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.failureNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionIntent
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxRepository
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionReceiptOutcome
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionPayload
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionReadRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Единая машина игры вопроса сессии соревнования (истории 5.10 и 5.11): ответ уезжает только
 * из очереди 5.9, квитанция приёмника — единственный источник факта доставки, READY_ACK ставится
 * по доставленному (DELIVERED) окну из сессии квитанции — предусловие серверного гейта, —
 * край окна ведёт перечитывание по серверным часам с нижней границей и backoff.
 * Квалификация и матч играют одной машиной — разнятся только тем, чья сессия открыта.
 *
 * Хозяин машины ([TournamentPlayHost]) владеет состоянием маршрута; машина пишет в него через
 * узкий набор операций и ничего не знает о раундах и событиях вокруг игры.
 */
abstract class TournamentSessionPlayComponent(
    context: ComponentContext,
    protected val repository: TournamentCompetitionRepository,
    protected val outbox: TournamentSessionOutboxRepository,
    protected val sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    protected val nowMs: () -> Long = System::currentTimeMillis,
) : ComponentContext by context {
    /** Хозяин состояния маршрута: машина видит только игру, занятость и честное состояние. */
    protected interface TournamentPlayHost {
        /** Текущая активная игра; пусто — поверхность игры закрыта. */
        fun play(): PlayUiState?

        /** Замена активной игры трансформацией. */
        fun reducePlay(transform: (PlayUiState?) -> PlayUiState?)

        /** Честное состояние обращения (AD-21) — единый словарь маршрута. */
        fun applyNotice(notice: TournamentNotice)

        /** Серверное «сейчас» последнего чтения: только факт, не клиентские часы. */
        fun applyServerNow(serverNowMs: Long?)

        /** Сессия дочитана: хозяин сводит её со своей поверхностью (раунды квалификации, статус матча). */
        fun onSessionMerged(
            label: Int,
            sessionId: String,
            status: String?,
        )

        /** Поколение открытого контекста (событие/матч): смена делает летящие ответы чужими. */
        fun currentGeneration(): Long

        /** Занятость маршрута: одна работа за раз. */
        fun busyNow(): Boolean

        /** Начало работы: хозяин помечает занятость и чью сессию она летит. */
        fun beginWork(session: OutboxSession)

        /** Конец работы: занятость снята. */
        fun endWork()
    }

    /** Хозяин этой машины. */
    protected abstract val host: TournamentPlayHost

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Ответ, чья квитанция ещё не применена: страхует опрос — поздняя доставка доедет по нему. */
    private var pendingAnswer: PendingAnswer? = null

    /** Наблюдатель пометки «не отправлено» на активной сессии (AD-14). */
    private var sessionObserver: Job? = null

    /** Окно последнего перевзвода края (L6): тот же windowId при backlog — удвоение нижней
     * границы; новое окно — сброс. */
    private var rearmWindowId: String? = null
    private var rearmAttempt = 0

    /** Ответ и его маршрут применения: одно применение на квитанцию, кто бы ни приехал первым. */
    private class PendingAnswer(
        val session: OutboxSession,
        val sessionId: String,
        val label: Int,
        val intent: TournamentSessionIntent,
    )

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    /** Выбор строки одиночного выбора (замена) или отметка множественного (переключение). */
    fun onToggleChoice(optionId: String) {
        host.reducePlay { current ->
            val play = current ?: return@reducePlay current
            val payload = play.question?.publicPayload ?: return@reducePlay current
            val selected =
                if (payload.type == TournamentQuestionTypes.MULTIPLE_CHOICE) {
                    if (optionId in play.selectedIds) play.selectedIds - optionId else play.selectedIds + optionId
                } else {
                    listOf(optionId)
                }
            play.copy(selectedIds = selected)
        }
    }

    /** Дописывание порядка: повторный тап по выбранной строке убирает её — полный порядок
     * восстанавливаем перестановкой, а не сбросом всего ответа. */
    fun onOrderTap(optionId: String) {
        host.reducePlay { current ->
            val play = current ?: return@reducePlay current
            val selected =
                if (optionId in play.selectedIds) {
                    play.selectedIds - optionId
                } else {
                    play.selectedIds + optionId
                }
            play.copy(selectedIds = selected)
        }
    }

    /** Правка текста пропуска по индексу. */
    fun onBlankChange(
        index: Int,
        value: String,
    ) {
        host.reducePlay { current ->
            val play = current ?: return@reducePlay current
            val blanks = play.blanks.toMutableList()
            while (blanks.size <= index) blanks.add("")
            blanks[index] = value
            play.copy(blanks = blanks)
        }
    }

    /** Отправка собранного ответа в очередь 5.9. */
    fun onSubmitAnswerClick() {
        val play = host.play() ?: return
        val question = play.question ?: return
        if (host.busyNow() || play.awaitingDelivery || play.settled || play.timedOut) return
        val session = requireSession() ?: return
        val answerPayload = buildAnswerPayload(question.publicPayload, play) ?: return
        val intent =
            TournamentSessionIntent.Answer(
                sessionId = play.sessionId,
                questionIndex = play.questionIndex,
                questionId = question.questionId ?: return,
                answerPayload = answerPayload,
            )
        val label = play.roundNumber
        val pending =
            PendingAnswer(
                session = session,
                sessionId = play.sessionId,
                label = label,
                intent = intent,
            )
        pendingAnswer = pending
        act(session) {
            host.applyNotice(TournamentNotice.Ready)
            host.reducePlay { it?.copy(awaitingDelivery = true) }
            outbox
                .enqueue(intent)
                .getOrElse { failure -> throw (failure as? Exception ?: IllegalStateException(failure.toString())) }
            observePending(play.sessionId, session)
            val outcome = awaitReceipt(intent, session.ownerUid)
            applyAnswerOutcome(pending, outcome)
        }
    }

    /** Перечитывание сессии: восстановление после «неизвестно» без выдумывания времени. */
    fun onRecheckClick() {
        val play = host.play() ?: return
        if (host.busyNow()) return
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
            host.applyNotice(TournamentNotice.Pending)
            return
        }
        // Применение одно: квитанция из опроса и поздняя квитанция не могут задвоить READY_ACK.
        if (pendingAnswer != pending) return
        pendingAnswer = null
        val session = pending.session
        when (outcome) {
            is TournamentSessionReceiptOutcome.Acknowledged -> {
                val view = outcome.receipt.view
                host.reducePlay { it?.copy(lastStatus = view.status, awaitingDelivery = false) }
                // L1: окно операции живёт вложенным в `session` (серверный `responseFor` кладёт
                // его туда; top-level окна у операций не бывает). ACK гейтится по DELIVERED —
                // ровно серверное предусловие READY_ACK (boundary.state=WINDOW_DELIVERED):
                // доставка клиентской операцией и есть delivery; OPEN (недоставленное) окно
                // ACK не порождает.
                val window = view.window ?: view.session?.window
                if (window != null && window.state == WINDOW_DELIVERED) {
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
                        host.reducePlay { it?.copy(readyAckWindowId = ack.windowId) }
                    }
                }
                // Опоздавший ответ честен дважды: пока сервер не подвёл вопрос, метка «время вышло»
                // держится; подвёл — экран показывает то, где сервер, без выдуманного счёта.
                val timedOutQuestion = pending.intent.questionIndex.takeIf { view.status == SESSION_QUESTION_TIMED_OUT }
                readSessionInto(session, pending.sessionId, pending.label, preserveTimedOutFor = timedOutQuestion)
            }
            TournamentSessionReceiptOutcome.Invalid -> {
                // Квитанция не признана: «неизвестно» не становится фактом — перечитывание у игрока.
                host.reducePlay { it?.copy(awaitingDelivery = false) }
                host.applyNotice(TournamentNotice.Retry)
            }
        }
    }

    protected suspend fun readSessionInto(
        session: OutboxSession,
        sessionId: String,
        label: Int,
        preserveTimedOutFor: Int? = null,
    ) {
        val generation = host.currentGeneration()
        val view = repository.readSession(TournamentSessionReadRequest(session.ownerUid, sessionId)).getOrThrow()
        val current = view.session
        if (current == null) {
            host.applyNotice(TournamentNotice.Retry)
            return
        }
        // Контекст закрыли или сменили, пока чтение летело: чужой ответ не применяется.
        if (generation != host.currentGeneration() || sessions.current() != session) return
        observePending(sessionId, session)
        val window = view.window ?: current.window
        // Край окна вернул тот же вопрос: собранный ответ игрока не стирается.
        val previous = host.play()?.takeIf { it.sessionId == sessionId }
        val sameQuestion =
            previous != null &&
                previous.question?.questionId != null &&
                previous.question?.questionId == view.currentQuestion?.questionId
        // Ниже — четыре записи хозяину (serverNow, сведение, игра, состояние) одним соседством:
        // между ними нет ни одной точки подвески, поэтому на одном потоке диспетчера между ними
        // не может войти другой coroutine (наблюдатель, край окна) и увидеть середину чтения.
        host.applyServerNow(view.serverNowMs)
        host.onSessionMerged(label, sessionId, current.status)
        host.reducePlay { existing ->
            PlayUiState(
                sessionId = sessionId,
                roundNumber = current.roundNumber ?: label,
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
                    existing
                        ?.takeIf { it.sessionId == sessionId }
                        ?.readyAckWindowId,
                selectedIds = if (sameQuestion) previous.selectedIds else emptyList(),
                blanks =
                    if (sameQuestion) {
                        previous.blanks
                    } else {
                        List(view.currentQuestion?.publicPayload?.blanks?.size ?: 0) { "" }
                    },
            )
        }
        host.applyNotice(TournamentNotice.Ready)
        // Окно открыто: следующий вопрос приезжает его краем — перечитываем по серверному
        // expiresAtMs. L6: device-часы — только источник offset от serverNowMs последнего
        // чтения (сместились/спешат — краем не становятся); нижняя граница не даёт петли
        // zero-delay read при sweep backlog, а повторный перевзвод того же окна удваивает
        // её (cap 60s); смена окна (уникальный windowId) счётчик сбрасывает.
        if (window?.state == WINDOW_OPEN) {
            if (rearmWindowId == window.windowId) rearmAttempt += 1 else rearmWindowId = window.windowId
            val floorMs =
                (WINDOW_REARM_FLOOR_MS shl rearmAttempt.coerceAtMost(REARM_BACKOFF_STEPS))
                    .coerceAtMost(WINDOW_REARM_CAP_MS)
            val readAtMs = nowMs()
            val serverOffsetMs = view.serverNowMs?.let { serverNow -> readAtMs - serverNow }
            val serverNowEstimateMs = readAtMs - (serverOffsetMs ?: 0L)
            val waitMs =
                (window.expiresAtMs - serverNowEstimateMs)
                    .coerceAtLeast(floorMs)
                    .coerceAtMost(WINDOW_REARM_CAP_MS)
            scope.launch {
                delay(waitMs)
                if (scope.isActive && host.play()?.sessionId == sessionId) {
                    // Край через act: занятость уважается — перечитывание не встаёт посреди
                    // доставки ответа (доставшееся применение само перечитается и перевзведёт
                    // край), а неудавшийся край читается честным состоянием, не тишиной.
                    act(session) { readSessionInto(session, sessionId, label) }
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
                        host.applyNotice(TournamentNotice.AccountChanged)
                        // Смена аккаунта кончает наблюдение: его нельзя оставить собирать дальше.
                        coroutineContext.cancel()
                        return@collect
                    }
                    val awaiting = sync is OutboxEntitySync.Queued
                    host.reducePlay { current -> current?.copy(awaitingDelivery = awaiting) }
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

    /** Наблюдение активной сессии гасится вместе с поверхностью игры (закрытие матча/события). */
    protected fun stopSessionObservation() {
        sessionObserver?.cancel()
        sessionObserver = null
    }

    /** Смена контекста (событие/матч): ответ, летевший по прежнему контексту, применён не будет. */
    protected fun dropPendingAnswer() {
        pendingAnswer = null
    }

    protected fun requireSession(): OutboxSession? {
        val session = sessions.current()
        if (session == null) host.applyNotice(TournamentNotice.AccountUnavailable)
        return session
    }

    protected fun act(
        session: OutboxSession,
        work: suspend () -> Unit,
    ) {
        if (host.busyNow()) return
        host.beginWork(session)
        scope.launch {
            try {
                work()
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                host.applyNotice(
                    if (sessions.current() == session) {
                        failureNotice(
                            failure,
                        )
                    } else {
                        TournamentNotice.AccountChanged
                    },
                )
            } finally {
                host.endWork()
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

    private companion object {
        const val RECEIPT_POLLS = 100
        const val RECEIPT_POLL_MS = 100L

        /** Нижняя граница перевзвода края (L6): sweep backlog не строит петлю zero-delay read. */
        const val WINDOW_REARM_FLOOR_MS = 1_000L

        /** Потолок перевзвода: окно короче 60s не бывает, дольше ждать смысла нет. */
        const val WINDOW_REARM_CAP_MS = 60_000L

        /** Удвоений нижней границы до потолка: 1s × 2^6 = 64s > cap. */
        const val REARM_BACKOFF_STEPS = 6
    }
}
