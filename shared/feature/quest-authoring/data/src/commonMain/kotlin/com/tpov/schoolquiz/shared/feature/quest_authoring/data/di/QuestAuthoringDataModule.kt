package com.tpov.schoolquiz.shared.feature.quest_authoring.data.di

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider.DefaultQuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider.DefaultQuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import org.koin.dsl.module

val questAuthoringDataModule = module {
    single<QuestAuthoringLocalDataSource> { QuestAuthoringLocalDataSourceImpl(get()) }
    single<QuestAuthoringRepository> { QuestAuthoringRepositoryImpl(get()) }
    single<QuestAuthoringIdProvider> { DefaultQuestAuthoringIdProvider() }
    single<QuestAuthoringTimestampProvider> { DefaultQuestAuthoringTimestampProvider() }
}
