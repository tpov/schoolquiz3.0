package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowView
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead

/**
 * Онлайн-переход next-question арены по общему контракту ONLINE-O2 (AD-31; история 3.2
 * эпика `epic-arena-completion`; контракт: `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md`).
 *
 * Что здесь есть и чего здесь нет. Содержимое вопросов аренной попытки уже лежит в замороженном
 * снапшоте тикета — выдачи контента нет и не появляется: офлайн/подготовленные пути эпика 3
 * контрактом не трогаются (§1, §9: «офлайн-сценарии ordinary/Arena не становятся онлайн», готовый
 * вопрос — без сетевого гейта, CAP-1). Адаптер несёт временную половину перехода для онлайн-HARD
 * прохождения: событие завершения вопроса открывает серверное окно с захваченной конфигурацией,
 * выдача ставит исходный deadline следующего вопроса, ready-ACK уезжает через общий outbox, и
 * ни reconnect, ни retry, ни потерянный ACK не заводят вторые часы и не оставляют неограниченной
 * паузы (§3–§7).
 *
 * Правила, ради которых адаптер написан именно так:
 *
 * - **Один источник времени — сервер.** Локальная проекция deadline строится один раз, из
 *   серверного остатка выдачи (§5), и дальше живёт в чекпоинте: рестарт/потеря связи продолжают
 *   хранимый исходный deadline (§7), повторная выдача его не перезапускает и не продлевает.
 * - **Переход едет только через PAUSED.** Пауза между вопросами — существующая фаза рановера
 *   (`AdvanceToPause`/`TimeoutToPause`); возобновление с серверным deadline — легальный
 *   PAUSED→ANSWERING переход той же машины (`validateArenaRunnerTransition`), никаких новых фаз.
 * - **Окно не начинается после последнего вопроса** (§3) — последний вопрос ПОПЫТКИ, то есть
 *   снапшота тикета: серверный пул урока шире и арене не владелец этой границы.
 * - **Identity прежде фактов.** Каждое серверное слово сверяется с попыткой (AD-6/§6): чужое окно,
 *   чужая позиция или чужой вопрос не читаются и не меняют локальное состояние.
 */
object ArenaOnlineO2Adapter {

    /**
     * Запрос окна перехода из [completedQuestionIndex], или `null` — окна нет.
     *
     * `null` — ровно одна граница: закрыт последний вопрос попытки (§3: после последнего вопроса
     * окно не начинается, лишних 60 секунд нет). Отрицательный индекс — расхождение вызывающего
     * с рановером, а не «нет окна»: ошибка, не вежливый отказ.
     */
    fun windowRequestOrNull(
        binding: ArenaRunnerCheckpointBinding,
        completedQuestionIndex: Int,
    ): QuestionWindowRequest? {
        require(completedQuestionIndex >= 0) { "completedQuestionIndex must be non-negative" }
        val questions = binding.snapshot.questions
        if (completedQuestionIndex >= questions.size - 1) return null
        val h = binding.snapshot.header
        return QuestionWindowRequest(
            attemptId = h.attemptId,
            lessonId = h.lessonId,
            lessonVersion = h.lessonVersion,
            completedQuestionIndex = completedQuestionIndex,
            nextQuestionId = questions[completedQuestionIndex + 1].questionId,
            isHard = h.difficulty == Difficulty.HARD,
        )
    }

