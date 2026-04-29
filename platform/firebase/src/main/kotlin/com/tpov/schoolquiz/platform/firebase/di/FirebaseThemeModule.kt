package com.tpov.schoolquiz.platform.firebase.di

import com.tpov.schoolquiz.platform.firebase.theme.FirebaseThemeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.theme.data.ThemeRemoteDataSource
import org.koin.dsl.module

val firebaseThemeModule =
    module {
        single<ThemeRemoteDataSource> { FirebaseThemeRemoteDataSource(get()) }
    }
