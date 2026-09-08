@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaFreshStart
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSyncScheduler
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArenaRunnerRootTest {
    @Test
    fun rotationRetainsOriginalCoordinatorFrameAndConsumesFreshOnlyOnce() =
        runTest {
            val f = RootFixture(this)
            try {
                val first = f.component()
                first.onArenaForeground()
                runCurrent()
                val token = first.question().token
                first.onArenaAction(token, ArenaRunAction.Answer(f.run.answer))
                runCurrent()
                val saved = first.question()
                first.onArenaBackground(true)
                f.lifecycles.last().destroy()
                val second = f.component()
                second.onArenaForeground()
                runCurrent()
                assertSame(first.arenaView, second.arenaView)
                assertEquals(saved, second.question())
                assertEquals(1, f.run.freshCalls)
                assertEquals(1, f.run.candidates.size)
            } finally {
                f.close()
            }
        }

    @Test
    fun legacyCallbacksWithoutFrameTokenCannotWriteOrSpend() =
        runTest {
            val f = RootFixture(this)
            try {
                val component = f.component()
                component.onArenaForeground()
                runCurrent()
                component.onAnswer(f.run.answer)
                component.onDraftChanged(f.run.answer)
                component.onTimeout()
                component.onContinue()
                assertFalse(component.hintRequested())
                runCurrent()
                assertTrue(f.run.candidates.isEmpty())
                assertEquals(0, f.sync.manualRequests)
            } finally {
                f.close()
            }
        }

    @Test
    fun againAndNextOnPendingResultReturnToTicketsWithoutNewAttempt() =
        runTest {
            val f = RootFixture(this)
            try {
                val component = f.component()
                component.onArenaForeground()
                runCurrent()
                repeat(2) {
                    component.onArenaAction(component.question().token, ArenaRunAction.Answer(f.run.answer))
                    runCurrent()
                    component.onArenaAction(component.question().token, ArenaRunAction.Continue)
                    runCurrent()
                }
                assertIs<ArenaRunnerView.Result>(component.arenaView.value)
                for (action in listOf(component::onRunAgain, component::onNextLesson)) {
                    val event = async { component.events.first() }
                    action()
                    runCurrent()
                    assertEquals(RunnerEvent.NavigateBack, event.await())
                }
                assertEquals(1, f.run.freshCalls)
                assertEquals(1, f.run.freezeCalls)
            } finally {
                f.close()
            }
        }

    @Test
    fun backFromFrozenResultNavigatesWithoutCancellingItsSettledTicket() =
        runTest {
            val f = RootFixture(this)
            try {
                val component = f.component()
                component.onArenaForeground()
                runCurrent()
                repeat(2) {
                    component.onArenaAction(component.question().token, ArenaRunAction.Answer(f.run.answer))
                    runCurrent()
                    component.onArenaAction(component.question().token, ArenaRunAction.Continue)
                    runCurrent()
                }
                assertIs<ArenaRunnerView.Result>(component.arenaView.value)

                val event = async { component.events.first() }
                component.onBack()
                runCurrent()

                assertEquals(RunnerEvent.NavigateBack, event.await())
                assertTrue(f.entries.cancels.isEmpty())
                assertEquals(0, f.entries.dispatches)
                assertIs<ArenaRunRead.DecisionPending>(f.run.observed)
            } finally {
                f.close()
            }
        }

    @Test
    fun retainedInstanceCannotBeReusedForDifferentTicket() =
        runTest {
            val f = RootFixture(this)
            try {
                f.component()
                runCurrent()
                assertFailsWith<IllegalArgumentException> {
                    f.component(f.request.copy(ticketId = "different-ticket"))
                }
                assertEquals(1, f.run.freshCalls)
            } finally {
                f.close()
            }
        }
}

private fun ArenaRunnerRootComponent.question() = assertIs<ArenaRunnerView.Question>(arenaView.value)

private class RootFixture(scope: TestScope) {
    val run = RunCoordinatorFixture(scope)
    val sync = FakeSyncScheduler()
    val entries = HostEntryFake(run)
    val lifecycles = mutableListOf<LifecycleRegistry>()
    val request =
        ArenaRunnerRequest(
            run.stamp.ownerUid,
            run.header.ticketId,
            ArenaFreshStart(run.stamp, SessionMode.LEARNING),
        )
    private val keeper = InstanceKeeperDispatcher()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val services =
        ArenaRunServices(
            run.repository,
            entries,
            ArenaSessionProvider { run.session },
            ArenaSessionObserver { flowOf(run.session) },
            ArenaWalletRepository { error("No automatic wallet read") },
            sync,
            HostSettlementFake(),
        )

    fun component(route: ArenaRunnerRequest = request): ArenaRunnerRootComponent {
        val lifecycle =
            LifecycleRegistry().also {
                it.resume()
                lifecycles += it
            }
        return DefaultArenaRunnerRootComponent(
            DefaultComponentContext(lifecycle = lifecycle, instanceKeeper = keeper),
            route,
            services,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(run.now)
            },
            dispatcher,
        )
    }

    fun close() {
        keeper.destroy()
        lifecycles.forEach { it.destroy() }
    }
}
