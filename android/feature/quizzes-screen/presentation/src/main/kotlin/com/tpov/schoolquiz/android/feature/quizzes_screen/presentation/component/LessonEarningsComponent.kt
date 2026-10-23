package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HistoryFilter
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.OwnHistoryUiState
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveOwnHistoryUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.TimeZone

interface LessonEarningsComponent {
    val state: StateFlow<OwnHistoryUiState>

    /** Часовой пояс группировки — один и тот же в группах и в заголовках дня (UX-20). */
    val timeZone: () -> TimeZone

    /** Урок, чьи «Начисления» читаются (AD-30: сервер проверяет account/lesson права). */
    val lessonId: String

    val title: String

    fun onFilterChanged(filter: HistoryFilter)

    fun onLoadMoreClick()

    fun onRetryClick()
}

/**
 * «Начисления» авторского урока (AD-30/UX-20, история 8.6) — тот же компонент разрешённой
 * проекции, что и общая история, в lesson-scoped режиме ([OwnHistoryComponent] c lessonId).
 * Чтение эффекта не создаёт; не-автор получает не-отличимый от отсутствия отказ.
 */
class DefaultLessonEarningsComponent(
    componentContext: ComponentContext,
    override val lessonId: String,
    override val title: String,
    observeOwnHistory: ObserveOwnHistoryUseCase,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
    override val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : LessonEarningsComponent, ComponentContext by componentContext {
    private val delegate =
        DefaultOwnHistoryComponent(
            componentContext = componentContext,
            observeOwnHistory = observeOwnHistory,
            lessonId = lessonId,
            mainContext = mainContext,
            timeZone = timeZone,
        )

    override val state: StateFlow<OwnHistoryUiState> = delegate.state

    override fun onFilterChanged(filter: HistoryFilter) = delegate.onFilterChanged(filter)

    override fun onLoadMoreClick() = delegate.onLoadMoreClick()

    override fun onRetryClick() = delegate.onRetryClick()
}

/** Отдельная фабрика: другие SAM-фабрики квизов не меняются. */
fun interface LessonEarningsComponentFactory {
    fun create(
        componentContext: ComponentContext,
        lessonId: String,
        title: String,
    ): LessonEarningsComponent
}
