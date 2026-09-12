@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaFrozenRatingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaFrozenRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArenaRunRatingTest {
    @Test
    fun confirmedResultObservesTypedLocalStateWithoutEquatingQueuedAndAccepted() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()

                assertEquals(
                    listOf(RatingPortKey(fixture.session, "ticket-1")),
                    fixture.ratings.observed,
                )
                assertIs<ArenaFrozenRatingState.Pending>(controller.state.value.local)

                fixture.ratings.emitAt(0, ArenaFrozenRatingState.Queued(2))
                runCurrent()
                assertEquals(ArenaFrozenRatingState.Queued(2), controller.state.value.local)

                fixture.ratings.emitAt(0, ArenaFrozenRatingState.Accepted(2))
                runCurrent()
                assertEquals(ArenaFrozenRatingState.Accepted(2), controller.state.value.local)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun pendingOrMalformedSettlementDoesNotSubscribeOrEnableRating() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            try {
                listOf(
                    ServerAttemptSettlement(ServerAttemptSettlementState.PENDING),
                    ServerAttemptSettlement(
                        state = ServerAttemptSettlementState.SETTLED,
                        ownerVerified = true,
                        completed = true,
                    ),
                ).forEach { settlement ->
                    controller.bind(fixture.result(settlement))
                    runCurrent()
                }

                assertTrue(fixture.ratings.observed.isEmpty())
                assertFalse(controller.state.value.canSubmit)
                assertIs<ArenaFrozenRatingState.Unavailable>(controller.state.value.local)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun mismatchedAttemptOwnerAndBlankTicketDoNotCreateRatingIdentity() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result(attemptOwner = "owner-b"))
                controller.bind(fixture.result(ticketId = " "))
                runCurrent()

                assertTrue(fixture.ratings.observed.isEmpty())
                assertFalse(controller.state.value.canSubmit)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun observationFailureFailsClosedWithoutCrashingTheRetainedController() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            fixture.ratings.observationFailure = IllegalStateException("missing frozen source")
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()

                assertIs<ArenaFrozenRatingState.Unavailable>(controller.state.value.local)
                assertTrue(controller.state.value.observationFailed)
                assertFalse(controller.state.value.canSubmit)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun accountEpochABAAndChangedAttemptCancelOldObservationsAndIgnoreLateState() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            val first = fixture.result()
            try {
                controller.bind(first)
                runCurrent()

                fixture.session = ArenaSessionStamp("owner-b", 2)
                controller.onSessionChanged()
                runCurrent()
                assertEquals(1, fixture.ratings.cancellations)
                assertIs<ArenaFrozenRatingState.Unavailable>(controller.state.value.local)

                fixture.session = ArenaSessionStamp("owner-a", 3)
                val restored = fixture.result(stamp = fixture.session)
                controller.bind(restored)
                runCurrent()
                fixture.ratings.emitAt(0, ArenaFrozenRatingState.Accepted(1))
                runCurrent()
                assertIs<ArenaFrozenRatingState.Pending>(controller.state.value.local)

                val changedAttempt = fixture.result(stamp = fixture.session, attemptId = "attempt-2")
                controller.bind(changedAttempt)
                runCurrent()
                assertEquals(2, fixture.ratings.cancellations)
                fixture.ratings.emitAt(1, ArenaFrozenRatingState.Accepted(2))
                runCurrent()
                assertIs<ArenaFrozenRatingState.Pending>(controller.state.value.local)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun delayedQueuedSubmitCannotOverwriteNewerObservedAcceptedReceipt() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val release = CompletableDeferred<Unit>()
            fixture.ratings.submitBehavior = { _, _, rating ->
                release.await()
                Result.success(ArenaFrozenRatingState.Queued(rating))
            }
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()
                controller.submit(2)
                runCurrent()

                fixture.ratings.emitAt(0, ArenaFrozenRatingState.Accepted(2))
                runCurrent()
                assertEquals(ArenaFrozenRatingState.Accepted(2), controller.state.value.local)

                release.complete(Unit)
                runCurrent()
                assertEquals(ArenaFrozenRatingState.Accepted(2), controller.state.value.local)
                assertFalse(controller.state.value.submitting)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun onlyQueuedRatingCanRequestManualSyncAndNothingSchedulesItAutomatically() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()
                assertEquals(0, fixture.sync.requests)

                fixture.ratings.emitAt(0, ArenaFrozenRatingState.Queued(2))
                runCurrent()
                assertTrue(controller.state.value.canSync)
                assertEquals(0, fixture.sync.requests)

                controller.requestSync()
                assertEquals(1, fixture.sync.requests)

                fixture.ratings.emitAt(0, ArenaFrozenRatingState.Accepted(2))
                runCurrent()
                controller.requestSync()
                assertEquals(1, fixture.sync.requests)

                fixture.session = ArenaSessionStamp("owner-b", 2)
                controller.onSessionChanged()
                controller.requestSync()
                assertEquals(1, fixture.sync.requests)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun queuedSyncBurstIsSuppressedButRetryAndLifecycleResetRemainAvailable() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()
                fixture.ratings.emitAt(0, ArenaFrozenRatingState.Queued(2))
                runCurrent()

                controller.requestSync()
                controller.requestSync()
                assertEquals(1, fixture.sync.requests)
                assertFalse(controller.state.value.canSync)

                testScheduler.advanceTimeBy(500L)
                runCurrent()
                assertTrue(controller.state.value.canSync)
                controller.requestSync()
                assertEquals(2, fixture.sync.requests)

                fixture.session = ArenaSessionStamp("owner-b", 2)
                controller.onSessionChanged()
                testScheduler.advanceTimeBy(500L)
                runCurrent()
                assertFalse(controller.state.value.canSync)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun destroyCancelsQueuedSyncCooldownWithoutLateReenable() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            controller.bind(fixture.result())
            runCurrent()
            fixture.ratings.emitAt(0, ArenaFrozenRatingState.Queued(1))
            runCurrent()

            controller.requestSync()
            controller.onDestroy()
            testScheduler.advanceTimeBy(500L)
            runCurrent()

            assertEquals(1, fixture.sync.requests)
            assertFalse(controller.state.value.canSync)
        }

    @Test
    fun firstTapSetsBusyBeforeLaunchAndRepositoryReceivesOnlyOneIntent() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val waitForSubmit = CompletableDeferred<Unit>()
            fixture.ratings.submitBehavior = { _, _, rating ->
                waitForSubmit.await()
                Result.success(ArenaFrozenRatingState.Queued(rating))
            }
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()

                controller.submit(2)
                controller.submit(3)
                assertTrue(controller.state.value.submitting)
                runCurrent()
                assertEquals(listOf(2), fixture.ratings.submittedRatings)

                waitForSubmit.complete(Unit)
                runCurrent()
                assertEquals(ArenaFrozenRatingState.Queued(2), controller.state.value.local)
                assertFalse(controller.state.value.submitting)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun ownerChangeCancelsInFlightSubmissionAndClearsItsResult() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val release = CompletableDeferred<Unit>()
            var cancelled = false
            fixture.ratings.submitBehavior = { _, _, rating ->
                try {
                    release.await()
                    Result.success(ArenaFrozenRatingState.Queued(rating))
                } finally {
                    cancelled = true
                }
            }
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()
                controller.submit(2)
                runCurrent()

                fixture.session = ArenaSessionStamp("owner-b", 2)
                controller.onSessionChanged()
                runCurrent()

                assertTrue(cancelled)
                assertIs<ArenaFrozenRatingState.Unavailable>(controller.state.value.local)
                assertFalse(controller.state.value.submitting)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun failureAllowsRetryAndCancelledSubmitClearsBusyWithoutPublishingFailure() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()

                fixture.ratings.submitBehavior = { _, _, _ -> Result.failure(IllegalStateException("offline")) }
                controller.submit(1)
                runCurrent()
                assertTrue(controller.state.value.submitFailed)
                assertIs<ArenaFrozenRatingState.Pending>(controller.state.value.local)

                fixture.ratings.submitBehavior = { _, _, rating ->
                    Result.success(ArenaFrozenRatingState.Queued(rating))
                }
                controller.submit(3)
                runCurrent()
                assertEquals(ArenaFrozenRatingState.Queued(3), controller.state.value.local)
                assertFalse(controller.state.value.submitFailed)

                fixture.ratings.submitBehavior = { _, _, _ -> throw CancellationException("owner changed") }
                fixture.ratings.emitAt(0, ArenaFrozenRatingState.Pending)
                runCurrent()
                controller.submit(2)
                runCurrent()
                assertFalse(controller.state.value.submitting)
                assertFalse(controller.state.value.submitFailed)

                // The cancelled child must not take the retained supervisor scope down with it.
                fixture.ratings.submitBehavior = { _, _, rating ->
                    Result.success(ArenaFrozenRatingState.Queued(rating))
                }
                controller.submit(1)
                runCurrent()
                assertEquals(ArenaFrozenRatingState.Queued(1), controller.state.value.local)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun liveObservationFailureDropsTheRetryNoticeThatHasNoRetryControlLeft() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()

                fixture.ratings.submitBehavior = { _, _, _ -> Result.failure(IllegalStateException("offline")) }
                controller.submit(2)
                runCurrent()
                assertTrue(controller.state.value.submitFailed)
                assertIs<ArenaFrozenRatingState.Pending>(controller.state.value.local)

                fixture.ratings.failAt(0, IllegalStateException("frozen source gone"))
                runCurrent()

                assertIs<ArenaFrozenRatingState.Unavailable>(controller.state.value.local)
                assertTrue(controller.state.value.observationFailed)
                // Nothing here can be retried, so the retry notice must not survive alongside it.
                assertFalse(controller.state.value.submitFailed)
                assertFalse(controller.state.value.canSubmit)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun observedUnverifiedStateAlsoDropsTheRetryNotice() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            try {
                controller.bind(fixture.result())
                runCurrent()

                fixture.ratings.submitBehavior = { _, _, _ -> Result.failure(IllegalStateException("offline")) }
                controller.submit(2)
                runCurrent()
                assertTrue(controller.state.value.submitFailed)

                fixture.ratings.emitAt(0, ArenaFrozenRatingState.ExistingUnverified)
                runCurrent()

                assertIs<ArenaFrozenRatingState.ExistingUnverified>(controller.state.value.local)
                assertFalse(controller.state.value.submitFailed)
                assertFalse(controller.state.value.canSubmit)
            } finally {
                controller.onDestroy()
            }
        }

    @Test
    fun destroyCancelsCollectorAndLateReceiptCannotRestoreRatingState() =
        runTest {
            val fixture = ArenaRunRatingFixture(this)
            val controller = fixture.controller()
            val result = fixture.result()
            controller.bind(result)
            runCurrent()

            controller.onDestroy()
            runCurrent()
            fixture.ratings.emitAt(0, ArenaFrozenRatingState.Accepted(2))
            runCurrent()

            assertEquals(1, fixture.ratings.cancellations)
            assertIs<ArenaFrozenRatingState.Unavailable>(controller.state.value.local)
        }
}

