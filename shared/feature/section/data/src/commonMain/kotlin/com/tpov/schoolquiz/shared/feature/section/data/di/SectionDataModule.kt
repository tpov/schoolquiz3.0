package com.tpov.schoolquiz.shared.feature.section.data.di

import com.tpov.schoolquiz.shared.feature.section.data.SectionLocalDataSource
import com.tpov.schoolquiz.shared.feature.section.data.SectionLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.section.data.SectionRepositoryImpl
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import org.koin.dsl.module

val sectionDataModule = module {
    single<SectionLocalDataSource> { SectionLocalDataSourceImpl(get()) }
    single<SectionRepository> { SectionRepositoryImpl(local = get(), remote = get()) }
}
