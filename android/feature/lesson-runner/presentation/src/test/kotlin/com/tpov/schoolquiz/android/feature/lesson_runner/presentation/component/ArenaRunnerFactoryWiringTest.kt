package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSyncScheduler
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestionWindowRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaFrozenRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArenaRunnerFactoryWiringTest {
    @Test
    fun resolvingArenaFactoryAndOrdinaryFactoryDoesNotStartOrReadAnything() =
        runTest {
            val f = RunCoordinatorFixture(this)
            val app = KoinApplication.init().modules(ports(f), clockModule, lessonRunnerPresentationModule)
            try {
                val factory = app.koin.get<ArenaRunnerComponentFactory>()
                assertSame(factory, app.koin.get<ArenaRunnerComponentFactory>())
                val ordinary = app.koin.get<LessonRunnerComponentFactory>()
                assertSame(ordinary, app.koin.get<LessonRunnerComponentFactory>())
                assertEquals(0, f.freshCalls + f.readCalls + f.freezeCalls)
                assertTrue(f.candidates.isEmpty())
            } finally {
                app.close()
            }
        }

    @Test
    fun missingClockOrRunPortCannotFallBackToSystemClockOrOrdinaryRunner() =
        runTest {
            for (omitClock in listOf(true, false)) {
                val f = RunCoordinatorFixture(this)
                val dependencies = ports(f, includeRun = omitClock)
                val app = KoinApplication.init().modules(dependencies, lessonRunnerPresentationModule)
                if (!omitClock) app.modules(clockModule)
                try {
                    val error = assertFailsWith<Exception> { app.koin.get<ArenaRunnerComponentFactory>() }
                    assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it is NoDefinitionFoundException })
                    assertEquals(0, f.freshCalls + f.readCalls)
                } finally {
                    app.close()
                }
            }
        }

    @Test
    fun arenaFactoryGraphBuildsWithTheOnlineO2WindowServiceArmed() =
        runTest {
            // Ретро A1: сборка с окном и монитором связи проходит, разрешение остаётся ленивым —
            // ни окно, ни связь не читаются, пока игрок не тапнёт Continue-из-паузы (CAP-1).
            val f = RunCoordinatorFixture(this)
            val app =
                KoinApplication.init().modules(
                    ports(f),
                    clockModule,
                    module {
                        single<QuestionWindowRemoteDataSource> {
                            object : QuestionWindowRemoteDataSource {
                                override suspend fun beginWindow(request: QuestionWindowRequest) =
                                    error("No window call during resolution")

                                override suspend fun fetchNext(
                                    attemptId: String,
                                    completedQuestionIndex: Int,
                                ) = error("No window call during resolution")
                            }
                        }
                        single<NetworkMonitor> {
                            object : NetworkMonitor {
                                override fun observeOnline() = error("No network read during resolution")

                                override suspend fun isOnline() = error("No network read during resolution")
                            }
                        }
                    },
                    lessonRunnerPresentationModule,
                )
            try {
                val factory = app.koin.get<ArenaRunnerComponentFactory>()
                assertSame(factory, app.koin.get<ArenaRunnerComponentFactory>())
                assertEquals(0, f.freshCalls + f.readCalls + f.freezeCalls)
                assertTrue(f.candidates.isEmpty())
            } finally {
                app.close()
            }
        }

    private fun ports(
        f: RunCoordinatorFixture,
        includeRun: Boolean = true,
    ) = module {
        if (includeRun) single<ArenaRunRepository> { f.repository }
        single<ArenaEntryRepository> { HostEntryFake(f) }
        single<ArenaSessionProvider> { ArenaSessionProvider { error("No session sampling during resolution") } }
        single<ArenaSessionObserver> { ArenaSessionObserver { error("No session observation during resolution") } }
        single<ArenaWalletRepository> { ArenaWalletRepository { error("No wallet read during resolution") } }
        single<SyncScheduler> { FakeSyncScheduler() }
        single<ServerAttemptSettlementRepository> { HostSettlementFake() }
        single<ArenaFrozenRatingRepository> { HostArenaFrozenRatingFake() }
    }

    private val clockModule =
        module {
            single<Clock> {
                object : Clock {
                    override fun now(): Instant = error("No clock read during resolution")
                }
            }
        }
}