private class ArenaRunRatingFixture(scope: kotlinx.coroutines.test.TestScope) {
    var session = ArenaSessionStamp("owner-a", 1)
    val ratings = FakeArenaFrozenRatingRepository()
    val sync = RatingSyncScheduler()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)

    fun controller(): ArenaRunRating =
        ArenaRunRating(
            ratings,
            ArenaSessionProvider { session },
            sync,
            dispatcher,
        )

    fun result(
        settlement: ServerAttemptSettlement = confirmedSettlement(),
        stamp: ArenaSessionStamp = session,
        ticketId: String = "ticket-1",
        attemptId: String = "attempt-1",
        attemptOwner: String = stamp.ownerUid,
    ): ArenaVerifiedResult =
        ArenaVerifiedResult(
            stamp,
            ticketId,
            AttemptIdentity(
                AttemptId(attemptId),
                attemptOwner,
                LessonId("lesson-1"),
                7L,
                Difficulty.EASY,
            ),
            settlement,
        )
}

private class FakeArenaFrozenRatingRepository : ArenaFrozenRatingRepository {
    private val subscriptions = mutableListOf<MutableSharedFlow<Result<ArenaFrozenRatingState>>>()
    val observed = mutableListOf<RatingPortKey>()
    val submittedRatings = mutableListOf<Int>()
    var cancellations = 0
    var observationFailure: Throwable? = null
    var submitBehavior: suspend (ArenaSessionStamp, String, Int) -> Result<ArenaFrozenRatingState> =
        { _, _, rating -> Result.success(ArenaFrozenRatingState.Queued(rating)) }

