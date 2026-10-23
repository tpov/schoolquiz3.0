package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

import com.tpov.schoolquiz.shared.feature.economy.domain.model.OperationHistoryRecord
import com.tpov.schoolquiz.shared.feature.economy.domain.model.OwnHistoryPage
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PendingOperation

/** Фильтры Все/Начисления/Расходы (UX-20): знак движения внутри каждой валюты. */
enum class HistoryFilter {
    ALL,
    INCOME,
    EXPENSE,
}

/** Группа записей одного дня: заголовок дня и его строки, порядок — как приехал (новые выше). */
data class OwnHistoryDayGroup(
    /** Начало дня по часам устройства (epoch ms), для заголовка даты. */
    val dayStartMs: Long,
    val entries: List<OperationHistoryRecord>,
)

/**
 * Экран «История операций» (UX-20, AD-30, история 8.6).
 *
 * Состояния различимы (AD-21): загрузка, пусто, ошибка, готово с продолжением. Pending виден и
 * никогда не входит в подтверждённые записи; ресурсы построчно и никогда не суммируются.
 */
data class OwnHistoryUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    /** Сервер назвал конец: продолжения нет. Отличимо от «ещё не загружено» (AD-8). */
    val isEndReached: Boolean = false,
    val errorMessage: Boolean = false,
    val groups: List<OwnHistoryDayGroup> = emptyList(),
    val pending: List<PendingOperation> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val nextCursor: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && !errorMessage && filteredEntries().isEmpty()

    /** Записи после фильтра; группировка сохраняет порядок страницы (новые выше). */
    fun filteredEntries(): List<OperationHistoryRecord> =
        groups
            .asSequence()
            .flatMap { it.entries }
            .filter { record ->
                when (filter) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.INCOME -> record.isIncome
                    HistoryFilter.EXPENSE -> !record.isIncome
                }
            }
            .toList()

    /**
     * Продолжение возможно, когда есть курсор и ничего не летит; поднятыый флаг ошибки ему не
     * мешает — повтор догрузки и есть способ снять ошибку (AD-21: неудача не завершение).
     */
    val canLoadMore: Boolean
        get() = !isLoading && !isLoadingMore && !isEndReached && nextCursor != null

    companion object {
        fun loading(): OwnHistoryUiState = OwnHistoryUiState()

        fun error(): OwnHistoryUiState = OwnHistoryUiState(isLoading = false, errorMessage = true)

        fun fromPage(
            previous: OwnHistoryUiState,
            page: OwnHistoryPage,
            dayStartOf: (Long) -> Long,
        ): OwnHistoryUiState {
            val existing = previous.groups
            val fresh =
                page.entries
                    .groupBy { record -> dayStartOf(record.atMs) }
                    .map { (dayStartMs, entries) -> OwnHistoryDayGroup(dayStartMs, entries) }
                    .sortedByDescending { it.dayStartMs }
            // Дни сливаются по границе страницы: день может начаться на прошлой странице.
            val merged =
                (existing + fresh)
                    .groupBy { it.dayStartMs }
                    .map { (dayStartMs, parts) -> OwnHistoryDayGroup(dayStartMs, parts.flatMap { it.entries }) }
                    .sortedByDescending { it.dayStartMs }
            return OwnHistoryUiState(
                isLoading = false,
                isLoadingMore = false,
                isEndReached = page.nextCursor == null,
                errorMessage = false,
                groups = merged,
                pending = page.pending.ifEmpty { previous.pending },
                filter = previous.filter,
                nextCursor = page.nextCursor,
            )
        }
    }
}
