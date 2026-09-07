package com.tpov.schoolquiz.platform.firebase.arena

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.module

/** Exercise the real Firebase module with SDK objects supplied by the test, never Firebase startup. */
class FirebaseArenaKoinWiringTest {
    @Test
    fun resolvingProductionRemoteIsLazyAndDoesNotCallFunctionsNetworkOrSession() {
        val f = Fixture()
        val app = f.graph()
        try {
            val remote = app.koin.get<ArenaTicketRemoteDataSource>()
            assertTrue(remote is FirebaseArenaTicketRemoteDataSource)
            assertSame(remote, app.koin.get<ArenaTicketRemoteDataSource>())
            assertSame(remote, app.koin.get<FirebaseArenaTicketRemoteDataSource>())
            assertSame(remote, app.koin.get<ArenaTicketStatusRemoteDataSource>())
            assertSame(f.functions, app.koin.get<FirebaseFunctions>())
            assertSame(f.sessions, app.koin.get<OutboxSessionProvider>())
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
        } finally {
            app.close()
        }
    }

    @Test
    fun resolvedRemoteUsesBoundOwnerGenerationAndNetworkBeforeAnySdkCall() =
        runTest {
            val f = Fixture()
            val app = f.graph()
            try {
                val remote = app.koin.get<ArenaTicketRemoteDataSource>()
                val request = ArenaTicketGetRequest("A", "a".repeat(64))
                f.session = OutboxSession("B", 2)
                val foreign = remote.get(request).exceptionOrNull() as ArenaRemoteFailure
                assertEquals(ArenaRemoteFailureKind.ACCOUNT_CHANGED, foreign.kind)
                assertEquals(0, f.networkReads)
                f.session = OutboxSession("A", 3)
                val offline = remote.get(request).exceptionOrNull() as SyncFailure
                assertEquals(SyncError.NoNetwork, offline.error)
                assertEquals(1, f.networkReads)
                f.changeSessionOnNetworkRead = true
                val changed = remote.get(request).exceptionOrNull() as ArenaRemoteFailure
                assertEquals(ArenaRemoteFailureKind.ACCOUNT_CHANGED, changed.kind)
                assertEquals(2, f.networkReads)
                verify { f.functions wasNot Called }
            } finally {
                app.close()
            }
        }

    @Test
    fun missingNetworkBindingFailsResolutionInsteadOfInventingConnectivity() {
        val f = Fixture()
        val app = f.graph(includeNetwork = false)
        try {
            val failure = runCatching { app.koin.get<ArenaTicketRemoteDataSource>() }.exceptionOrNull()
            assertTrue(generateSequence(failure) { it.cause }.any { it is NoDefinitionFoundException })
            assertEquals(0, f.sessionReads)
            verify { f.functions wasNot Called }
        } finally {
            app.close()
        }
    }

    @Test
    fun statusFirstResolutionUsesTheSameConcreteInstanceAndOwnerNetworkGuards() =
        runTest {
            val f = Fixture()
            val app = f.graph()
            try {
                val status = app.koin.get<ArenaTicketStatusRemoteDataSource>()
                assertSame(status, app.koin.get<ArenaTicketRemoteDataSource>())
                assertSame(status, app.koin.get<FirebaseArenaTicketRemoteDataSource>())
                assertEquals(0, f.sessionReads)
                assertEquals(0, f.networkReads)
                val request = ArenaTicketGetRequest("A", "a".repeat(64))
                f.session = OutboxSession("B", 2)
                val foreign = status.status(request).exceptionOrNull() as ArenaRemoteFailure
                assertEquals(ArenaRemoteFailureKind.ACCOUNT_CHANGED, foreign.kind)
                assertEquals(0, f.networkReads)
                f.session = OutboxSession("A", 3)
                val offline = status.status(request).exceptionOrNull() as SyncFailure
                assertEquals(SyncError.NoNetwork, offline.error)
                f.changeSessionOnNetworkRead = true
                val changed = status.status(request).exceptionOrNull() as ArenaRemoteFailure
                assertEquals(ArenaRemoteFailureKind.ACCOUNT_CHANGED, changed.kind)
                verify { f.functions wasNot Called }
            } finally {
                app.close()
            }
        }

    @Test
    fun missingNetworkPreventsStatusPortResolutionWithoutCreatingAnotherProvider() {
        val f = Fixture()
        val app = f.graph(includeNetwork = false)
        try {
            val failure = runCatching { app.koin.get<ArenaTicketStatusRemoteDataSource>() }.exceptionOrNull()
            assertTrue(generateSequence(failure) { it.cause }.any { it is NoDefinitionFoundException })
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
        } finally {
            app.close()
        }
    }

    private class Fixture {
        val functions = mockk<FirebaseFunctions>()
        var session: OutboxSession? = OutboxSession("A", 1)
        var sessionReads = 0
        var networkReads = 0
        var changeSessionOnNetworkRead = false
        val sessions =
            OutboxSessionProvider {
                sessionReads++
                session
            }
        val network =
            object : NetworkMonitor {
                override fun observeOnline() = flowOf(false)

                override suspend fun isOnline(): Boolean {
                    networkReads++
                    if (changeSessionOnNetworkRead) session = OutboxSession("A", 4)
                    return false
                }
            }

        fun graph(includeNetwork: Boolean = true): KoinApplication =
            KoinApplication.init().modules(firebaseModule).modules(
                module {
                    single<FirebaseFunctions> { functions }
                    single<OutboxSessionProvider> { sessions }
                    if (includeNetwork) single<NetworkMonitor> { network }
                },
            )
    }
}
