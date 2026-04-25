package com.tpov.schoolquiz.shared.feature.quest.data.di

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.feature.quest.data.QuestLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.QuestLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest.data.QuestRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val questDataModule = module {
    single<QuestLocalDataSource> { QuestLocalDataSourceImpl(get()) }
    single<QuestRepository> {
        QuestRepositoryImpl(
            local = get(),
            remote = get(),
            storageUrlResolver = get<StorageUrlResolver>(named("storageUrlResolver")),
        )
    }
}