    /**
     * Identity-сверка открытого окна с попыткой; возвращает то же окно или бросает.
     *
     * Несходное окно — расхождение клиента с сервером (AD-6): оно не читается дальше и не
     * порождает ни чекпоинта, ни ready-ACK. Захваченная конфигурация проверяется на осмысленность
     * здесь: положительные секунды и непустой срок — всё, что клиент вправе требовать; значения
     * сервер не перечитывает при выдаче (§3), поэтому сверять их с локальной формулой — значило бы
     * назначать серверные факты на клиенте (§5).
     */
    fun requireWindowIdentity(
        binding: ArenaRunnerCheckpointBinding,
        completedQuestionIndex: Int,
        view: QuestionWindowView,
    ): QuestionWindowView {
        require(completedQuestionIndex >= 0) { "completedQuestionIndex must be non-negative" }
        val h = binding.snapshot.header
        require(view.attemptId == h.attemptId) { "window attempt ${view.attemptId} is not the attempt ${h.attemptId}" }
        require(view.completedQuestionIndex == completedQuestionIndex) {
            "window names completed index ${view.completedQuestionIndex}, expected $completedQuestionIndex"
        }
        require(view.nextQuestionIndex == completedQuestionIndex + 1) {
            "window names next index ${view.nextQuestionIndex}, expected ${completedQuestionIndex + 1}"
        }
        require(view.windowId.isNotBlank()) { "windowId must not be blank" }
        require(view.windowSeconds > 0) { "captured windowSeconds must be positive" }
        require(view.allocatedSeconds > 0) { "captured allocatedSeconds must be positive" }
        require(view.openedAtMs >= 0 && view.expiresAtMs > view.openedAtMs) { "window term is not a term" }
        require(view.state.isNotBlank()) { "window state must not be blank" }
        return view
    }

    /**
     * Возобновление выданного вопроса: PAUSED→ANSWERING с серверным исходным deadline (§3, §5).
     *
     * Чекпоинт обязан стоять на паузе ровно на выданном слоте — пауза и есть «между вопросами»
     * существующего рановера. Deadline слота — локальная проекция серверного остатка, построенная
     * один раз на момент получения ответа сервера; `questionStartedAtMs` не трогается (та же
     * позиция). Дальше срок живёт в чекпоинте: рестарт и потеря связи продолжают его (§7), и ни
     * повтор выдачи, ни повторный вызов этого метода часы заново не ставят — для уже идущего
     * вопроса есть [reconfirmDelivered].
     *
     * Выданный вопрос обязан быть вопросом снапшота на следующей позиции: сервер подтверждает
     * выдачу конкретного id, и расхождение — не «другой вопрос», а чужой переход (AD-21: клиент не
     * назначает серверные факты; здесь и обратно — сервер не перевыгает пул попытки).
     */
    fun resumeDelivered(
        current: ArenaRunRead.Continuable,
        completedQuestionIndex: Int,
        view: QuestionWindowView,
        verdict: QuestionDeliveryVerdict.Delivered,
        localNowMs: Long,
    ): ArenaRunnerCheckpoint {
        requireWindowIdentity(binding(current), completedQuestionIndex, view)
        requireSameWindow(view, verdict)
        val checkpoint = current.checkpoint
        require(checkpoint.phase == ArenaRunnerPhase.PAUSED) {
            "delivered question resumes from the between-questions pause, not from ${checkpoint.phase}"
        }
        require(checkpoint.indexInPool == completedQuestionIndex + 1) {
            "paused at ${checkpoint.indexInPool}, delivered position is ${completedQuestionIndex + 1}"
        }
        val delivery = verdict.delivery
        val next = completedQuestionIndex + 1
        require(delivery.questionIndex == next) { "delivery names position ${delivery.questionIndex}, expected $next" }
        require(delivery.questionId == binding(current).snapshot.questions[next].questionId) {
            "delivered question ${delivery.questionId} is not the attempt's question at position $next"
        }
        require(verdict.remainderMs >= 0 && verdict.serverNowMs >= 0) { "verdict times must be non-negative" }
        require(verdict.remainderMs <= delivery.answerDeadlineMs) { "remainder must not exceed the deadline" }
        require(localNowMs >= 0 && verdict.remainderMs <= Long.MAX_VALUE - localNowMs) { "projection overflows" }
        val candidate =
            checkpoint.copy(
                phase = ArenaRunnerPhase.ANSWERING,
                deadlineMs = localNowMs + verdict.remainderMs,
            )
        validateArenaRunnerTransition(checkpoint, candidate, binding(current))
        return candidate
    }

