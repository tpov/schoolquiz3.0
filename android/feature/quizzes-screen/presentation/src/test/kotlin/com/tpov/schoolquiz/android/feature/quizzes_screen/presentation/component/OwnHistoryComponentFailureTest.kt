package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HistoryFilter
import com.tpov.schoolquiz.shared.feature.economy.domain.model.OperationHistoryRecord
import com.tpov.schoolquiz.shared.feature.economy.domain.model.OwnHistoryPage
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.OwnHistoryRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveOwnHistoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Неудача чтения НЕ стирает прочитанное (AD-21/UX-20, история 8.6): группы и курсор остаются,
 * ошибка поднимается флагом на месте, продолжение остаётся возможным.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnHistoryComponentFailureTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    private val record = OperationHistoryRecord(
        id = "row-1", atMs = 1_000L, reason = "attempt_reward",
        currency = "nolics", delta = 12, status = "CONFIRMED", lessonId = null,
    )

    private class FakeRepository(
        private val pages: MutableList<Result<OwnHistoryPage>>,
    ) : OwnHistoryRepository {
        override suspend fun historyPage(
            ownerUid: String,
            cursor: String?,
            pageSize: Int,
        ): Result<OwnHistoryPage> = pages.removeAt(0)

        override suspend fun lessonEarningsPage(
            ownerUid: String,
            lessonId: String,
            cursor: String?,
            pageSize: Int,
        ): Result<OwnHistoryPage> = historyPage(ownerUid, cursor, pageSize)
    }

    private fun page(entries: List<OperationHistoryRecord>, nextCursor: String?) =
        Result.success(OwnHistoryPage(entries = entries, pending = emptyList(), nextCursor = nextCursor))

    @Test
    fun loadMoreFailure_keepsGroupsAndCursor_andLiftsErrorInPlace() = runTest(dispatcher) {
        val pages = mutableListOf(
            page(listOf(record), nextCursor = "cursor-1"),
            Result.failure<OwnHistoryPage>(IllegalStateException("transport")),
        )
        val repository = FakeRepository(pages)
        lifecycle = LifecycleRegistry().apply { resume() }
        val component =
            DefaultOwnHistoryComponent(
                componentContext = DefaultComponentContext(lifecycle),
                observeOwnHistory = ObserveOwnHistoryUseCase(repository, currentUid = { "uid-1" }),
                mainContext = dispatcher,
                timeZone = { TimeZone.UTC },
            )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, component.state.value.groups.size, "первая страница прочитана")
        assertFalse(component.state.value.errorMessage)

        // Догрузка за курсором отказывает: группы и курсор остаются, ошибка — флаг на месте.
        component.onLoadMoreClick()
        dispatcher.scheduler.advanceUntilIdle()

        val state = component.state.value
        assertTrue(state.errorMessage, "неудача поднята флагом")
        assertFalse(state.isLoadingMore)
        assertEquals("cursor-1", state.nextCursor, "курсор сохранён — продолжение остаётся возможным")
        assertEquals(1, state.groups.size, "прочитанные группы не стёрты")
        assertEquals(HistoryFilter.ALL, state.filter)

        // Повтор после отказа продолжает с сохранённого курсора.
        pages += page(listOf(record.copy(id = "row-2", atMs = 2_000)), nextCursor = null)
        component.onLoadMoreClick()
        dispatcher.scheduler.advanceUntilIdle()

        val recovered = component.state.value
        assertFalse(recovered.errorMessage, "после удачной догрузки ошибка снята")
        // Обе записи одного дня (UTC) — одна группа, две записи: догрузка дошилась в неё.
        assertEquals(1, recovered.groups.size)
        assertEquals(2, recovered.groups.single().entries.size)
        assertEquals(null, recovered.nextCursor)
    }
}
