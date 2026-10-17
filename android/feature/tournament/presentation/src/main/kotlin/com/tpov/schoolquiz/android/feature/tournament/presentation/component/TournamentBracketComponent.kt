package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import kotlinx.coroutines.flow.StateFlow

/**
 * Сетка чемпионата (история 5.11): опубликованное состояние сетки — раунды и матчи чтения
 * события (BRACKET/FINISHED несут прогресс и матчи) и итоговые места у завершённого события.
 * Сетка неизменна: повторные чтения возвращают одну и ту же публикацию, места заморожены
 * сервером; согласование слота открывается отсюда — экран только называет матчи и исходы.
 */
interface TournamentBracketComponent {
    val state: StateFlow<BracketUiState>

    /** Первая страница событий или её перечитывание; контекст курсора сбрасывается осознанно. */
    fun onRefreshClick()

    /** Следующая страница под тем же курсором. */
    fun onLoadMoreClick()

    /** Открытие сетки события: полное чтение с прогрессом и матчами. */
    fun onOpenEvent(eventId: String)

    /** Закрытие сетки события. */
    fun onCloseEvent()
}

data class BracketUiState(
    val session: OutboxSession? = null,
    val events: List<TournamentEventView> = emptyList(),
    val canLoadMore: Boolean = false,
    val hasLoaded: Boolean = false,
    val detail: TournamentEventRead? = null,
    val busy: Boolean = false,
    val notice: TournamentNotice = TournamentNotice.NotLoaded,
)
