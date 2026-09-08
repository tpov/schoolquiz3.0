package com.tpov.schoolquiz.platform.firebase.arena

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.arenaDataModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaWalletRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Clock
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.module

class ArenaWalletKoinTest {
    @Test fun resolvingTheWalletGraphIsLazyAndSharesTheRequiredDependencies() {
        val firestore = mockk<FirebaseFirestore>()
        val sessions = OutboxSessionProvider { error("No session read on construction") }
        val network = mockk<NetworkMonitor>()
        val gate = mockk<ArenaOwnerGate>()
        val clock = mockk<Clock>()
        val app =
            KoinApplication.init().modules(firebaseModule, arenaDataModule).modules(
                module {
                    single<FirebaseFirestore> { firestore }
                    single<OutboxSessionProvider> { sessions }
                    single<NetworkMonitor> { network }
                    single<ArenaOwnerGate> { gate }
                    single<Clock> { clock }
                },
            )
        try {
            assertNotNull(app.koin.get<ArenaWalletRepository>())
            assertTrue(app.koin.get<ArenaWalletRemoteDataSource>() is FirebaseArenaWalletRemoteDataSource)
            assertSame(app.koin.get<ArenaWalletRepository>(), app.koin.get<ArenaWalletRepository>())
            verify {
                firestore wasNot Called
                network wasNot Called
                gate wasNot Called
                clock wasNot Called
            }
        } finally {
            app.close()
        }
    }

    @Test fun missingGateCannotResolveAReaderWithAPermissiveFallback() {
        val app =
            KoinApplication.init().modules(firebaseModule, arenaDataModule).modules(
                module {
                    single<FirebaseFirestore> { mockk<FirebaseFirestore>() }
                    single<OutboxSessionProvider> { OutboxSessionProvider { OutboxSession("A", 1) } }
                    single<NetworkMonitor> { mockk<NetworkMonitor>() }
                    single<Clock> { mockk<Clock>() }
                },
            )
        try {
            val failure = runCatching { app.koin.get<ArenaWalletRepository>() }.exceptionOrNull()
            assertTrue(generateSequence(failure) { it.cause }.any { it is NoDefinitionFoundException })
        } finally {
            app.close()
        }
    }
}
