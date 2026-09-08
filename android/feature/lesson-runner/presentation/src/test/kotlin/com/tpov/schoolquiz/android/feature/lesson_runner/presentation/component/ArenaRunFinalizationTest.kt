@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ArenaRunFinalizationTest {
    @Test
    fun finalCheckpointFreezesOnceAndRemainsPendingNotServerSuccess() =
        runTest {
            val fixture = RunCoordinatorFixture(this)
            fixture.complete { runCurrent() }
            fixture.coordinator.freeze()
            fixture.coordinator.freeze()
            runCurrent()
            val pending = assertIs<ArenaRunRead.DecisionPending>(fixture.coordinator.state.value.observed)
            assertEquals(ArenaRunDecision.SETTLE, pending.decision)
            fixture.coordinator.freeze()
            runCurrent()
            assertEquals(1, fixture.freezeCalls)
            assertNull(fixture.coordinator.token())
        }

    @Test
    fun lostFreezeResponseRecoversOriginalPendingDecisionByRead() =
        runTest {
            val fixture = RunCoordinatorFixture(this)
            fixture.complete { runCurrent() }
            fixture.afterFreeze = { error("lost response") }
            fixture.coordinator.freeze()
            runCurrent()
            assertEquals(ArenaRunNotice.RELOAD_REQUIRED, fixture.coordinator.state.value.notice)
            assertIs<ArenaRunRead.Completing>(fixture.coordinator.state.value.observed)
            fixture.coordinator.reload()
            runCurrent()
            assertIs<ArenaRunRead.DecisionPending>(fixture.coordinator.state.value.observed)
            assertEquals(1, fixture.freezeCalls)
        }

    @Test
    fun cancelledFreezeAfterCommitDoesNotRollbackAndRequiresRead() =
        runTest {
            val fixture = RunCoordinatorFixture(this)
            fixture.complete { runCurrent() }
            fixture.afterFreeze = { throw CancellationException("after freeze") }
            fixture.coordinator.freeze()
            runCurrent()
            assertEquals(ArenaRunNotice.RELOAD_REQUIRED, fixture.coordinator.state.value.notice)
            assertIs<ArenaRunRead.DecisionPending>(fixture.observed)
            fixture.coordinator.reload()
            runCurrent()
            assertIs<ArenaRunRead.DecisionPending>(fixture.coordinator.state.value.observed)
        }

    @Test
    fun epochChangeAfterFreezeCannotPublishOriginalOwnersResult() =
        runTest {
            val fixture = RunCoordinatorFixture(this)
            fixture.complete { runCurrent() }
            fixture.afterFreeze = { fixture.session = fixture.stamp.copy(generation = 2) }
            fixture.coordinator.freeze()
            runCurrent()
            assertIs<ArenaRunRead.DecisionPending>(fixture.observed)
            assertNull(fixture.coordinator.state.value.observed)
            assertEquals(ArenaRunNotice.ACCOUNT_CHANGED, fixture.coordinator.state.value.notice)
        }

    @Test
    fun deferredBackgroundUsesCapturedStopTimeNotLaterDispatcherTime() =
        runTest {
            val fixture = RunCoordinatorFixture(this)
            fixture.start()
            fixture.coordinator.dispatch(
                requireNotNull(fixture.coordinator.token()),
                ArenaRunAction.Answer(fixture.answer),
            )
            runCurrent()
            fixture.now = 9000
            fixture.coordinator.dispatch(requireNotNull(fixture.coordinator.token()), ArenaRunAction.BackgroundAt(2000))
            runCurrent()
            assertEquals(2000L, fixture.current().checkpoint.acceptedAnswers.single().answeredAtMs)
        }

    private fun RunCoordinatorFixture.complete(flush: () -> Unit) {
        start()
        repeat(2) {
            coordinator.dispatch(requireNotNull(coordinator.token()), ArenaRunAction.Answer(answer))
            flush()
            coordinator.dispatch(requireNotNull(coordinator.token()), ArenaRunAction.Continue)
            flush()
        }
        assertIs<ArenaRunRead.Completing>(coordinator.state.value.observed)
    }
}
