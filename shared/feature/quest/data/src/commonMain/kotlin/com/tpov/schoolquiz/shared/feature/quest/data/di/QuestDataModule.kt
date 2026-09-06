package com.tpov.schoolquiz.shared.feature.quest.data.di

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.quest.data.QuestLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.QuestLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest.data.QuestRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest.data.outbox.QuestShelfQuarantineMark
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.datetime.Clock
import org.koin.core.qualifier.named
import org.koin.dsl.module

val questDataModule =
    module {
        single<QuestLocalDataSource> { QuestLocalDataSourceImpl(get()) }
        single<QuestRepository> {
            QuestRepositoryImpl(
                local = get(),
                remote = get(),
                storageUrlResolver = get<StorageUrlResolver>(named("storageUrlResolver")),
                // Перенос на полку уезжает общей очередью (AD-5, AD-6); локальной половины у него нет.
                outbox = get<OutboxStore>(),
                clock = Clock.System,
            )
        }
        // Реакция на карантин переноса `quest.SET_SHELF` — решение владеющей фичи (AD-28), той же, чьё
        // имя стоит в операции (NFR4). Ядру она отдаётся в composition root, где собирается
        // маршрутизатор карантина. Откатывать нечего (AD-25): пометка на карточке — сама запись.
        single { QuestShelfQuarantineMark() }
    }
