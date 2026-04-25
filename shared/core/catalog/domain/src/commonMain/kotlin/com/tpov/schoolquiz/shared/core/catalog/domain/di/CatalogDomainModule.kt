package com.tpov.schoolquiz.shared.core.catalog.domain.di

import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import org.koin.dsl.module

val catalogDomainModule = module {
    factory { ObserveCatalogsUseCase(get()) }
}
