package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import kotlinx.coroutines.flow.StateFlow

/**
 * Итоги чемпионата (история 5.11): итоговые места и правда эскроу завершённого события — всё
 * чтением события. Квитанции о выплатах остаются серверным фактом: клиент показывает эскроу
 * (зарезервировано, осталось, выплачено, возвращено) и места, а не выдуманный список денег.
 */
interface TournamentResultsComponent {
    val state: StateFlow<ResultsUiState>

    /**
     * Открытие итогов события чтением; повторное чтение возвращает ту же публикацию.
     * Возвращает, принял ли компонент открытие: при занятости или без сессии — нет.
     */
    fun onOpenEvent(eventId: String): Boolean

    /** Закрытие итогов. */
    fun onClose()
}

data class ResultsUiState(
    val session: OutboxSession? = null,
    val eventId: String? = null,
    val detail: TournamentEventRead? = null,
    val busy: Boolean = false,
    val notice: TournamentNotice = TournamentNotice.NotLoaded,
)
