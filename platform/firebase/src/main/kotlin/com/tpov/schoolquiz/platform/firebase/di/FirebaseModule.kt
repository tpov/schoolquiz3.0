package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.FirebaseUserStatsDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseCatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import org.koin.dsl.module

val firebaseModule =
    module {
        single<FirebaseFirestore> { FirebaseFirestore.getInstance() }
        single<UserStatsDataSource> {
            FirebaseUserStatsDataSource(
                firestore = get(),
                auth = FirebaseAuth.getInstance(),
            )
        }
        single<CatalogSyncChangeRemoteDataSource> {
            FirebaseCatalogSyncChangeRemoteDataSource(firestore = get())
        }
    }
