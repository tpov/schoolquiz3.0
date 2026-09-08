package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaWalletRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaWalletRepositoryImplTest {
    private val original = ArenaSessionStamp("A", 1)

    private fun observation(stamp: ArenaSessionStamp = original) =
        ArenaWalletObservation(
            stamp,
            1000,
            70,
            30,
            400,
            100,
            123,
        )

    @Test fun preservesOriginalStampAndEveryBalanceWithoutAnyLocalDeduction() =
        runTest {
            val calls = mutableListOf<OutboxSession>()
            val repository =
                ArenaWalletRepositoryImpl(
                    ArenaWalletRemoteDataSource {
                        calls += it
                        Result.success(observation())
                    },
                    ArenaSessionProvider { original },
                    StandardTestDispatcher(testScheduler),
                )
            assertTrue(calls.isEmpty())
            val result = repository.read(original).getOrThrow()
            assertEquals(listOf(OutboxSession("A", 1)), calls)
            assertEquals(original, result.stamp)
            assertEquals(1000L, result.observedAtMs)
            assertEquals(70L, result.availableNolics)
            assertEquals(30L, result.reservedNolics)
            assertEquals(400L, result.storedLifePoints)
            assertEquals(100L, result.reservedLifePoints)
            assertEquals(123L, result.lifePointsUpdatedAtMs)
        }

    @Test fun oldEpochNeverReadsAndMidReadSwitchNeverReturnsAnotherSessionsBalance() =
        runTest {
            var current = original
            var calls = 0
            val repository =
                ArenaWalletRepositoryImpl(
                    ArenaWalletRemoteDataSource {
                        calls++
                        current = original.copy(generation = 3)
                        Result.success(observation())
                    },
                    ArenaSessionProvider { current },
                    StandardTestDispatcher(testScheduler),
                )
            assertEquals(
                ArenaWalletFailureKind.ACCOUNT_CHANGED,
                (repository.read(original.copy(generation = 0)).exceptionOrNull() as ArenaWalletFailure).kind,
            )
            assertEquals(0, calls)
            assertEquals(
                ArenaWalletFailureKind.ACCOUNT_CHANGED,
                (repository.read(original).exceptionOrNull() as ArenaWalletFailure).kind,
            )
            assertEquals(1, calls)
        }

    @Test fun dispatcherReturnRejectsAnEpochChangedAfterTheIoBlockCheckedIt() =
        runTest {
            var current = original
            val repository =
                ArenaWalletRepositoryImpl(
                    ArenaWalletRemoteDataSource {
                        // Runs before withContext resumes its caller, but after its IO checks.
                        this@runTest.launch { current = original.copy(generation = 3) }
                        Result.success(observation())
                    },
                    ArenaSessionProvider { current },
                    StandardTestDispatcher(testScheduler),
                )
            assertEquals(
                ArenaWalletFailureKind.ACCOUNT_CHANGED,
                (repository.read(original).exceptionOrNull() as ArenaWalletFailure).kind,
            )
        }

    @Test fun malformedRemoteStampIsNotAValidObservation() =
        runTest {
            val repository =
                ArenaWalletRepositoryImpl(
                    ArenaWalletRemoteDataSource { Result.success(observation(original.copy(ownerUid = "B"))) },
                    ArenaSessionProvider { original },
                    StandardTestDispatcher(testScheduler),
                )
            assertEquals(
                ArenaWalletFailureKind.NEEDS_REFRESH,
                (repository.read(original).exceptionOrNull() as ArenaWalletFailure).kind,
            )
        }

    @Test fun missingProfileRemainsMissingAndNoFallbackWalletIsCreated() =
        runTest {
            val repository =
                ArenaWalletRepositoryImpl(
                    ArenaWalletRemoteDataSource {
                        Result.failure(
                            ArenaWalletFailure(ArenaWalletFailureKind.MISSING_PROFILE),
                        )
                    },
                    ArenaSessionProvider { original },
                    StandardTestDispatcher(testScheduler),
                )
            assertEquals(
                ArenaWalletFailureKind.MISSING_PROFILE,
                (repository.read(original).exceptionOrNull() as ArenaWalletFailure).kind,
            )
        }

    @Test fun cancellationIsNotSwallowedAndUnexpectedErrorsAreSanitized() =
        runTest {
            var cancelled = true
            val repository =
                ArenaWalletRepositoryImpl(
                    ArenaWalletRemoteDataSource {
                        if (cancelled) {
                            throw CancellationException(
                                "cancel",
                            )
                        } else {
                            error("private")
                        }
                    },
                    ArenaSessionProvider { original },
                    StandardTestDispatcher(testScheduler),
                )
            assertFailsWith<CancellationException> { repository.read(original) }
            cancelled = false
            val failure = repository.read(original).exceptionOrNull() as ArenaWalletFailure
            assertEquals(ArenaWalletFailureKind.UNAVAILABLE, failure.kind)
            assertNull(failure.cause)
        }
}
