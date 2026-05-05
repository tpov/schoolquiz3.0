package com.tpov.schoolquiz.shared.feature.quest_authoring.data.di

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestArenaSubmissionSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider.DefaultQuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider.DefaultQuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import org.koin.dsl.module

val questAuthoringDataModule = module {
    single<QuestAuthoringLocalDataSource> { QuestAuthoringLocalDataSourceImpl(get(), get()) }
    single<ReviewAssignmentLocalDataSource> { ReviewAssignmentLocalDataSourceImpl(get()) }
    single<QuestAuthoringRepository> { QuestAuthoringRepositoryImpl(get()) }
    single<ReviewAssignmentRepository> { ReviewAssignmentRepositoryImpl(get(), get()) }
    single { QuestArenaSubmissionSync(get(), get(), get()) }
    single<QuestAuthoringIdProvider> { DefaultQuestAuthoringIdProvider() }
    single<QuestAuthoringTimestampProvider> { DefaultQuestAuthoringTimestampProvider() }
}
