package com.tpov.schoolquiz.platform.firebase.di

import com.tpov.schoolquiz.platform.firebase.section.FirebaseSectionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.section.data.SectionRemoteDataSource
import org.koin.dsl.module

val firebaseSectionModule = module {
    single<SectionRemoteDataSource> { FirebaseSectionRemoteDataSource(get()) }
}
