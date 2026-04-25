package com.tpov.schoolquiz.shared.core.catalog.data.di

import com.tpov.schoolquiz.shared.core.catalog.data.CatalogLocalDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogLocalDataSourceImpl
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogRepositoryImpl
import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val catalogDataModule = module {
    single<CatalogLocalDataSource> { CatalogLocalDataSourceImpl(get()) }
    single<CatalogRepository> {
        CatalogRepositoryImpl(
            local = get(),
            remote = get(),
            storageUrlResolver = get<StorageUrlResolver>(named("storageUrlResolver")),
            syncStateRepo = get(),
        )
    }
}
