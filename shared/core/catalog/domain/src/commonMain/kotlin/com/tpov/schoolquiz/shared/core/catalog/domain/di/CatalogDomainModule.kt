package com.tpov.schoolquiz.shared.core.catalog.domain.di

import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.RefreshCatalogsUseCase
import org.koin.dsl.module

val catalogDomainModule =
    module {
        factory { ObserveCatalogsUseCase(get()) }
        factory { RefreshCatalogsUseCase(get()) }
    }
