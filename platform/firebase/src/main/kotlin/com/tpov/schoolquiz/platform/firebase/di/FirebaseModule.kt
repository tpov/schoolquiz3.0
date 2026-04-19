package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.FirebaseUserStatsDataSource
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import org.koin.dsl.module

val firebaseModule =
    module {
        single<UserStatsDataSource> {
            FirebaseUserStatsDataSource(
                firestore = FirebaseFirestore.getInstance(),
                auth = FirebaseAuth.getInstance(),
            )
        }
    }
