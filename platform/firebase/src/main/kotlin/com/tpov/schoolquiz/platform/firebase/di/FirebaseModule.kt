package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.FirebaseUserStatsDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestPrivateRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseCatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseLessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChangeRemoteDataSource
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
