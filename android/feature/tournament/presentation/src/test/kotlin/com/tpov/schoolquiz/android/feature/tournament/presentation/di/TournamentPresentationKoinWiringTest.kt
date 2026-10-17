package com.tpov.schoolquiz.android.feature.tournament.presentation.di

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAgreementComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBracketComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentMatchPlayComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentQualificationComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentResultsComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeOutboxStore
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeReceiptStore
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeTournamentRepository
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxRepository
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionReceiptParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentEventWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentMeetingWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentSessionWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.core.parameter.parametersOf
import kotlin.test.assertNotNull

/**
 * Проводка Koin (истории 5.10 и 5.11): восемь маршрутов разрешаются из
 * [tournamentPresentationModule] над готовым 5.9-слоем — репозиторий, очередь и провайдер
 * сессии приходят снаружи.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TournamentPresentationKoinWiringTest {
    private val dispatcher = StandardTestDispatcher()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `all eight journeys resolve over the shared 5_9 layer`() {
        Dispatchers.setMain(dispatcher)
        val app =
            KoinApplication.init().modules(
                tournamentPresentationModule,
                module {
                    single<TournamentCompetitionRepository> { FakeTournamentRepository() }
                    single {
                        TournamentSessionOutboxRepository(
                            outbox = FakeOutboxStore(),
                            receipts = FakeReceiptStore(),
                            sessions = OutboxSessionProvider { OutboxSession("owner-1", 1) },
                            parser =
                                TournamentSessionReceiptParser(
                                    TournamentSessionWireParser(TournamentEventWireParser(), TournamentMeetingWireParser()),
                                ),
                            clock = { 1L },
                        )
                    }
                    single<OutboxSessionProvider> { OutboxSessionProvider { OutboxSession("owner-1", 1) } }
                },
            )
        val lifecycle = LifecycleRegistry().apply { resume() }
        try {
            val context = DefaultComponentContext(lifecycle)
            assertNotNull(app.koin.get<TournamentOrganizerComponent> { parametersOf(context) })
            assertNotNull(app.koin.get<TournamentBrowserComponent> { parametersOf(context) })
            assertNotNull(app.koin.get<TournamentAdmissionComponent> { parametersOf(context) })
            assertNotNull(app.koin.get<TournamentQualificationComponent> { parametersOf(context) })
            assertNotNull(app.koin.get<TournamentAgreementComponent> { parametersOf(context) })
            assertNotNull(app.koin.get<TournamentMatchPlayComponent> { parametersOf(context) })
            assertNotNull(app.koin.get<TournamentBracketComponent> { parametersOf(context) })
            assertNotNull(app.koin.get<TournamentResultsComponent> { parametersOf(context) })
        } finally {
            lifecycle.destroy()
            app.close()
        }
    }
}
