package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.FirebaseUserStatsDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseEconomyRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseGiftBoxRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.lesson_result.FirebaseLessonResultRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.profile.FirebaseProfileRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestPrivateRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseCatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseLessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
import org.koin.dsl.module

val firebaseModule =
    module {
        single<FirebaseFirestore> { FirebaseFirestore.getInstance() }
        single<FirebaseFunctions> { FirebaseFunctions.getInstance() }
        single<UserStatsDataSource> {
            FirebaseUserStatsDataSource(
                firestore = get(),
                auth = FirebaseAuth.getInstance(),
            )
        }
        single<ProfileRemoteDataSource> {
            FirebaseProfileRemoteDataSource(functions = get())
        }
        single<EconomyRemoteDataSource> {
            FirebaseEconomyRemoteDataSource(functions = get())
        }
        single<GiftBoxRemoteDataSource> {
            FirebaseGiftBoxRemoteDataSource(functions = get())
        }
        single<LessonResultRemoteDataSource> {
            FirebaseLessonResultRemoteDataSource(functions = get())
        }
        single<CatalogSyncChangeRemoteDataSource> {
            FirebaseCatalogSyncChangeRemoteDataSource(firestore = get())
        }
        single<LessonContentSyncChangeRemoteDataSource> {
            FirebaseLessonContentSyncChangeRemoteDataSource(firestore = get())
        }
        single<QuestArenaSubmissionRemoteDataSource> {
            FirebaseQuestArenaSubmissionRemoteDataSource(firestore = get())
        }
        single<QuestPrivateRemoteDataSource> {
            FirebaseQuestPrivateRemoteDataSource(
                firestore = get(),
                auth = FirebaseAuth.getInstance(),
            )
        }
        single<ReviewAssignmentRemoteDataSource> {
            FirebaseReviewAssignmentRemoteDataSource(functions = get())
        }
    }
