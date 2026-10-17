package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingPage
import kotlinx.coroutines.flow.StateFlow

/**
 * Согласование слота матча (история 5.11): чтение встречи 5.7 — предложения, подтверждения и
 * готовность поимённо каждым участником, границы стадии C и слота; намерения PROPOSE/CONFIRM/READY
 * уезжают прямыми операциями сервера, а состояние после любого исхода перечитывается — на каждом
 * повторном входе оба участника видят одни и те же серверные факты, никаких локальных черновиков.
 * Слот после согласия неизменен, матч клиент не начинает — стартует сервер.
 */
interface TournamentAgreementComponent {
    val state: StateFlow<AgreementUiState>

    /**
     * Открытие матча: встреча строится чтением — тем же путём, что и повторный вход.
     * Возвращает, принял ли компонент открытие: при занятости или без сессии открытие
     * не случилось, и оверлей над сеткой поднимать нельзя.
     */
    fun onOpenMatch(
        eventId: String,
        matchId: String,
    ): Boolean

    /** Закрытие матча. */
    fun onCloseMatch()

    /** Дочитка предложений и подтверждений по курсорам страницы. */
    fun onLoadMoreClick()

    /** Ввод слота (эпоха, мс): только ввод игрока, границу считает сервер. */
    fun onSlotStartsAtChange(value: String)

    /** Предложение введённого слота. */
    fun onProposeClick()

    /** Подтверждение слота по строке предложения: строка называет свой слот, ввод не подменяет её. */
    fun onConfirmClick(slotStartsAtMs: Long)

    /** Готовность: сервер принимает её только внутри согласованного окна. */
    fun onReadyClick()
}

data class AgreementUiState(
    val session: OutboxSession? = null,
    val eventId: String? = null,
    val matchId: String? = null,
    val meeting: TournamentMeetingPage? = null,
    val slotInput: String = "",
    /** Своя сессия матча, когда сервер её уже создал; пусто — поверхности игры нет. */
    val playSessionId: String? = null,
    val busy: Boolean = false,
    val notice: TournamentNotice = TournamentNotice.NotLoaded,
) {
    /** Оценённая встреча — серверный факт страницы. */
    val meetingView get() = meeting?.meeting
}
