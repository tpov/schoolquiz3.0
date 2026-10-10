package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxItem
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.InboxRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListInboxItemsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Общий inbox на экране: страницы склеиваются по якорю без повторов, «Обновить» всегда с начала,
 * транспортный отказ — сообщение, а не пустой список (AD-14).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultInboxComponentTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `first page loads entries and load-more appends by anchor`() = runTest(testScheduler) {
        val fakes = FakeRepository()
        val component = buildComponent(fakes)
        val collected = launchCollector(component)

        assertEquals(listOf("report-1"), component.state.value.entries.map { it.id })

        component.onLoadMoreClick()

        assertEquals(listOf("report-1", "comment-1"), component.state.value.entries.map { it.id })
        assertEquals(InboxAnchor(5_000L, "report-1"), fakes.anchors.last())
        assertEquals(false, component.state.value.canLoadMore)
        collected.cancel()
    }

    @Test
    fun `refresh restarts from the beginning without duplicating rows`() = runTest(testScheduler) {
        val fakes = FakeRepository()
        val component = buildComponent(fakes)
        val collected = launchCollector(component)

        component.onLoadMoreClick()
        component.onRefreshClick()

        assertEquals(listOf("report-1"), component.state.value.entries.map { it.id })
        assertNull(fakes.anchors.last())
        collected.cancel()
    }

    @Test
    fun `transport failure becomes an error message, entries stay empty`() = runTest(testScheduler) {
        val fakes = FakeRepository(result = Result.failure(SyncFailure(SyncError.Refused("unavailable"))))
        val component = buildComponent(fakes)
        val collected = launchCollector(component)

        assertTrue(component.state.value.entries.isEmpty())
        assertEquals(true, component.state.value.errorMessage != null)
        collected.cancel()
    }

    private fun launchCollector(component: InboxComponent) =
        kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
            component.state.collect { }
        }

    private fun buildComponent(fakes: FakeRepository): DefaultInboxComponent {
        lifecycle = LifecycleRegistry()
        return DefaultInboxComponent(
            componentContext = DefaultComponentContext(lifecycle),
            authRepository = FakeAuthRepository("uid-a"),
            listInboxItems = ListInboxItemsUseCase(fakes),
        ).also { lifecycle.resume() }
    }

    private class FakeAuthRepository(
        private val uid: String?,
    ) : AuthRepository {
        private val flow = MutableStateFlow(uid)
        override fun observeUid(): Flow<String?> = flow

        override suspend fun currentUid(): String? = flow.value
    }

    private class FakeRepository(
        private val result: Result<InboxPage>? = null,
    ) : InboxRepository {
        val anchors = mutableListOf<InboxAnchor?>()

        override suspend fun listInboxItems(
            pageSize: Int,
            after: InboxAnchor?,
        ): Result<InboxPage> {
            anchors += after
            result?.let { return it }
            return when (after) {
                null -> Result.success(
                    InboxPage(
                        listOf(
                            InboxItem.PrivateErrorReport("report-1", "les-9", "whole-lesson text", 5_000L),
                        ),
                        InboxAnchor(5_000L, "report-1"),
                    ),
                )
                else -> Result.success(
                    InboxPage(
                        listOf(
                            InboxItem.LessonComment("comment-1", "les-9", "public text", 4_000L, "Player"),
                        ),
                        null,
                    ),
                )
            }
        }

        override suspend fun queueErrorReport(submission: com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonErrorReportSubmission): Result<Unit> =
            Result.success(Unit)
    }
}
