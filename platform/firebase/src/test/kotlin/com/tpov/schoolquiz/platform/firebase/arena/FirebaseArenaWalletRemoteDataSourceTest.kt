package com.tpov.schoolquiz.platform.firebase.arena

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Source
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.OutboxArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletFailureKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class FirebaseArenaWalletRemoteDataSourceTest {
    @Test fun explicitReadsStartOnceUnderTheGateAndAwaitWithoutHoldingIt() =
        runTest {
            val f = Fixture(this)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            f.awaitAction = {
                entered.complete(Unit)
                release.await()
                f.document
            }
            val read = async { f.remote.read(f.original) }
            entered.await()
            withTimeout(1000) { f.gate.withOwner(f.original) { Unit } }
            release.complete(Unit)
            val observation = read.await().getOrThrow()
            assertEquals(70L, observation.availableNolics)
            assertEquals(30L, observation.reservedNolics)
            assertEquals(1, f.starts)
            assertEquals(listOf("A"), f.owners)
        }

    @Test fun switchedAwayAndBackDuringNetworkWaitDiscardsTheResponse() =
        runTest {
            val f = Fixture(this)
            f.awaitAction = {
                f.session = OutboxSession("A", 3)
                f.document
            }
            failure(f.remote.read(f.original), ArenaWalletFailureKind.ACCOUNT_CHANGED)
            assertEquals(1, f.starts)
        }

    @Test fun switchedDuringConnectivityCheckNeverStartsSdkRequest() =
        runTest {
            val f = Fixture(this)
            f.onNetwork = { f.session = OutboxSession("B", 2) }
            failure(f.remote.read(f.original), ArenaWalletFailureKind.ACCOUNT_CHANGED)
            assertEquals(0, f.starts)
        }

    @Test fun absentOrStaleSessionAndOfflineAreDistinctAndNeverReadWallet() =
        runTest {
            val f = Fixture(this)
            f.session = null
            failure(f.remote.read(f.original), ArenaWalletFailureKind.ACCOUNT_UNAVAILABLE)
            f.session = OutboxSession("A", 4)
            failure(f.remote.read(f.original), ArenaWalletFailureKind.ACCOUNT_CHANGED)
            f.session = f.original
            f.online = false
            failure(f.remote.read(f.original), ArenaWalletFailureKind.NO_NETWORK)
            assertEquals(0, f.starts)
        }

    @Test fun sdkFailureIsSanitizedAndAccountSwitchWinsOverItsPrivateError() =
        runTest {
            val f = Fixture(this)
            f.awaitAction = { error("private document contents") }
            val result = f.remote.read(f.original)
            failure(result, ArenaWalletFailureKind.UNAVAILABLE)
            assertNull(result.exceptionOrNull()?.cause)
            assertFalse(result.exceptionOrNull().toString().contains("private document"))
            f.awaitAction = {
                f.session = OutboxSession("A", 9)
                error("private")
            }
            failure(f.remote.read(f.original), ArenaWalletFailureKind.ACCOUNT_CHANGED)
        }

    @Test fun cancellationEscapesInsteadOfBecomingAnUnknownWallet() =
        runTest {
            val f = Fixture(this)
            val cancellation = CancellationException("cancel read")
            f.awaitAction = { throw cancellation }
            val error = runCatching { f.remote.read(f.original) }.exceptionOrNull()
            assertTrue(error === cancellation || error?.cause === cancellation)
        }

    @Test fun switchWhileStampingReceptionTimeIsStillFenced() =
        runTest {
            val f = Fixture(this)
            f.onClock = { f.session = OutboxSession("A", 8) }
            failure(f.remote.read(f.original), ArenaWalletFailureKind.ACCOUNT_CHANGED)
        }

    @Test fun dispatcherReturnRejectsAnEpochChangedAfterTheParsedObservationPassedItsFence() =
        runTest {
            val f = Fixture(this)
            f.onClock = {
                // Queue the switch before withContext posts its return; the IO block still sees A/1.
                this@runTest.launch { f.session = OutboxSession("A", 3) }
            }
            failure(f.remote.read(f.original), ArenaWalletFailureKind.ACCOUNT_CHANGED)
            assertEquals(1, f.starts)
        }

    @Test fun actualSdkBoundaryUsesOriginalUsersPathAndServerSourceOnly() =
        runTest {
            val f = Fixture(this)
            val firestore = mockk<FirebaseFirestore>()
            val collection = mockk<CollectionReference>()
            val reference = mockk<DocumentReference>()
            val snapshot = mockk<DocumentSnapshot>()
            val metadata = mockk<SnapshotMetadata>()
            every { firestore.collection("users") } returns collection
            every { collection.document("A") } returns reference
            every { reference.path } returns "users/A"
            every { reference.get(Source.SERVER) } returns Tasks.forResult(snapshot)
            every { snapshot.data } returns walletFields()
            every { snapshot.metadata } returns metadata
            every { metadata.isFromCache } returns false
            every { metadata.hasPendingWrites() } returns false
            val reader =
                FirebaseArenaWalletRemoteDataSource(firestore, f.sessions, f.network, f.gate, f.dispatcher, f.clock)
            assertEquals(70L, reader.read(f.original).getOrThrow().availableNolics)
            verify(exactly = 1) {
                firestore.collection("users")
                collection.document("A")
                reference.get(Source.SERVER)
            }
            verify(exactly = 0) { reference.get() }
        }

    private fun failure(
        result: Result<*>,
        expected: ArenaWalletFailureKind,
    ) {
        assertEquals(expected, (result.exceptionOrNull() as ArenaWalletFailure).kind)
    }

    private class Fixture(scope: TestScope) {
        val original = OutboxSession("A", 1)
        var session: OutboxSession? = original
        val sessions = OutboxSessionProvider { session }
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val gate = OutboxArenaOwnerGate(sessions, arenaSessionTestEngine(sessions))
        var online = true
        var onNetwork: () -> Unit = {}
        var onClock: () -> Unit = {}
        var starts = 0
        val owners = mutableListOf<String>()
        val document = ArenaWalletDocument("users/A", walletFields(), false, false)
        var awaitAction: suspend () -> ArenaWalletDocument = { document }
        val clock =
            object : Clock {
                override fun now(): Instant {
                    onClock()
                    return Instant.fromEpochMilliseconds(1000)
                }
            }
        val network =
            object : NetworkMonitor {
                override fun observeOnline() = flowOf(online)

                override suspend fun isOnline(): Boolean {
                    onNetwork()
                    return online
                }
            }
        val remote =
            FirebaseArenaWalletRemoteDataSource(
                ArenaWalletExchange { owner ->
                    starts++
                    owners += owner
                    ArenaPendingWalletRead { awaitAction() }
                },
                sessions,
                network,
                gate,
                dispatcher,
                clock,
            )
    }
}
