package com.tpov.schoolquiz.shared.feature.section.domain.di

import com.tpov.schoolquiz.shared.feature.section.domain.use_case.SyncSectionsUseCase
import org.koin.dsl.module

val sectionDomainModule = module {
    factory { SyncSectionsUseCase(get()) }
}