    override fun observe(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Flow<ArenaFrozenRatingState> {
        observationFailure?.let { throw it }
        return flow {
            val source = MutableSharedFlow<Result<ArenaFrozenRatingState>>(replay = 1)
            source.tryEmit(Result.success(ArenaFrozenRatingState.Pending))
            observed += RatingPortKey(stamp, ticketId)
            subscriptions += source
            try {
                source.collect { emit(it.getOrThrow()) }
            } finally {
                cancellations++
            }
        }
    }

    override suspend fun submit(
        stamp: ArenaSessionStamp,
        ticketId: String,
        rating: Int,
    ): Result<ArenaFrozenRatingState> {
        submittedRatings += rating
        return submitBehavior(stamp, ticketId, rating)
    }

    fun emitAt(
        subscription: Int,
        state: ArenaFrozenRatingState,
    ) {
        subscriptions[subscription].tryEmit(Result.success(state))
    }

    /** Fails a live subscription, unlike [observationFailure] which refuses at subscribe time. */
    fun failAt(
        subscription: Int,
        error: Throwable,
    ) {
        subscriptions[subscription].tryEmit(Result.failure(error))
    }
}

private class RatingSyncScheduler : SyncScheduler {
    var requests = 0

    override fun enqueueManualSync() {
        requests++
    }

    override fun enqueueManualProfileSync() = Unit

    override fun applyFrequency(frequency: SyncFrequency) = Unit

    override fun applyProfileFrequency(frequency: SyncFrequency) = Unit
}

private data class RatingPortKey(
    val stamp: ArenaSessionStamp,
    val ticketId: String,
)
