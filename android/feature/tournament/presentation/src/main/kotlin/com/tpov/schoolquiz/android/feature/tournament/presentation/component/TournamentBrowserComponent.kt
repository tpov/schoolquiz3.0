package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import kotlinx.coroutines.flow.StateFlow

/**
 * Маршрут просмотра (история 5.10): страница авторизованного просмотра listEvents с курсором и
 * чтение события readEvent (прогресс, матчи, опубликованный рейтинг). Знание eventId допуском не
 * является — вход решает отдельный маршрут допуска.
 */
interface TournamentBrowserComponent {
    val state: StateFlow<BrowserUiState>

    /** Первая страница просмотра или её перечитывание; контекст курсора сбрасывается осознанно. */
    fun onRefreshClick()

    /** Следующая страница под тем же курсором. */
    fun onLoadMoreClick()

    /** Открытие детали события: полное чтение с рейтингом и эскроу. */
    fun onOpenEvent(eventId: String)

    /** Закрытие детали. */
    fun onCloseEvent()
}

data class BrowserUiState(
    val session: OutboxSession? = null,
    val events: List<TournamentEventView> = emptyList(),
    val canLoadMore: Boolean = false,
    val hasLoaded: Boolean = false,
    val detail: TournamentEventRead? = null,
    val busy: Boolean = false,
    val notice: TournamentNotice = TournamentNotice.NotLoaded,
)
