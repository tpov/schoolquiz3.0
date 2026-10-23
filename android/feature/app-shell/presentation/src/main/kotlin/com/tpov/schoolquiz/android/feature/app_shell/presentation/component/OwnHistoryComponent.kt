package com.tpov.schoolquiz.android.feature.app_shell.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.app_shell.presentation.uistate.HistoryFilter
import com.tpov.schoolquiz.android.feature.app_shell.presentation.uistate.OwnHistoryUiState
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.OwnHistoryRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveOwnHistoryUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime

interface OwnHistoryComponent {
    val state: StateFlow<OwnHistoryUiState>

    fun onFilterChanged(filter: HistoryFilter)

    fun onLoadMoreClick()

    fun onRetryClick()
}

/**
 * Экран «История операций» (UX-20/AD-30, история 8.6).
 *
 * Чтение — только проекция: никакой записи и никакого денежного эффекта. Владелец берётся
 * use-case'ом из авторизации: смена аккаунта отсекает чужой/поздний ответ, и экран честно
 * перечитывается (AD-30/AD-8). Пагинация ограниченными страницами с серверным курсором.
 */
@Suppress("TooManyFunctions")
class DefaultOwnHistoryComponent(
    componentContext: ComponentContext,
    private val observeOwnHistory: ObserveOwnHistoryUseCase,
    private val mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : OwnHistoryComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + mainContext)
    private val _state = MutableStateFlow(OwnHistoryUiState.loading())
    override val state: StateFlow<OwnHistoryUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        refresh()
    }

    override fun onFilterChanged(filter: HistoryFilter) {
        // Фильтр — слово над уже прочитанной страницей, перечитывания он не требует.
        _state.value = _state.value.copy(filter = filter)
    }

    override fun onLoadMoreClick() {
        val cursor = _state.value.nextCursor ?: return
        if (!_state.value.canLoadMore) return
        load(cursor, isLoadMore = true)
    }

    override fun onRetryClick() {
        // Повтор с нулевым курсором: смена владельца между попытками начинается заново (AD-30).
        refresh()
    }

    private fun refresh() {
        load(cursor = null, isLoadMore = false)
    }

    private fun load(
        cursor: String?,
        isLoadMore: Boolean,
    ) {
        loadJob?.cancel()
        loadJob =
            scope.launch {
                _state.value =
                    if (isLoadMore) {
                        _state.value.copy(isLoadingMore = true)
                    } else {
                        OwnHistoryUiState.loading()
                    }
                try {
                    observeOwnHistory(cursor = cursor)
                        .onSuccess { page ->
                            _state.value = OwnHistoryUiState.fromPage(previous(), page, ::dayStartOf)
                        }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            _state.value = OwnHistoryUiState.error()
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _state.value = OwnHistoryUiState.error()
                }
            }
    }

    /** База слияния: при догрузке существующие дни остаются, при обновлении страница ложится на пусто. */
    private fun previous(): OwnHistoryUiState =
        _state.value.takeIf { it.isLoadingMore } ?: OwnHistoryUiState(filter = _state.value.filter)

    private fun dayStartOf(atMs: Long): Long {
        val zone = timeZone()
        val instant = Instant.fromEpochMilliseconds(atMs)
        return instant.toLocalDateTime(zone).date.atStartOfDayIn(zone).toEpochMilliseconds()
    }
}

/** Пустая поверхность на случай, если Koin-биндинг не собрался — экран остаётся читаемым. */
class PlaceholderOwnHistoryComponent : OwnHistoryComponent {
    override val state: StateFlow<OwnHistoryUiState> =
        MutableStateFlow(OwnHistoryUiState(isLoading = false, isEndReached = true))

    override fun onFilterChanged(filter: HistoryFilter) = Unit

    override fun onLoadMoreClick() = Unit

    override fun onRetryClick() = Unit
}
