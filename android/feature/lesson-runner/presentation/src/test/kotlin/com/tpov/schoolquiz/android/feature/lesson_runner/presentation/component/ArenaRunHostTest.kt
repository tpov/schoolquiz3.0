@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestionWindowRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaOnlineO2Adapter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaFrozenRatingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDelivery
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowView
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunHostTest {
    @Test
    fun settleReceiptUsesExactFrozenIdentityAndOnlyConfirmedCompletionIsGreen() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()

                val pending = assertIs<ArenaRunnerView.Result>(host.view.value)
                assertEquals(f.run.header.attemptId, pending.attempt.id.value)
                assertEquals(f.run.stamp.ownerUid, pending.attempt.userId)
                assertEquals(f.run.header.lessonId, pending.attempt.lessonId.value)
                assertEquals(f.run.header.lessonVersion, pending.attempt.lessonVersion)
                assertEquals(f.run.header.difficulty, pending.attempt.mode)
                assertEquals(listOf(pending.attempt), f.settlements.observedAttempts)
                assertFalse(pending.settlement.isConfirmedCompletion)

                f.settlements.emit(pending.attempt, confirmedSettlement())
                runCurrent()
                val confirmed = assertIs<ArenaRunnerView.Result>(host.view.value)
                assertTrue(confirmed.settlement.isConfirmedCompletion)
                assertEquals(100, confirmed.settlement.percentScore?.raw)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun malformedLegacyAndPendingReceiptsNeverBecomeConfirmedArenaResults() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                val attempt = assertIs<ArenaRunnerView.Result>(host.view.value).attempt
                val invalid =
                    listOf(
                        ServerAttemptSettlement(ServerAttemptSettlementState.PENDING),
                        ServerAttemptSettlement(ServerAttemptSettlementState.UNSCORABLE),
                        ServerAttemptSettlement(ServerAttemptSettlementState.REJECTED),
                        ServerAttemptSettlement(ServerAttemptSettlementState.LEGACY_RECORDED),
                        ServerAttemptSettlement(ServerAttemptSettlementState.INVALID_RECEIPT),
                        ServerAttemptSettlement(ServerAttemptSettlementState.ACCOUNT_UNAVAILABLE),
                        ServerAttemptSettlement(
                            state = ServerAttemptSettlementState.SETTLED,
                            ownerVerified = true,
                            completed = true,
                        ),
                    )
                invalid.forEach { settlement ->
                    f.settlements.emit(attempt, settlement)
                    runCurrent()
                    val frame = assertIs<ArenaRunnerView.Result>(host.view.value)
                    assertFalse(frame.settlement.isConfirmedCompletion)
                    assertNull(frame.settlement.percentScore)
                    assertNull(frame.settlement.reward)
                }
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun pendingReceiptCanRequestManualSyncButConfirmedReceiptCannot() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                val pending = assertIs<ArenaRunnerView.Result>(host.view.value)
                assertTrue(pending.canSync)

                host.sync()
                assertEquals(1, f.syncCalls)

                f.settlements.emit(pending.attempt, confirmedSettlement())
                runCurrent()
                val confirmed = assertIs<ArenaRunnerView.Result>(host.view.value)
                assertFalse(confirmed.canSync)
                host.sync()
                assertEquals(1, f.syncCalls)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun ratingCallbacksRequireTheCurrentConfirmedFrozenReceipt() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                val pending = assertIs<ArenaRunnerView.Result>(host.view.value)
                f.settlements.emit(pending.attempt, confirmedSettlement())
                runCurrent()
                f.ratings.emit(pending.stamp, pending.header.ticketId, ArenaFrozenRatingState.Pending)
                runCurrent()
                val confirmed = assertIs<ArenaRunnerView.Result>(host.view.value)

                host.submitRating(confirmed, 2)
                runCurrent()
                assertEquals(listOf(Triple(confirmed.stamp, confirmed.header.ticketId, 2)), f.ratings.submitted)
                val queued = assertIs<ArenaRunnerView.Result>(host.view.value)
                host.requestRatingSync(queued)
                assertEquals(1, f.syncCalls)

                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.CANCEL)
                host.reload()
                runCurrent()
                host.requestRatingSync(confirmed)
                host.submitRating(confirmed, 3)
                runCurrent()
                assertEquals(1, f.ratings.submitted.size)
                assertEquals(1, f.syncCalls)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun receiptSubscriptionCancelsForCancellationAndLateABATicketFlow() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                val attempt = assertIs<ArenaRunnerView.Result>(host.view.value).attempt

                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.CANCEL)
                host.reload()
                runCurrent()
                assertEquals(
                    ArenaRunnerStatus.PENDING_CANCEL,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
                assertEquals(1, f.settlements.cancellations)

                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                f.run.session = ArenaSessionStamp("other-owner", 2)
                f.sessionEvents.value = f.run.session
                runCurrent()
                f.run.session = f.run.stamp.copy(generation = 3)
                f.sessionEvents.value = f.run.session
                runCurrent()
                f.settlements.emit(attempt, confirmedSettlement())
                runCurrent()
                assertEquals(
                    ArenaRunnerStatus.ACCOUNT_CHANGED,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
                assertTrue(f.settlements.cancellations >= 2)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun terminalSettledResubscribesAtMostOnceAndDestroyCancelsReceiptFlow() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                val attempt = assertIs<ArenaRunnerView.Result>(host.view.value).attempt
                f.run.observed = ArenaRunRead.Terminal(f.run.header.copy(status = ArenaTicketStatus.SETTLED))
                host.reload()
                host.reload()
                runCurrent()
                assertEquals(listOf(attempt), f.settlements.observedAttempts)
                host.onDestroy()
                runCurrent()
                assertEquals(1, f.settlements.cancellations)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun restoredFrozenTicketReceivesTheSameDurableReceipt() =
        runTest {
            val f = ArenaRunHostFixture(this)
            val first = f.host()
            try {
                first.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                first.reload()
                runCurrent()
                val attempt = assertIs<ArenaRunnerView.Result>(first.view.value).attempt
                f.settlements.emit(attempt, confirmedSettlement())
                runCurrent()
                first.onDestroy()

                val restored = f.host(fresh = false, initiatingSession = f.run.stamp)
                try {
                    restored.foreground()
                    runCurrent()
                    val result = assertIs<ArenaRunnerView.Result>(restored.view.value)
                    assertEquals(attempt, result.attempt)
                    assertTrue(result.settlement.isConfirmedCompletion)
                } finally {
                    restored.onDestroy()
                }
            } finally {
                first.onDestroy()
            }
        }

    @Test
    fun liveResumeCannotBorrowSameUidNewEpochBetweenTapAndConstruction() =
        runTest {
            val f = ArenaRunHostFixture(this)
            f.switchEpoch()
            val host = f.host(fresh = false, initiatingSession = f.run.stamp)
            try {
                host.foreground()
                runCurrent()
                assertEquals(
                    ArenaRunnerStatus.ACCOUNT_CHANGED,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
                assertEquals(0, f.run.readCalls + f.run.freshCalls)
            } finally {
                host.onDestroy()
            }
        }

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
                val pending = assertIs<ArenaRunnerView.Result>(host.view.value)
                assertFalse(pending.settlement.isConfirmedCompletion)
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

    @Test
    fun unsatisfiedConsentOnPendingWorkRaisesDialogAndDecisionDrains() =
        runTest {
            var satisfied = false
            var accepted = 0
            val f =
                ArenaRunHostFixture(
                    this,
                    ArenaConsentHandles(
                        satisfied = { satisfied },
                        accept = { accepted += 1; satisfied = true },
                    ),
                )
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                assertIs<ArenaRunnerView.Result>(host.view.value)
                val syncsBefore = f.syncCalls

                host.sync()
                runCurrent()
                assertTrue(
                    host.needsTermsConsent.value,
                    "у прогона есть незакрытый SETTLE: игрок должен увидеть запрос согласия",
                )
                assertEquals(syncsBefore, f.syncCalls, "без согласия очередь не дренируется")

                host.acceptTerms()
                runCurrent()
                assertEquals(1, accepted, "согласие фиксируется один раз")
                assertFalse(host.needsTermsConsent.value, "диалог закрывается только успехом")
                assertEquals(syncsBefore + 1, f.syncCalls, "согласие сразу возвращает SETTLE в отправку")
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun throwingAcceptKeepsDialogOpenAndDrainsNothing() =
        runTest {
            var satisfied = false
            val f =
                ArenaRunHostFixture(
                    this,
                    ArenaConsentHandles(
                        satisfied = { satisfied },
                        accept = { throw IllegalStateException("room io failure") },
                    ),
                )
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                val syncsBefore = f.syncCalls

                host.sync()
                runCurrent()
                assertTrue(host.needsTermsConsent.value)

                host.acceptTerms()
                runCurrent()
                assertTrue(
                    host.needsTermsConsent.value,
                    "падение accept не закрывает диалог — решение ещё не принято",
                )
                assertEquals(
                    syncsBefore,
                    f.syncCalls,
                    "ошибка согласия не дренирует очередь",
                )
                assertFalse(satisfied, "падение accept не считается согласием")
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun dismissClearsTheConsentFlagWithoutTouchingTheQueue() =
        runTest {
            var satisfied = false
            val f =
                ArenaRunHostFixture(
                    this,
                    ArenaConsentHandles(
                        satisfied = { satisfied },
                        accept = { satisfied = true },
                    ),
                )
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                f.run.observed = ArenaRunRead.DecisionPending(f.run.header, ArenaRunDecision.SETTLE)
                host.reload()
                runCurrent()
                host.sync()
                runCurrent()
                assertTrue(host.needsTermsConsent.value)
                val syncsBefore = f.syncCalls

                host.dismissTermsConsent()
                assertFalse(
                    host.needsTermsConsent.value,
                    "«Позже» сбрасывает флаг — экран остаётся, хост не навигирует",
                )
                assertEquals(syncsBefore, f.syncCalls, "отказ от решения не дренирует очередь")

                host.sync()
                runCurrent()
                assertTrue(
                    host.needsTermsConsent.value,
                    "следующий явный sync с незакрытой работой поднимает диалог снова",
                )
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun bareGateVerdictWithoutPendingWorkDoesNotRaiseTheDialog() =
        runTest {
            var satisfied = false
            val f =
                ArenaRunHostFixture(
                    this,
                    ArenaConsentHandles(
                        satisfied = { satisfied },
                        accept = { satisfied = true },
                    ),
                )
            val host = f.host()
            try {
                host.foreground()
                runCurrent()
                // Тикет уже SETTLED-терминал: незакрытой мутации у прогона нет — гейт не важен.
                f.run.observed = ArenaRunRead.Terminal(f.run.header.copy(status = ArenaTicketStatus.SETTLED))
                host.reload()
                runCurrent()

                host.sync()
                runCurrent()
                assertFalse(
                    host.needsTermsConsent.value,
                    "голый вердикт гейта без pending-работы диалога не поднимает",
                )
                assertEquals(0, f.syncCalls)
            } finally {
                host.onDestroy()
            }
        }

    // ── Онлайн-O2 боковой канал: Continue-из-паузы на HARD-попытке (ретро A1, дев-дефолт) ──────

    @Test
    fun onlineHardContinueAnchorsTheDeadlineToTheServerWindow() =
        runTest {
            val windows = HostWindowFake.delivered()
            var onlineReads = 0
            val f =
                ArenaRunHostFixture(
                    this,
                    difficulty = Difficulty.HARD,
                    windows = windows,
                    online = {
                        onlineReads++
                        true
                    },
                )
            val host = f.host()
            try {
                val pauseDeadline = pausedAfterAnswer(f, host)
                f.run.candidates.clear()

                host.action(host.question().token, ArenaRunAction.Continue)
                runCurrent()

                assertEquals(1, onlineReads, "свежесть связи проверяется один раз на переход")
                assertEquals(1, windows.beginCalls)
                assertEquals(1, windows.fetchCalls)
                assertEquals(
                    listOf(ArenaRunnerPhase.ANSWERING),
                    f.run.candidates.map { it.phase },
                    "локальный Continue при armed-канале не диспетчится — резюм едет через CAS бокового канала",
                )
                assertEquals(
                    f.run.now + 24_000,
                    f.run.candidates.single().deadlineMs,
                    "серверный остаток зажимается захваченной выдачей окна и ставит исходный deadline",
                )
                assertEquals(1, host.question().token.index)
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun offlineHardContinueKeepsTheLocalTimerWithoutTouchingTheWindow() =
        runTest {
            val windows = HostWindowFake.delivered()
            val f =
                ArenaRunHostFixture(
                    this,
                    difficulty = Difficulty.HARD,
                    windows = windows,
                    online = { false },
                )
            val host = f.host()
            try {
                val pauseDeadline = pausedAfterAnswer(f, host)
                f.run.candidates.clear()

                host.action(host.question().token, ArenaRunAction.Continue)
                runCurrent()

                assertEquals(
                    0,
                    windows.beginCalls + windows.fetchCalls,
                    "CAP-1: без связи окно не открывается — попытка едет на локальном таймере",
                )
                assertEquals(
                    listOf(ArenaRunnerPhase.ANSWERING),
                    f.run.candidates.map { it.phase },
                )
                assertEquals(pauseDeadline, f.run.candidates.single().deadlineMs, "локальный резюм хранит прежний deadline")
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun failedWindowCallFallsBackToLocalResume() =
        runTest {
            val windows = HostWindowFake.beginFails()
            val f =
                ArenaRunHostFixture(
                    this,
                    difficulty = Difficulty.HARD,
                    windows = windows,
                    online = { true },
                )
            val host = f.host()
            try {
                val pauseDeadline = pausedAfterAnswer(f, host)
                f.run.candidates.clear()

                host.action(host.question().token, ArenaRunAction.Continue)
                runCurrent()

                assertEquals(1, windows.beginCalls)
                assertEquals(
                    0,
                    windows.fetchCalls,
                    "упавшее окно не переигрывается — отказ quiet, CAP-1",
                )
                assertEquals(
                    listOf(ArenaRunnerPhase.ANSWERING),
                    f.run.candidates.map { it.phase },
                )
                assertEquals(pauseDeadline, f.run.candidates.single().deadlineMs, "фолбэк — прежний локальный resumePaused")
                assertNull(f.run.coordinator.state.value.notice, "отказ окна не RELOAD_REQUIRED и не CONFLICT")
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun expiredWindowClosesTheAttemptThroughTheExistingCancelPath() =
        runTest {
            val windows = HostWindowFake.expired()
            val f =
                ArenaRunHostFixture(
                    this,
                    difficulty = Difficulty.HARD,
                    windows = windows,
                    online = { true },
                )
            val host = f.host()
            try {
                pausedAfterAnswer(f, host)
                f.run.candidates.clear()

                host.action(host.question().token, ArenaRunAction.Continue)
                runCurrent()

                // Ровно один заход: begin + fetch, без повторов — истёкшее окно не переигрывается.
                assertEquals(2, windows.beginCalls + windows.fetchCalls)
                assertTrue(f.run.candidates.isEmpty(), "истёкшее окно не заводит часы")
                assertEquals(
                    listOf("window-expired-${f.run.header.attemptId}-0"),
                    f.entries.cancels,
                    "закрытие — существующий путь отмены с детерминированным ключом",
                )
                assertEquals(
                    ArenaRunnerStatus.PENDING_CANCEL,
                    assertIs<ArenaRunnerView.Attention>(host.view.value).status,
                )
            } finally {
                host.onDestroy()
            }
        }

    @Test
    fun easyAttemptNeverArmsTheWindowSideChannel() =
        runTest {
            val windows = HostWindowFake.delivered()
            val f =
                ArenaRunHostFixture(
                    this,
                    difficulty = Difficulty.EASY,
                    windows = windows,
                    online = { true },
                )
            val host = f.host()
            try {
                val pauseDeadline = pausedAfterAnswer(f, host)
                f.run.candidates.clear()

                host.action(host.question().token, ArenaRunAction.Continue)
                runCurrent()

                assertEquals(
                    0,
                    windows.beginCalls + windows.fetchCalls,
                    "дев-дефолт: канал armed только на HARD-попытке",
                )
                assertEquals(
                    listOf(ArenaRunnerPhase.ANSWERING),
                    f.run.candidates.map { it.phase },
                )
                assertEquals(pauseDeadline, f.run.candidates.single().deadlineMs)
            } finally {
                host.onDestroy()
            }
        }

    /**
     * Answer + Continue-в-паузу под фоном: авторезюма нет (граница фона поглощает RevealPrepared),
     * кадр остаётся PAUSED — тот же вход, что у игрока после возврата из фона. Возвращает паузный
     * deadline — исходный срок локального резюма для последующих сравнений.
     */
    private fun TestScope.pausedAfterAnswer(
        f: ArenaRunHostFixture,
        host: ArenaRunHost,
    ): Long {
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
        f.run.beforeCas = {}
        assertEquals(ArenaRunnerPhase.PAUSED, host.question().token.phase)
        return f.run.candidates.last().deadlineMs
    }
}

/** Честный источник окна под тесты хоста: identity совпадает с фикстурой прогона. */
private class HostWindowFake(
    private val view: Result<QuestionWindowView>,
    private val verdict: Result<QuestionDeliveryVerdict>,
) : QuestionWindowRemoteDataSource {
    var beginCalls = 0
    var fetchCalls = 0

    override suspend fun beginWindow(request: QuestionWindowRequest): Result<QuestionWindowView> {
        beginCalls++
        return view
    }

    override suspend fun fetchNext(
        attemptId: String,
        completedQuestionIndex: Int,
    ): Result<QuestionDeliveryVerdict> {
        fetchCalls++
        return verdict
    }

    companion object {
        /** Открытое окно с состоявшейся выдачей; остаток больше allocation — зажим до 24s. */
        fun delivered(): HostWindowFake =
            HostWindowFake(
                view = Result.success(questionWindowView()),
                verdict =
                    Result.success(
                        QuestionDeliveryVerdict.Delivered(
                            windowId = "win_o2",
                            delivery = QuestionDelivery(1, "q2", issuedAtMs = 1_000, answerDeadlineMs = 25_000),
                            remainderMs = 500_000,
                            serverNowMs = 1_000,
                        ),
                    ),
            )

        fun beginFails(): HostWindowFake =
            HostWindowFake(
                view = Result.failure(IllegalStateException("window begin unavailable")),
                verdict = Result.failure(IllegalStateException("unreachable")),
            )

        fun expired(): HostWindowFake =
            HostWindowFake(
                view = Result.success(questionWindowView()),
                verdict =
                    Result.success(
                        QuestionDeliveryVerdict.WindowExpired(
                            windowId = "win_o2",
                            closedAtMs = 61_000,
                            serverNowMs = 61_000,
                        ),
                    ),
            )

        private fun questionWindowView(): QuestionWindowView =
            QuestionWindowView(
                windowId = "win_o2",
                attemptId = "b".repeat(64),
                completedQuestionIndex = 0,
                nextQuestionIndex = 1,
                state = ArenaOnlineO2Adapter.WINDOW_STATE_OPEN,
                windowSeconds = 60,
                allocatedSeconds = 24,
                openedAtMs = 1_000,
                expiresAtMs = 61_000,
            )
    }
}
