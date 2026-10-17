package com.tpov.schoolquiz.shared.feature.tournament.data.di

import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxRepository
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionReceiptParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentEventWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentMeetingWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentSessionWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepositoryImpl
import kotlinx.datetime.Clock
import org.koin.dsl.module

/**
 * Сборка данных соревнования: строгие разборы проекций и репозитории. Порты транспорта
 * (`Tournament*RemoteDataSource`) сюда не входят — их реализует платформа Firebase и привязывает
 * `FirebaseModule`; этот модуль только потребляет их по интерфейсам домена. Очередь, хранилище
 * квитанций и провайдер сессии — общие (`persistenceModule`, `firebaseModule`), своих часов модуль
 * не регистрирует.
 */
val tournamentDataModule =
    module {
        single { TournamentEventWireParser() }
        single { TournamentMeetingWireParser() }
        single { TournamentSessionWireParser(events = get(), meetings = get()) }
        single { TournamentSessionReceiptParser(views = get()) }
        single<TournamentCompetitionRepository> {
            TournamentCompetitionRepositoryImpl(
                events = get(),
                meetings = get(),
                sessionReads = get(),
                operations = get(),
            )
        }
        single {
            TournamentSessionOutboxRepository(
                outbox = get(),
                receipts = get(),
                sessions = get(),
                parser = get(),
                clock = { Clock.System.now().toEpochMilliseconds() },
            )
        }
    }