    /**
     * Повторная выдача при уже идущих часах вопроса (потерянный ответ, reconnect): хранимый
     * исходный deadline не перезапускается и не продлевается (§7, §5 — «время ответа не
     * запускается повторно»). Сервер отвечает тем же вопросом и тем же deadline; локальная
     * проекция уже стоит в чекпоинте и остаётся ровно той же — кандидат совпадает с текущим
     * состоянием байт-в-байт. Чужой вопрос или чужое окно не читаются (AD-6).
     */
    fun reconfirmDelivered(
        current: ArenaRunRead.Continuable,
        completedQuestionIndex: Int,
        view: QuestionWindowView,
        verdict: QuestionDeliveryVerdict.Delivered,
    ): ArenaRunnerCheckpoint {
        requireWindowIdentity(binding(current), completedQuestionIndex, view)
        requireSameWindow(view, verdict)
        val checkpoint = current.checkpoint
        require(checkpoint.phase == ArenaRunnerPhase.ANSWERING) {
            "a re-issued delivery reconfirms a running question, not ${checkpoint.phase}"
        }
        require(checkpoint.indexInPool == completedQuestionIndex + 1) {
            "answering ${checkpoint.indexInPool}, reconfirmed position is ${completedQuestionIndex + 1}"
        }
        val delivery = verdict.delivery
        val next = completedQuestionIndex + 1
        require(delivery.questionIndex == next) { "delivery names position ${delivery.questionIndex}, expected $next" }
        require(delivery.questionId == binding(current).snapshot.questions[next].questionId) {
            "re-delivered question ${delivery.questionId} is not the attempt's question at position $next"
        }
        return checkpoint
    }

    /**
     * Ветвь истёкшего окна: решение о закрытии, а не чекпоинт (§7).
     *
     * Окно истекло без выдачи — режимный владелец завершает попытку по своему контракту; на
     * клиенте это существующие режимные пути закрытия арены (позднее завершение/отмена), и
     * адаптер не выдумывает для них новую машину. Решение фиксирует, что готового ready-ACK из
     * этого перехода не возникает, часы не заводятся, повтор запроса выдачи остаётся стабильно
     * истёкшим (§7: без бесконечного gap и без победы таймауту).
     *
     * Бросает, если попытка уже отвечает на выданный вопрос: истекшее окно означает, что выдачи
     * не было, а значит слот не мог начаться (§3: пока вопрос активен, окно не обрезает его
     * время) — такое сочетание противоречиво и не читается.
     */
    fun requireExpiryConsistent(
        current: ArenaRunRead.Continuable,
        completedQuestionIndex: Int,
        view: QuestionWindowView?,
        expired: QuestionDeliveryVerdict.WindowExpired,
    ): ArenaOnlineO2ExpiryDecision {
        require(completedQuestionIndex >= 0) { "completedQuestionIndex must be non-negative" }
        val checkpoint = current.checkpoint
        require(checkpoint.phase != ArenaRunnerPhase.ANSWERING || checkpoint.indexInPool != completedQuestionIndex + 1) {
            "expired window cannot close a question that is already answering"
        }
        view?.let { requireWindowIdentity(binding(current), completedQuestionIndex, it) }
        expired.windowId?.let { id -> require(id.isNotBlank()) { "expired windowId must not be blank" } }
        require(expired.closedAtMs >= 0 && expired.serverNowMs >= 0) { "expiry times must be non-negative" }
        return ArenaOnlineO2ExpiryDecision(
            attemptId = binding(current).snapshot.header.attemptId,
            completedQuestionIndex = completedQuestionIndex,
            windowId = expired.windowId,
            closedAtMs = expired.closedAtMs,
        )
    }

    private fun requireSameWindow(
        view: QuestionWindowView,
        verdict: QuestionDeliveryVerdict.Delivered,
    ) {
        require(verdict.windowId == view.windowId) { "delivery window ${verdict.windowId} is not the opened ${view.windowId}" }
    }

    private fun binding(current: ArenaRunRead.Continuable): ArenaRunnerCheckpointBinding = current.binding
}

/**
 * Решение о закрытии попытки по истёкшему окну (§7): готового вопроса нет, ready-ACK не
 * возникает, часы следующего вопроса не заводились. Закрытие — существующими режимными путями
 * арены; попытка не получает ни победы таймауту, ни новой платной попытки.
 */
data class ArenaOnlineO2ExpiryDecision(
    val attemptId: String,
    val completedQuestionIndex: Int,
    val windowId: String?,
    val closedAtMs: Long,
)
