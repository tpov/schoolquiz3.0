package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_COMPLETED
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.SESSION_QUESTION_TIMED_OUT
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestion
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionResult
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentWindowView
import kotlinx.coroutines.flow.StateFlow

/**
 * Маршрут участия (история 5.10): выбор события, три раунда квалификации, START_QUALIFICATION под
 * раунд (токен ссылки предъявляется здесь), игра вопроса с AD-31 — отсчёт окна по expiresAtMs и
 * READY_ACK после доставки ответа из очереди 5.9. Статусы раундов читаются из `sessions[]`.
 */
interface TournamentQualificationComponent {
    val state: StateFlow<QualificationUiState>

    /** Первая страница выбора события. */
    fun onRefreshEventsClick()

    /** Следующая страница выбора события. */
    fun onLoadMoreEventsClick()

    /** Выбор события участия. */
    fun onPickEvent(eventId: String)

    /** Закрытие события участия. */
    fun onCloseEvent()

    /** Ввод токена ссылки организатора (out-of-band; предъявляется только старту). */
    fun onLinkTokenChange(value: String)

    /** Старт раунда: тот же attemptId/operationId при любой повторной попытке. */
    fun onStartRoundClick(roundNumber: Int)

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

data class QualificationUiState(
    val session: OutboxSession? = null,
    val events: List<TournamentEventView> = emptyList(),
    val canLoadMore: Boolean = false,
    val hasLoaded: Boolean = false,
    val event: TournamentEventView? = null,
    val serverNowMs: Long? = null,
    val rounds: List<RoundProgress> = emptyList(),
    val linkToken: String = "",
    val play: PlayUiState? = null,
    val busy: Boolean = false,
    val notice: TournamentNotice = TournamentNotice.NotLoaded,
)

/** Статус одного раунда: сессия и её серверный статус; не начатый раунд честно пуст. */
data class RoundProgress(
    val roundNumber: Int,
    val sessionId: String?,
    val status: String?,
)

/** Активная игра вопроса: серверный вопрос, окно AD-31 и собранный ответ игрока. */
data class PlayUiState(
    val sessionId: String,
    val roundNumber: Int,
    val question: TournamentPublicQuestion?,
    val questionIndex: Int,
    val window: TournamentWindowView?,
    val selectedIds: List<String> = emptyList(),
    val blanks: List<String> = emptyList(),
    val awaitingDelivery: Boolean = false,
    val lastStatus: String? = null,
    val readyAckWindowId: String? = null,
    val result: TournamentSessionResult? = null,
) {
    val settled: Boolean get() = result != null || lastStatus == SESSION_COMPLETED

    val timedOut: Boolean get() = lastStatus == SESSION_QUESTION_TIMED_OUT

    /** Ответ собирается только из строк публичного вопроса. */
    val optionIds: List<String>
        get() =
            question?.publicPayload?.options.orEmpty().map { it.id } +
                question?.publicPayload?.items.orEmpty().map { it.id } +
                question?.publicPayload?.candidates.orEmpty().map { it.id }
}
