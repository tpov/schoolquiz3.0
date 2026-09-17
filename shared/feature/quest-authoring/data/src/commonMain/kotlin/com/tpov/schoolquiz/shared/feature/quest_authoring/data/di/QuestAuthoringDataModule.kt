package com.tpov.schoolquiz.shared.feature.quest_authoring.data.di

import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.QuestArenaQuarantineRollback
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.ReviewActionQuarantine
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider.DefaultQuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider.DefaultQuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestArenaOutcomeSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import org.koin.dsl.module

val questAuthoringDataModule =
    module {
        single<QuestAuthoringLocalDataSource> { QuestAuthoringLocalDataSourceImpl(get()) }
        single<ReviewAssignmentLocalDataSource> { ReviewAssignmentLocalDataSourceImpl(get()) }
        single<QuestAuthoringRepository> { QuestAuthoringRepositoryImpl(get(), get(), get()) }
        // Решение ревьюера уходит общей очередью, а не callable (AD-6): репозиторию нужен только
        // локальный источник и сама очередь.
        single<ReviewAssignmentRepository> {
            ReviewAssignmentRepositoryImpl(local = get<ReviewAssignmentLocalDataSource>(), outbox = get<OutboxStore>())
        }
        single { QuestArenaOutcomeSync(get(), get(), get()) }
        // Откат локальной половины при карантине — решение владеющей фичи (AD-28).
        single { QuestArenaQuarantineRollback(get(), get()) }
        // У решения ревьюера локальной половины нет (AD-25): карантин — пометка по entity_ref, не откат.
        // Обработчик живёт в модуле фичи, чьё имя стоит в operation и entity_ref (NFR4, NFR5).
        single { ReviewActionQuarantine() }
        single<QuestAuthoringIdProvider> { DefaultQuestAuthoringIdProvider() }
        single<QuestAuthoringTimestampProvider> { DefaultQuestAuthoringTimestampProvider() }
    }
