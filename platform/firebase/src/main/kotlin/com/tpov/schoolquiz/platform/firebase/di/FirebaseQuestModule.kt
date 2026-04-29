package com.tpov.schoolquiz.platform.firebase.di

import com.tpov.schoolquiz.platform.firebase.quest.FirebaseQuestRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.QuestRemoteDataSource
import org.koin.dsl.module

val firebaseQuestModule =
    module {
        single<QuestRemoteDataSource> { FirebaseQuestRemoteDataSource(get()) }
    }
