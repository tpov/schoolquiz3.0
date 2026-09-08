package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancellationReceipt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryCommandOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryResolution
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPlacement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingPreparation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusMetadata
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultArenaEntryComponentTest {
    @Test
    fun failedEpochStreamLocksActionsInsteadOfReusingLastUid() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                val holder =
                    ArenaEntryStateHolder(
                        f.dependencies.copy(sessionEvents = ArenaSessionObserver { flow { error("private failure") } }),
                        StandardTestDispatcher(testScheduler),
                        { "unused" },
                        { f.now },
                    )
                try {
                    runCurrent()
                    assertNull(holder.state.value.session)
                    holder.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, null))
                    holder.onEvent(ArenaEntryEvent.Load(f.stamp, ArenaEntryPage.TICKETS))
                    runCurrent()
                    assertNull(holder.state.value.dialog)
                    assertTrue(f.repository.calls.isEmpty())
                } finally {
                    holder.onDestroy()
                }
            }
        }

    @Test
    fun busyObserverReentrantConfirmCannotCreateSecondRequest() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, null))
                val confirm = ArenaEntryEvent.Confirm(f.stamp, requireNotNull(f.state.dialog).id)
                val observer =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        f.component.state.collect { if (it.busy) f.component.onEvent(confirm) }
                    }
                f.component.onEvent(confirm)
                runCurrent()
                assertEquals(1, f.repository.requests.size)
                observer.cancel()
            }
        }

    @Test
    fun recreatedProcessOnlyLoadsJournalOnRequestAndRetryDoesNotCreateConsent() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                val original =
                    ArenaPrepareRequest.Selected(
                        f.stamp.ownerUid,
                        "before-restart",
                        Difficulty.HARD,
                        "lesson",
                        1,
                    )
                f.repository.preparations = ArenaPreparationsPage(listOf(pending(original)), null)
                val lifecycle = LifecycleRegistry()
                val keeper = InstanceKeeperDispatcher()
                val recreated =
                    DefaultArenaEntryComponent(
                        DefaultComponentContext(lifecycle, instanceKeeper = keeper),
                        f.dependencies,
                        StandardTestDispatcher(testScheduler),
                        ids = { error("Restoring or retrying must not allocate IDs") },
                        nowMs = { f.now },
                    )
                try {
                    lifecycle.resume()
                    runCurrent()
                    assertTrue(f.repository.calls.isEmpty())
                    recreated.onEvent(ArenaEntryEvent.Load(f.stamp, ArenaEntryPage.PREPARATIONS))
                    runCurrent()
                    recreated.onEvent(ArenaEntryEvent.RetryPreparation(f.stamp, original.requestId))
                    runCurrent()
                    assertEquals(1, f.repository.calls.count { it == "dispatch:before-restart" })
                    assertTrue(f.repository.requests.isEmpty())
                } finally {
                    keeper.destroy()
                    lifecycle.stop()
                    lifecycle.destroy()
                }
            }
        }

    @Test
    fun creationResumeAndAccountEventsDoNotReadOrPrepare() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                runCurrent()
                f.lifecycle.stop()
                f.lifecycle.resume()
                f.sessions.events.value = ArenaSessionStamp("owner-a", 2)
                runCurrent()
                assertTrue(f.repository.calls.isEmpty())
                assertEquals(0, f.walletCalls)
                assertEquals(2L, f.state.session?.generation)
            }
        }

    @Test
    fun selectedConsentFreezesCeilingAndDifficultyAndRejectsDoubleTapBeforeSuspension() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                val card = lesson(1)
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, card))
                val dialog = assertIs<ArenaEntryDialog.Prepare>(f.state.dialog)
                f.component.onEvent(ArenaEntryEvent.ChangeDifficulty(f.stamp, dialog.id, Difficulty.HARD))
                val confirm = ArenaEntryEvent.Confirm(f.stamp, dialog.id)
                assertTrue(f.repository.calls.isEmpty())
                f.component.onEvent(confirm)
                f.component.onEvent(confirm)
                assertTrue(f.state.busy)
                assertTrue(f.repository.calls.isEmpty())
                runCurrent()
                val request = assertIs<ArenaPrepareRequest.Selected>(f.repository.requests.single())
                assertEquals(1, request.maxPriceNolics)
                assertEquals(Difficulty.HARD, request.difficulty)
                assertEquals(card.lessonId, request.lessonId)
                assertEquals(
                    listOf("record:" + request.requestId, "dispatch:" + request.requestId, "tickets", "preparations"),
                    f.repository.calls,
                )
                assertFalse(f.state.busy)
            }
        }

    @Test
    fun randomRequiresConfirmationAndDoesNotPrefetch() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, null))
                runCurrent()
                assertTrue(f.repository.calls.isEmpty())
                f.confirm()
                runCurrent()
                assertIs<ArenaPrepareRequest.Random>(f.repository.requests.single())
                assertEquals(1, f.repository.calls.count { it.startsWith("dispatch:") })
                assertEquals(0, f.walletCalls)
            }
        }

    @Test
    fun invalidSelectedPricesNeverBecomeRandomOrMaximumDefault() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                for (price in listOf(0, 101, -1)) {
                    f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, lesson(price)))
                    assertNull(f.state.dialog)
                }
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, lesson(100)))
                f.confirm()
                runCurrent()
                assertEquals(100, assertIs<ArenaPrepareRequest.Selected>(f.repository.requests.single()).maxPriceNolics)
            }
        }

    @Test
    fun lostResponseRetryDispatchesOriginalIdWithoutRecordingNewConsent() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                f.repository.dispatchHook = { Result.success(ArenaEntryCommandOutcome.RETRY_SAME_REQUEST) }
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, null))
                f.confirm()
                runCurrent()
                val request = f.repository.requests.single()
                f.repository.preparations = ArenaPreparationsPage(listOf(pending(request)), null)
                f.load(ArenaEntryPage.PREPARATIONS)
                runCurrent()
                f.component.onEvent(ArenaEntryEvent.RetryPreparation(f.stamp, request.requestId))
                runCurrent()
                assertEquals(1, f.repository.requests.size)
                assertEquals(2, f.repository.calls.count { it == "dispatch:" + request.requestId })
                assertEquals(request.requestId, f.state.lastRequestId)
            }
        }

    @Test
    fun priceChangedBlocksEvenStaleJournalRetryUntilNewExplicitConsent() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                f.repository.dispatchHook = { Result.success(ArenaEntryCommandOutcome.PRICE_CHANGED_REQUIRES_CONSENT) }
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, lesson(1)))
                f.confirm()
                runCurrent()
                val first = f.repository.requests.single()
                f.repository.preparations = ArenaPreparationsPage(listOf(pending(first)), null)
                f.load(ArenaEntryPage.PREPARATIONS)
                runCurrent()
                f.component.onEvent(ArenaEntryEvent.RetryPreparation(f.stamp, first.requestId))
                runCurrent()
                assertEquals(1, f.repository.requests.size)
                assertEquals(1, f.repository.calls.count { it.startsWith("dispatch:") })
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, lesson(100)))
                f.confirm()
                runCurrent()
                assertEquals(100, assertIs<ArenaPrepareRequest.Selected>(f.repository.requests.last()).maxPriceNolics)
                assertTrue(first.requestId != f.repository.requests.last().requestId)
            }
        }

    @Test
    fun corruptPreparationCannotBeRetriedEvenWithRequestPayload() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                val request = ArenaPrepareRequest.Random(f.stamp.ownerUid, "saved", Difficulty.EASY)
                val row = pending(request).copy(state = ArenaPreparationState.NEEDS_RECOVERY)
                f.repository.preparations = ArenaPreparationsPage(listOf(row), null)
                f.load(ArenaEntryPage.PREPARATIONS)
                runCurrent()
                f.component.onEvent(ArenaEntryEvent.RetryPreparation(f.stamp, row.requestId))
                runCurrent()
                assertFalse(row.canRetryPreparation())
                assertFalse(f.repository.calls.any { it.startsWith("dispatch:") })
            }
        }

    @Test
    fun separateOpaqueCursorsAreForwardedOnlyByTheirOwnExplicitNextAction() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                val t = object : ArenaEntriesCursor {}
                val p = object : ArenaPreparationsCursor {}
                val r = object : ArenaRecoveryCursor {}
                f.repository.tickets = ArenaEntriesPage(emptyList(), t)
                f.repository.preparations = ArenaPreparationsPage(emptyList(), p)
                f.repository.recovery = ArenaRecoveryPage(emptyList(), r)
                for (page in listOf(ArenaEntryPage.TICKETS, ArenaEntryPage.PREPARATIONS, ArenaEntryPage.RECOVERY)) {
                    f.load(page)
                    runCurrent()
                    f.load(page, true)
                    runCurrent()
                }
                assertSame(t, f.repository.ticketsCursors[1])
                assertSame(p, f.repository.preparationsCursors[1])
                assertSame(r, f.repository.recoveryCursors[1])
                assertEquals(2, f.repository.recoveryCursors.size)
                assertEquals(0, f.walletCalls)
            }
        }

    @Test
    fun zeroAndUnknownWalletNeverBlockListsOrRecoveryAndReserveIsNotSubtractedTwice() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                f.load(ArenaEntryPage.WALLET)
                runCurrent()
                assertEquals(70L, f.state.wallet?.availableNolics)
                assertEquals(30L, f.state.wallet?.reservedNolics)
                f.walletReply = { Result.success(ArenaWalletObservation(it, f.now, 0, 30, 0, 2, 500)) }
                f.load(ArenaEntryPage.WALLET)
                runCurrent()
                f.load(ArenaEntryPage.TICKETS)
                runCurrent()
                assertTrue(f.state.hasLoadedTickets)
                f.walletReply = { Result.failure(ArenaWalletFailure(ArenaWalletFailureKind.MISSING_PROFILE)) }
                f.load(ArenaEntryPage.WALLET)
                runCurrent()
                assertNull(f.state.wallet)
                f.load(ArenaEntryPage.RECOVERY)
                runCurrent()
                assertTrue("recover-page" in f.repository.calls)
            }
        }

    @Test
    fun staleWalletExpiresOnResumeAndFinancialActionInvalidatesBeforeAwait() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                f.load(ArenaEntryPage.WALLET)
                runCurrent()
                f.now += 60001
                f.lifecycle.stop()
                f.lifecycle.resume()
                assertNull(f.state.wallet)
                f.load(ArenaEntryPage.WALLET)
                runCurrent()
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, null))
                f.confirm()
                assertNull(f.state.wallet)
                runCurrent()
                assertEquals(2, f.walletCalls)
            }
        }

    @Test
    fun sameUidNewEpochClearsDialogAndOldCallbackCannotBorrowNewSession() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                runCurrent()
                val oldStamp = f.stamp
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(oldStamp, lesson(1)))
                val oldDialog = requireNotNull(f.state.dialog)
                f.sessions.events.value = ArenaSessionStamp("owner-b", 2)
                f.sessions.events.value = ArenaSessionStamp("owner-a", 3)
                runCurrent()
                assertNull(f.state.dialog)
                f.component.onEvent(ArenaEntryEvent.Confirm(oldStamp, oldDialog.id))
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(oldStamp, lesson(1)))
                runCurrent()
                assertTrue(f.repository.calls.isEmpty())
                assertEquals(3L, f.state.session?.generation)
            }
        }

    @Test
    fun accountSwitchAfterDurableConsentDoesNotDispatchAndOldResponseCannotClearNewBusy() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                val oldReply = CompletableDeferred<Unit>()
                f.repository.recordHook = { withContext(NonCancellable) { oldReply.await() } }
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, null))
                f.confirm()
                runCurrent()
                f.sessions.events.value = ArenaSessionStamp("owner-b", 2)
                runCurrent()
                val walletReply = CompletableDeferred<Unit>()
                f.walletReply = { stamp ->
                    walletReply.await()
                    Result.success(ArenaWalletObservation(stamp, f.now, 1, 0, 1, 0, 500))
                }
                f.load(ArenaEntryPage.WALLET)
                runCurrent()
                oldReply.complete(Unit)
                runCurrent()
                assertTrue(f.state.busy)
                assertFalse(f.repository.calls.any { it.startsWith("dispatch:") })
                walletReply.complete(Unit)
                runCurrent()
                assertFalse(f.state.busy)
                assertEquals("owner-b", f.state.wallet?.stamp?.ownerUid)
            }
        }

    @Test
    fun cancelledSettlementUsesStrictResolverAndNotCancelOrNewAttempt() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                val row = ticket(ArenaTicketStatus.CANCELLED, ArenaLocalDecision.SETTLE)
                f.repository.tickets = ArenaEntriesPage(listOf(row), null)
                val metadata = ArenaTicketStatusMetadata(requireNotNull(row.header), "author", 1, null)
                f.repository.resolution =
                    ArenaEntryResolution.CancelledResolved(
                        ArenaTicketStatusObservation.Cancelled(
                            metadata,
                            ArenaCancellationReceipt("receipt", 1000, 1, 1),
                        ),
                    )
                f.load(ArenaEntryPage.TICKETS)
                runCurrent()
                assertEquals(
                    setOf(ArenaTicketAction.RECOVER, ArenaTicketAction.RECONCILE_CANCELLED),
                    row.availableEntryActions(),
                )
                f.component.onEvent(
                    ArenaEntryEvent.TicketAction(f.stamp, row.ticketId, ArenaTicketAction.RECONCILE_CANCELLED),
                )
                runCurrent()
                assertEquals(ArenaEntryNotice.CANCELLED_RESOLVED, f.state.notice)
                assertTrue("reconcile:" + row.ticketId in f.repository.calls)
                assertFalse(f.repository.calls.any { it.startsWith("cancel-") || it.startsWith("record:") })
            }
        }

    @Test
    fun offlineCancelRecordsOnceThenRetryOnlyDispatchesOriginalIntent() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                val row =
                    ticket(
                        ArenaTicketStatus.RESERVED,
                        ArenaLocalDecision.NONE,
                    ).copy(state = ArenaLocalEntryState.NEEDS_RECOVERY)
                f.repository.tickets = ArenaEntriesPage(listOf(row), null)
                f.load(ArenaEntryPage.TICKETS)
                runCurrent()
                assertTrue(row.canOfferCancellation())
                f.repository.dispatchHook = { Result.failure(ArenaEntryFailure(ArenaEntryFailureKind.NO_NETWORK)) }
                f.component.onEvent(ArenaEntryEvent.ShowCancel(f.stamp, row.ticketId))
                f.confirm()
                runCurrent()
                assertEquals(ArenaEntryNotice.OFFLINE, f.state.notice)
                f.repository.tickets =
                    ArenaEntriesPage(
                        listOf(
                            row.copy(
                                localDecision = ArenaLocalDecision.CANCEL,
                                state = ArenaLocalEntryState.CANCEL_PENDING,
                            ),
                        ),
                        null,
                    )
                f.load(ArenaEntryPage.TICKETS)
                runCurrent()
                f.component.onEvent(ArenaEntryEvent.TicketAction(f.stamp, row.ticketId, ArenaTicketAction.RETRY_CANCEL))
                runCurrent()
                assertEquals(1, f.repository.calls.count { it.startsWith("cancel-record:") })
                assertEquals(2, f.repository.calls.count { it.startsWith("cancel-dispatch:") })
            }
        }

    @Test
    fun prepareFailureDoesNotFallbackToRandomOrAutomaticallyRetry() =
        runTest {
            ArenaEntryFixture(StandardTestDispatcher(testScheduler)).use { f ->
                f.repository.dispatchHook = { Result.failure(ArenaEntryFailure(ArenaEntryFailureKind.UNAVAILABLE)) }
                f.component.onEvent(ArenaEntryEvent.ShowPrepare(f.stamp, lesson(1)))
                f.confirm()
                runCurrent()
                assertIs<ArenaPrepareRequest.Selected>(f.repository.requests.single())
                assertEquals(1, f.repository.calls.count { it.startsWith("dispatch:") })
                assertEquals(ArenaEntryNotice.UNAVAILABLE, f.state.notice)
            }
        }

    private fun lesson(price: Int) =
        ArenaLessonCatalogCard(
            "lesson",
            1,
            "Frozen lesson",
            "author",
            ArenaLessonCatalogRating(0, 0, ArenaLessonCatalogScore(40, 20), 1),
            price,
            ArenaLessonCatalogPlacement(null, null, null),
        )

    private fun pending(request: ArenaPrepareRequest) =
        ArenaPendingPreparation(
            request.requestId,
            request,
            ArenaPreparationState.PENDING,
            ArenaPreparationReason.RETRY_SAME_REQUEST,
        )

    private fun ticket(
        status: ArenaTicketStatus,
        decision: ArenaLocalDecision,
    ): ArenaLocalEntry {
        val id = "a".repeat(64)
        val header =
            ArenaTicketHeader(
                1, id, "attempt", "owner-a", "original-request", ArenaEntryMode.SELECTED,
                Difficulty.EASY, "lesson", 1, status, 1, 1, 1000, "2026-09-08",
            )
        return ArenaLocalEntry(
            id,
            header,
            if (status == ArenaTicketStatus.RESERVED) {
                ArenaLocalEntryState.PREPARED_SNAPSHOT
            } else {
                ArenaLocalEntryState.TERMINAL_OBSERVED
            },
            decision,
        )
    }
}
