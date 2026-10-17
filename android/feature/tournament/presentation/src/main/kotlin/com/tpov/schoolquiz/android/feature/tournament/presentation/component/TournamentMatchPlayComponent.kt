package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import kotlinx.coroutines.flow.StateFlow

/**
 * Игра матча (история 5.11): та же машина сессии, что и квалификация (история 5.10), на сессии
 * вида MATCH — ответ едет только из очереди 5.9, READY_ACK ставится после доставки в открытое
 * окно, край окна ведёт перечитывание. Матч клиент не начинает: сессию создаёт сервер, когда
 * оба участника готовы, и её идентичность приезжает из ответа операции согласования.
 */
interface TournamentMatchPlayComponent {
    val state: StateFlow<MatchPlayUiState>

    /**
     * Открытие своей сессии матча: чтение сессии строит игру, как и любой повторный вход.
     * Возвращает, принял ли компонент открытие: при занятости или без сессии — нет.
     */
    fun onOpenMatchSession(
        eventId: String,
        matchId: String,
        sessionId: String,
    ): Boolean

    /** Закрытие поверхности игры матча. */
    fun onCloseMatchSession()

    /** Выбор строки одиночного выбора (замена) или отметка множественного (переключение). */
    fun onToggleChoice(optionId: String)

    /** Дописывание порядка: строка становится следующей в предложенном порядке. */
    fun onOrderTap(optionId: String)

    /** Правка текста пропуска по индексу. */
    fun onBlankChange(
        index: Int,
        value: String,
    )

    /** Отправка собранного ответа в очередь 5.9. */
    fun onSubmitAnswerClick()

    /** Перечитывание сессии: восстановление после «неизвестно» без выдумывания времени. */
    fun onRecheckClick()
}

data class MatchPlayUiState(
    val session: OutboxSession? = null,
    val eventId: String? = null,
    val matchId: String? = null,
    /** Своя сессия матча (`m_…`); пуста — поверхность игры закрыта. */
    val sessionId: String? = null,
    val play: PlayUiState? = null,
    val serverNowMs: Long? = null,
    val busy: Boolean = false,
    val notice: TournamentNotice = TournamentNotice.NotLoaded,
)
