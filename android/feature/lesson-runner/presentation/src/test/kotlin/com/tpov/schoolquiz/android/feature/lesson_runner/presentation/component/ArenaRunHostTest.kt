@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunHostTest {
    @Test
    fun freshClaimStartingAfterBackgroundCannotCreateNegativeAnswerDuration() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                f.run.now = 500
                host.background(false)
                f.run.now = 1500
                runCurrent()
                val saved = assertIs<ArenaRunRead.Continuable>(f.run.observed).checkpoint
                assertEquals(1, saved.indexInPool)
                assertEquals(ArenaRunnerPhase.PAUSED, saved.phase)
                assertEquals(1000L, saved.acceptedAnswers.single().answeredAtMs)
                assertEquals(0L, saved.acceptedAnswers.single().durationMs)
                assertEquals(1, f.run.candidates.size)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun finalAnswerFreezesExactlyOnceWithoutShowingFinancialSuccess() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                repeat(2) {
                    host.action(host.question().token, ArenaRunAction.Answer(f.run.answer))
                    runCurrent()
                    host.action(host.question().token, ArenaRunAction.Continue)
                    runCurrent()
                }
                assertEquals(1, f.run.freezeCalls)
                assertIs<ArenaRunRead.DecisionPending>(f.run.observed)
                assertEquals(
                    ArenaRunnerStatus.PENDING_COMPLETION,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
                host.foreground()
                host.reload()
                runCurrent()
                assertEquals(1, f.run.freezeCalls)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun exitConfirmationCreatesOneLocalCancellationWithoutClaimingRefund() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                host.requestExit()
                assertTrue(host.question().question.showExitConfirmDialog)
                host.confirmExit("cancel-intent")
                host.confirmExit("different-repeat")
                runCurrent()
                assertEquals(listOf("cancel-intent"), f.entries.cancels)
                assertEquals(0, f.entries.dispatches)
                assertEquals(
                    ArenaRunnerStatus.PENDING_CANCEL,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
                assertEquals(0, f.run.freezeCalls)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun failedBackgroundWriteRequiresExplicitReadThenUsesOriginalBoundary() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.beforeCas = { error("disk failure before commit") }
                f.run.now = 2000
                host.background(false)
                runCurrent()
                assertEquals(
                    ArenaRunnerStatus.LOCAL_FAILURE,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
                assertEquals(1, f.run.candidates.size)
                host.foreground()
                runCurrent()
                assertEquals(1, f.run.candidates.size)
                f.run.beforeCas = {}
                f.run.now = 9000
                host.reload()
                runCurrent()
                val saved = assertIs<ArenaRunRead.Continuable>(f.run.observed).checkpoint
                assertEquals(2000L, saved.acceptedAnswers.single().answeredAtMs)
                assertEquals(ArenaRunnerPhase.PAUSED, saved.phase)
                assertEquals(2, f.run.candidates.size)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun foregroundContinueSavesPauseThenRevealsWithOriginalDeadline() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                host.action(host.question().token, ArenaRunAction.Answer(f.run.answer))
                runCurrent()
                host.action(host.question().token, ArenaRunAction.Continue)
                runCurrent()
                assertEquals(
                    listOf(ArenaRunnerPhase.FEEDBACK, ArenaRunnerPhase.PAUSED, ArenaRunnerPhase.ANSWERING),
                    f.run.candidates.map { it.phase },
                )
                assertEquals(f.run.candidates[1].deadlineMs, f.run.candidates[2].deadlineMs)
                assertEquals(1, host.question().token.index)
                assertEquals(0, f.walletCalls + f.syncCalls + f.entries.dispatches)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun backgroundDuringContinueKeepsUnshownNextQuestionPaused() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                host.action(host.question().token, ArenaRunAction.Answer(f.run.answer))
                runCurrent()
                val barrier = CompletableDeferred<Unit>()
                f.run.beforeCas = { barrier.await() }
                host.action(host.question().token, ArenaRunAction.Continue)
                runCurrent()
                host.background(false)
                host.foreground()
                barrier.complete(Unit)
                runCurrent()
                assertEquals(2, f.run.candidates.size)
                assertEquals(ArenaRunnerPhase.PAUSED, host.question().token.phase)
                assertEquals(1, host.question().token.index)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun backgroundDuringAnswerSaveClosesOneSlotAtOriginalTime() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                val barrier = CompletableDeferred<Unit>()
                f.run.beforeCas = { barrier.await() }
                host.action(host.question().token, ArenaRunAction.Answer(f.run.answer))
                runCurrent()
                f.run.now = 2000
                host.background(false)
                f.run.now = 9000
                barrier.complete(Unit)
                runCurrent()
                val saved = assertIs<ArenaRunRead.Continuable>(f.run.observed).checkpoint
                assertEquals(ArenaRunnerPhase.PAUSED, saved.phase)
                assertEquals(2000L, saved.acceptedAnswers.single().answeredAtMs)
                assertEquals(2, f.run.candidates.size)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun rotationDoesNotPersistAnAnswerAndRestoredPreparedRouteDoesNotClaim() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host(false)
            try {
                host.foreground()
                runCurrent()
                host.background(true)
                runCurrent()
                assertEquals(0, f.run.freshCalls)
                assertEquals(1, f.run.readCalls)
                assertTrue(f.run.candidates.isEmpty())
                assertEquals(
                    ArenaRunnerStatus.PREPARED_REQUIRES_EXPLICIT_START,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun sameUidNewEpochClearsQuestionAndRejectsOldCallback() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                val old = host.question().token
                f.switchEpoch()
                runCurrent()
                host.action(old, ArenaRunAction.Answer(f.run.answer))
                host.refreshWallet()
                runCurrent()
                assertEquals(
                    ArenaRunnerStatus.ACCOUNT_CHANGED,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
                assertTrue(f.run.candidates.isEmpty())
                assertEquals(0, f.walletCalls)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun hintsRequireExplicitKnownBudgetAndPersistBeforeFeedback() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                assertNull(host.question().question.lives)
                host.action(host.question().token, ArenaRunAction.Hint(f.run.answer))
                runCurrent()
                assertTrue(f.run.candidates.isEmpty())
                host.refreshWallet()
                runCurrent()
                assertEquals(5, host.question().question.lives)
                host.action(host.question().token, ArenaRunAction.Hint(f.run.answer))
                runCurrent()
                assertEquals(ArenaRunnerPhase.FEEDBACK, host.question().token.phase)
                assertEquals(4, host.question().question.lives)
                assertEquals(1, f.run.candidates.size)
            } finally {
                host.onDestroy()
            }
        }
}
