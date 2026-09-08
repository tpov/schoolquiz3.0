package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaCatalogRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArenaEntryKoinWiringTest {
    @Test
    fun productionCatalogCreatesEntryWithoutImplicitReadOrFinancialMutation() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                ArenaEntryFixture(dispatcher).use { f ->
                    var catalogReads = 0
                    val app =
                        KoinApplication.init().modules(
                            quizzesPresentationModule,
                            module {
                                single<ArenaCatalogRepository> {
                                    ArenaCatalogRepository {
                                        catalogReads++
                                        error("No implicit catalogue IO")
                                    }
                                }
                                single<AuthRepository> { FakeAuthRepository(f.stamp.ownerUid) }
                                single<ArenaEntryRepository> { f.repository }
                                single<ArenaWalletRepository> { f.dependencies.wallet }
                                single<ArenaSessionProvider> { f.sessions }
                                single<ArenaSessionObserver> { f.sessions }
                            },
                        )
                    val lifecycle = LifecycleRegistry()
                    val keeper = InstanceKeeperDispatcher()
                    try {
                        val factory = app.koin.get<ArenaLessonCatalogComponentFactory>()
                        val catalog = factory.create(DefaultComponentContext(lifecycle, instanceKeeper = keeper))
                        assertNotNull(catalog.entry)
                        lifecycle.resume()
                        runCurrent()
                        assertEquals(f.stamp, catalog.state.value.session)
                        assertEquals(f.stamp, catalog.entry?.state?.value?.session)
                        assertEquals(0, catalogReads)
                        assertEquals(0, f.walletCalls)
                        assertTrue(f.repository.calls.isEmpty())
                    } finally {
                        keeper.destroy()
                        lifecycle.stop()
                        lifecycle.destroy()
                        app.close()
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
