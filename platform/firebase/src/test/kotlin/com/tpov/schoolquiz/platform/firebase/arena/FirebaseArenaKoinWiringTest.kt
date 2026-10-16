package com.tpov.schoolquiz.platform.firebase.arena

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
import com.tpov.schoolquiz.platform.firebase.exam.FirebaseExamCertificateRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.exam.FirebaseExamSittingRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.network.FirebaseMutationTransport
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.TermsConsentVerdict
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyTermsConsentGate
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCancelledSettlementProof
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCancelledSettlementResolver
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaEntryLocalMapper
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.OutboxArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.arenaDataModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaLessonCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamSittingRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaCatalogRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaEntryRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaCatalogRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    fun examRemoteDataSourcesResolveFromTheProductionFirebaseModule() {
        val f = Fixture()
        val app = f.graph()
        try {
            // Экзамен (CAP-21/22): оба привязки поднимаются лениво из боевого модуля и не
            // трогают ни SDK, ни сессию, ни сеть при разрешении.
            val sittings = app.koin.get<ExamSittingRemoteDataSource>()
            assertTrue(sittings is FirebaseExamSittingRemoteDataSource)
            assertSame(sittings, app.koin.get<FirebaseExamSittingRemoteDataSource>())
            val certificates = app.koin.get<ExamCertificateRemoteDataSource>()
            assertTrue(certificates is FirebaseExamCertificateRemoteDataSource)
            assertSame(certificates, app.koin.get<FirebaseExamCertificateRemoteDataSource>())
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
        } finally {
            app.close()
        }
    }

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

    @Test
    fun resolvingCatalogRepositoryAndRemoteIsLazyWithoutJournalOrSdkEffects() {
        val f = Fixture()
        val app = f.graph()
        try {
            // No Room DAO, journal engine or clock is provided: resolving a read must not need them.
            val repository = app.koin.get<ArenaCatalogRepository>()
            assertTrue(repository is ArenaCatalogRepositoryImpl)
            assertSame(repository, app.koin.get<ArenaCatalogRepository>())
            val remote = app.koin.get<ArenaLessonCatalogRemoteDataSource>()
            assertTrue(remote is FirebaseArenaLessonCatalogRemoteDataSource)
            assertSame(remote, app.koin.get<ArenaLessonCatalogRemoteDataSource>())
            assertSame(remote, app.koin.get<FirebaseArenaLessonCatalogRemoteDataSource>())
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
    fun resolvedCatalogRepositoryPreservesActualRemoteOwnerEpochAndNetworkFences() =
        runTest {
            val f = Fixture()
            val app = f.graph()
            try {
                val repository = app.koin.get<ArenaCatalogRepository>()
                val request = ArenaLessonCatalogRequest("A")
                f.session = null
                val missing = repository.list(request).exceptionOrNull() as ArenaLessonCatalogFailure
                assertEquals(ArenaLessonCatalogFailureKind.ACCOUNT_UNAVAILABLE, missing.kind)
                assertEquals(0, f.networkReads)
                f.session = OutboxSession("B", 2)
                val foreign = repository.list(request).exceptionOrNull() as ArenaLessonCatalogFailure
                assertEquals(ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED, foreign.kind)
                assertEquals(0, f.networkReads)
                f.session = OutboxSession("A", 3)
                val offline = repository.list(request).exceptionOrNull() as SyncFailure
                assertEquals(SyncError.NoNetwork, offline.error)
                assertEquals(1, f.networkReads)
                f.changeSessionOnNetworkRead = true
                val changed = repository.list(request).exceptionOrNull() as ArenaLessonCatalogFailure
                assertEquals(ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED, changed.kind)
                assertEquals(2, f.networkReads)
                verify { f.functions wasNot Called }
            } finally {
                app.close()
            }
        }

    @Test
    fun missingNetworkPreventsCatalogRemoteAndRepositoryResolutionWithoutFallback() {
        val f = Fixture()
        val app = f.graph(includeNetwork = false)
        try {
            val remoteFailure = runCatching { app.koin.get<ArenaLessonCatalogRemoteDataSource>() }.exceptionOrNull()
            val repositoryFailure = runCatching { app.koin.get<ArenaCatalogRepository>() }.exceptionOrNull()
            for (failure in listOf(remoteFailure, repositoryFailure)) {
                assertTrue(generateSequence(failure) { it.cause }.any { it is NoDefinitionFoundException })
            }
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
        } finally {
            app.close()
        }
    }

    @Test
    fun actualGateEngineAndProductionTransportResolveWithoutStartingAnyRequests() {
        val f = Fixture()
        val app = f.graph()
        try {
            val remote = app.koin.get<ArenaTicketRemoteDataSource>()
            assertTrue(app.koin.get<ArenaOwnerGate>() is OutboxArenaOwnerGate)
            assertTrue(
                app.koin.get<MutationTransport>() is FirebaseMutationTransport,
            )
            assertSame(app.koin.get<OutboxEngine>(), app.koin.get<OutboxEngine>())
            assertSame(remote, app.koin.get<ArenaTicketStatusRemoteDataSource>())
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
        } finally {
            app.close()
        }
    }

    @Test
    fun missingEngineFailsTicketGateResolutionButCatalogStillNeedsNoJournalOrRoom() {
        val f = Fixture()
        val app = f.graph(includeEngine = false)
        try {
            for (resolve in listOf<() -> Any>(
                { app.koin.get<ArenaTicketRemoteDataSource>() },
                { app.koin.get<ArenaTicketStatusRemoteDataSource>() },
            )) {
                val failure = runCatching(resolve).exceptionOrNull()
                assertTrue(generateSequence(failure) { it.cause }.any { it is NoDefinitionFoundException })
            }
            assertTrue(app.koin.get<ArenaLessonCatalogRemoteDataSource>() is FirebaseArenaLessonCatalogRemoteDataSource)
            assertTrue(app.koin.get<ArenaCatalogRepository>() is ArenaCatalogRepositoryImpl)
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
        } finally {
            app.close()
        }
    }

    @Test
    fun missingGateDefinitionFailsClosedWhilePlainCatalogRemainsIndependent() {
        val f = Fixture()
        val app =
            KoinApplication.init().modules(firebaseModule).modules(
                module {
                    single<FirebaseFunctions> { f.functions }
                    single<OutboxSessionProvider> { f.sessions }
                    single<NetworkMonitor> { f.network }
                    single<EconomyTermsConsentGate> { f.noConsent }
                },
            )
        try {
            val failure = runCatching { app.koin.get<ArenaTicketRemoteDataSource>() }.exceptionOrNull()
            assertTrue(generateSequence(failure) { it.cause }.any { it is NoDefinitionFoundException })
            assertTrue(app.koin.get<ArenaLessonCatalogRemoteDataSource>() is FirebaseArenaLessonCatalogRemoteDataSource)
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
        } finally {
            app.close()
        }
    }

    @Test
    fun fullEntryGraphUsesActualTicketStatusGateEngineAndTransportWithoutStartupActions() {
        val f = Fixture()
        val app = f.graph(includeEntryPorts = true)
        try {
            val koin = app.koin
            val entry = koin.get<ArenaEntryRepository>()
            assertTrue(entry is ArenaEntryRepositoryImpl)
            assertSame(entry, koin.get<ArenaEntryRepository>())
            assertSame(koin.get<ArenaEntryLocalMapper>(), koin.get<ArenaEntryLocalMapper>())
            assertSame(koin.get<ArenaCancelledSettlementProof>(), koin.get<ArenaCancelledSettlementProof>())
            assertSame(koin.get<ArenaCancelledSettlementResolver>(), koin.get<ArenaCancelledSettlementResolver>())
            assertSame(koin.get<ArenaSessionProvider>(), koin.get<ArenaSessionProvider>())
            val ticket = koin.get<ArenaTicketRemoteDataSource>()
            assertTrue(ticket is FirebaseArenaTicketRemoteDataSource)
            assertSame(ticket, koin.get<ArenaTicketStatusRemoteDataSource>())
            assertSame(ticket, koin.get<FirebaseArenaTicketRemoteDataSource>())
            assertTrue(koin.get<ArenaOwnerGate>() is OutboxArenaOwnerGate)
            assertTrue(koin.get<MutationTransport>() is FirebaseMutationTransport)
            assertSame(koin.get<OutboxEngine>(), koin.get<OutboxEngine>())
            assertSame(f.sessions, koin.get<OutboxSessionProvider>())
            assertEquals(0, f.clockReads)
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
            verify { f.dao wasNot Called }
        } finally {
            app.close()
        }
    }

    @Test
    fun missingExistingEngineBlocksEntryThroughItsRequiredGateWhileCatalogRemainsIndependent() {
        val f = Fixture()
        val app = f.graph(includeEngine = false, includeEntryPorts = true)
        try {
            for (resolve in listOf<() -> Any>(
                { app.koin.get<ArenaOwnerGate>() },
                { app.koin.get<ArenaEntryRepository>() },
                { app.koin.get<ArenaCancelledSettlementResolver>() },
            )) {
                val failure = runCatching(resolve).exceptionOrNull()
                assertTrue(generateSequence(failure) { it.cause }.any { it is NoDefinitionFoundException })
            }
            assertTrue(app.koin.get<ArenaCatalogRepository>() is ArenaCatalogRepositoryImpl)
            assertTrue(app.koin.get<ArenaLessonCatalogRemoteDataSource>() is FirebaseArenaLessonCatalogRemoteDataSource)
            assertEquals(0, f.clockReads)
            assertEquals(0, f.sessionReads)
            assertEquals(0, f.networkReads)
            verify { f.functions wasNot Called }
            verify { f.dao wasNot Called }
        } finally {
            app.close()
        }
    }

    private class Fixture {
        val functions = mockk<FirebaseFunctions>()
        val dao = mockk<ArenaTicketDao>()
        var clockReads = 0
        val clock =
            object : Clock {
                override fun now(): Instant {
                    clockReads++
                    return Instant.fromEpochMilliseconds(1000)
                }
            }
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

        // Транспорт несёт capturedTerms (CAP-16): здешний граф поднимает firebaseModule целиком,
        // и согласие в этих тестах — «нет и не спрашиваем».
        val noConsent =
            object : EconomyTermsConsentGate {
                override fun capturedTerms(): PayableTerms? = null

                override fun verdict(): TermsConsentVerdict = TermsConsentVerdict.Required(PayableTerms.BOOTSTRAP, change = null)

                override suspend fun acceptCurrentTerms() = Unit
            }

        fun graph(
            includeNetwork: Boolean = true,
            includeEngine: Boolean = true,
            includeEntryPorts: Boolean = false,
        ): KoinApplication =
            KoinApplication.init().modules(firebaseModule, arenaDataModule).modules(
                module {
                    if (includeEngine) single<OutboxEngine> { arenaSessionTestEngine(get(), get<MutationTransport>()) }
                    single<FirebaseFunctions> { functions }
                    if (includeEntryPorts) {
                        single<ArenaTicketDao> { dao }
                        single<Clock> { clock }
                    }
                    single<OutboxSessionProvider> { sessions }
                    single<EconomyTermsConsentGate> { noConsent }
                    if (includeNetwork) single<NetworkMonitor> { network }
                },
            )
    }
}
